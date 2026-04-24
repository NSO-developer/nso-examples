#!/bin/sh
# Copyright 2026 Cisco Systems, Inc.
# SPDX-License-Identifier: MIT

# Log a message with a severity level
alog()
{
    sev="$1"; shift

    if [ "$sev" = "ERROR" ]; then
        echo "ERROR: $*" >&2
    elif [ "$NCSCERT_LOG_LEVEL" = "ERROR" ]; then
        return
    elif [ "$sev" = "WARN" ]; then
        echo "WARN: $*" >&2
    elif [ "$NCSCERT_LOG_LEVEL" = "WARN" ]; then
        return
    elif [ "$sev" = "INFO" ]; then
        echo "INFO: $*" >&2
    elif [ "$NCSCERT_LOG_LEVEL" = "INFO" ]; then
        return
    else
        echo "${sev}: $*" >&2
    fi
}

# Create a new CA in a separate directory and switch to that directory
create_ca()
{
    if [ -z "${NCSCERT_CAPATH}" ]; then
        NCSCERT_CAPATH="ca-$(date +%y%m%d-%H%M%S)"
    fi
    alog INFO "Generating a new CA in ${NCSCERT_CAPATH}"

    if ! (
        mkdir -p "${NCSCERT_CAPATH}" &&
        chmod 700 "${NCSCERT_CAPATH}" &&
        cd "${NCSCERT_CAPATH}" &&
        create_ca_cwd "$@"
    )
    then
        alog ERROR "Failed to generate a new CA in ${NCSCERT_CAPATH}"
        exit 3
    fi
    find_ca || exit 1
}

# Create new CA structure in the current directory
create_ca_cwd()
{
    if [ -e ca.key ] || [ -e ca.crt ]; then
        alog ERROR "CA cert/key already exists"
        exit 3
    fi

    if [ -n "$1" ]; then
        ca_name="$1"
    else
        ca_name=$(python3 "${BASE}/util/name.py" || echo "$$")
    fi
    if [ "$NCSCERT_NOCIPHER" = "1" ]; then
        alog WARN "Unencrypted CA private key is discouraged for production"
        cipher="-nodes"
    else
        cipher=""
    fi

    if ! \
        openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:secp384r1 \
                -keyout ca.key $cipher \
                -subj "/CN=NSO-HA CA ${ca_name}" -sha384 -days 3652 \
                -out ca.crt && chmod 400 ca.key
    then
        alog ERROR "Failed to generate CA cert/key"
        exit 3
    fi
    mkdir -p nodes
    echo "$ca_name" > name.txt
    alog INFO "Created CA '${ca_name}'"
}

# Generate and sign a certificate for the specified host
create_cert()
{
    cert="$1"
    cert_path="nodes/$1"

    if [ -e "${cert_path}/revoked" ]; then
        alog INFO "Found a previously revoked certificate/key, removing"
        rm -f "${cert_path}"/node.* "${cert_path}/revoked"
    fi

    if [ -e "${cert_path}/node.crt" ]; then
        alog WARN "Certificate for ${cert} already exists, skipping"
        return 0
    fi

    if ! [ -e "${cert_path}/node.csr" ]; then
        alog INFO "Generating new key/CSR for ${cert}"
        if [ -z "$NCSCERT_ENCIPHER_KEY" ]; then
            cipher="-nodes"
        else
            cipher=""
        fi
        mkdir -p "${cert_path}"
        if ! \
            openssl req -newkey ec -pkeyopt ec_paramgen_curve:secp384r1 \
                    -keyout "${cert_path}/node.crt.key" $cipher \
                    -subj "/CN=$1" -out "${cert_path}/node.csr"
        then
            alog ERROR "Failed to generate key/CSR for ${cert}"
            exit 2
        fi

        san=$(python3 "${BASE}/util/san.py" "$@")
        if [ -n "$san" ]; then
            echo "$san" > "${cert_path}/node.san"
        fi
    fi
    sign_cert "$cert" || return
    alog INFO "Use 'export-cert' to export files for each host"
}

# Export files relevant for the specified host to some directory
export_dir()
{
    [ -n "${INITIAL_DIR}" ] || return 1
    outdir="${2:-.}"

    cadir=$(pwd)
    cert_path="${cadir}/nodes/$1"
    cd "${INITIAL_DIR}" || return

    mkdir -p "${outdir}/crls" || return
    cp "${cadir}/ca.crt" "${cert_path}/node.crt" "${outdir}" || return

    crl="${cadir}/crl.pem"
    if [ -e "${crl}" ]; then
        crl_name="$(openssl x509 -hash -noout -in ${cadir}/ca.crt).r0"
        cp "${crl}" "${outdir}/crls/${crl_name}" || return
    fi

    key="${cert_path}/node.crt.key"
    if [ -e "${key}" ]; then
        if [ -e "${outdir}/node.key" ]; then
            alog INFO "File '${outdir}/node.key' already exists, skipping"
        else
            if [ -z "$NCSCERT_ENCIPHER_KEY" ]; then
                cipher=""
            else
                alog INFO "Encrypting private key; use 'openssl pkey" \
                    "-in host.key -out unencrypted.key' to decrypt"
                cipher="-cipher aes-256-cbc"
            fi
            openssl pkey -in "${key}" $cipher -out "${outdir}/node.key" && \
                chmod 400 "${outdir}/node.key" || return
        fi
    else
        alog INFO "Private key not found, skipping"
    fi
    alog INFO "Exported files for $1 certificate to '${outdir}'"
}

# Change current directory to NCSCERT_CAPATH or the newest ca-* dir
find_ca()
{
    if [ -z "${NCSCERT_CAPATH}" ]; then
        candidate=$(find ca-*/ca.crt 2>/dev/null | LC_ALL=C sort | tail -n 1)
        if [ -e "${candidate}" ]; then
            NCSCERT_CAPATH=$(dirname "$candidate")
        fi
    fi
    if ! [ -e "${NCSCERT_CAPATH}/ca.crt" ]; then
        alog INFO "No existing CA found"
        return 1
    fi

    cd "${NCSCERT_CAPATH}" || exit 1
    ca_name=$(cat name.txt 2>/dev/null || echo "unknown")
    alog INFO "Using CA in ${NCSCERT_CAPATH} (${ca_name})"
}

remove_key()
{
    cert_path="nodes/$1"
    alog INFO "Removing private key for ${1}"
    if ! rm -f "${cert_path}/node.crt.key"; then
        alog ERROR "Failed to remove private key"
        exit 2
    fi
}

revoke_cert()
{
    alog INFO "Revoking certificate $1"
    cert_path="nodes/$1"
    if setup_config && \
        openssl ca -cert ca.crt -keyfile ca.key -config ca.cnf \
                -md sha384 -revoke "${cert_path}/node.crt" && \
        touch "${cert_path}/revoked" && \
        openssl ca -cert ca.crt -keyfile ca.key -config ca.cnf \
                -md sha384 -gencrl -crldays 3652 -out crl.pem
    then
        alog INFO "Certificate revoked; new CRL available"
        echo "${NCSCERT_CAPATH}/crl.pem"
        alog INFO "Use 'export-cert' to export files for each host"
    else
        alog ERROR "Failed to revoke certificate"
        exit 2
    fi
}

setup_config()
{
    [ -e ca.cnf ] || cat <<EOF >ca.cnf || return
[ ca ]
default_ca = default_ca
[ default_ca ]
database = revoked
EOF
    [ -e revoked ] || touch revoked || return
}

sign_cert()
{
    alog INFO "Signing certificate $1"
    cert_path="nodes/$1"
    san="${cert_path}/node.san"
    [ -e "${san}" ] || san=""

    if [ -e "${cert_path}/revoked" ]; then
        alog WARN "Signing a previously revoked key"
    fi

    if ! \
        openssl x509 -req -CA "ca.crt" -CAkey "ca.key" \
                -CAcreateserial -sha384 -days 365 \
                -in "${cert_path}/node.csr" ${san:+-extfile "${san}"} \
                -out "${cert_path}/node.crt"
    then
        alog ERROR "Failed to sign certificate at ${cert_path}"
        exit 2
    else
        alog INFO "Signed certificate"
        echo "${NCSCERT_CAPATH}/${cert_path}/node.crt"
    fi
}

show_usage_maybe()
{
    case "$1" in
        --help) usage 0 ;;
        -h)     usage 0 ;;
    esac
}

validate_hosts()
{
    while true; do
        if [ -z "$1" ]; then
            break
        fi
        if ! [ "$1" = "$(echo "$1" | sed -E 's/[^a-zA-Z0-9.:-]+//g')" ]; then
            alog ERROR "Invalid host/IP '$1'"
            exit 1
        fi
        shift
    done
}

verify_cert()
{
    cert_file="nodes/$1/node.crt"
    crl="crl.pem"
    [ -e "$crl" ] || crl=""
    openssl x509 -text -noout -in "${cert_file}" 1>&2 2>/dev/null || true
    openssl verify -CAfile ca.crt ${crl:+-crl_check -CRLfile "$crl"} \
        "${cert_file}" >/dev/null
    return $?
}

with_cert_do()
{
    cmd="$1"; shift
    if [ -z "$1" ]; then
        usage 1
    fi
    show_usage_maybe "$@"
    find_ca || exit 1
    if ! [ -e "nodes/$1/node.crt" ]; then
        alog ERROR "Unknown certificate '$1'"
        exit 1
    fi
    $cmd "$@"
    return $?
}
