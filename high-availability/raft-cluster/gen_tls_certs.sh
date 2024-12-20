#!/bin/sh

scrptname=${0##*/}
outdir=ssl
aflag=''

usage()
{
    cat <<EOF
usage: $scrptname [-d outdir] [-a] [host1 host2 ... hostn]

    Generates TLS certs/keys for a self-signed Certificate Authority (CA) or
    prints a warning if it already exists. The script will use the CA to sign
    certs for any subsequent host[1..n] certs/keys.

    Keys, certificate signing requests (csr) and certs are created under $outdir/

    host[1..n] is a fully-qualified domain name and is used to verify the source
    address when a server verifies the cert, unless subjectAltName is specified,
    then it will be used instead, see next section.

    If host.cnf exists in current directory, subjectAltName (SAN) will be used
    from that file INSTEAD of -subj to specify the host in the host certificate,
    where it is possible to specify IP addresses and multiple hostnames.

    If the -a switch is specified, host[1..n] must instead be an IP address
    and the script OVERWRITES each <IP address>.cnf file, using the IP as SAN.
EOF
    exit "${1:-0}"
}

# certificate authority certs and keys
generate_self_signed_ca()
{
    # see https://wwwin-github.cisco.com/CSDL/PSB/tree/master/normative-external
    # for guidance if used crypto algorithms need upgrading
    if ( [ -e "$outdir/private/ca.key" ] || [ -e "$outdir/certs/ca.crt" ] )
    then
        echo "WARNING: CA cert/key already exists, skipping" >&2
        return
    fi
    # umask removes group/other read/write access from private key
    ( set -xe
      openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:secp384r1 \
              -keyout "$outdir/private/ca.key" -nodes \
              -subj "/CN=self-signed CA" -sha384 -days 3652 \
              -out "$outdir/certs/ca.crt"
      chmod 600 "$outdir/private/ca.key" )
    if [ $? -ne 0 ]; then
        echo "$scrptname: error: failed to generate CA certs/keys" >&2
        exit 1
    fi
}

generate_host_certs()
{
    seed1=''
    [ -z "$2" ] || seed1="<seed-nodes><seed-node>$2</seed-node></seed-nodes>"
    echo "add below to /etc/ncs/ncs.conf"
    cat <<EOF
  <ha-raft>
    <enabled>true</enabled>
    <cluster-name>stockholm</cluster-name>
    <listen>
      <node-address>$1</node-address>
    </listen>
    $seed1
    <ssl>
      <enabled>true</enabled>
      <ca-cert-file>\${NCS_CONFIG_DIR}/dist/ssl/certs/ca.crt</ca-cert-file>
      <cert-file>\${NCS_CONFIG_DIR}/dist/ssl/certs/$1.crt</cert-file>
      <key-file>\${NCS_CONFIG_DIR}/dist/ssl/private/$1.key</key-file>
    </ssl>
  </ha-raft>
EOF

    for i in "private/$1.key" "csr/$1.csr" certs/$1.crt; do
        if [ -e "$outdir/$i" ]; then
            echo "WARNING: '$outdir/$i' already exists, skipping" >&2
            return
        fi
    done

    if [ -n "$aflag" ]; then
        echo "subjectAltName=IP:$1, DNS:$1" > "$1.cnf"  # See below why 'DNS:'
    fi

    # the following applies when subjectAltName (SAN) is used
    # 1. overrides hostname/IP address verification specified by -subj
    # 2. and multiple addresses are allowed (vs. -subj only allows one)
    # 3. "DNS:" allows IP address use (where -subj only hostname)
    # 4. as of TLS application in Erlang/OTP 25 "IP:" value has no effect
    extfile=
    [ ! -e "$1.cnf" ] || extfile="-extfile $1.cnf"
    # umask removes group/other read/write access from private key
    ( set -x; umask 077 && \
          openssl req -newkey ec -pkeyopt ec_paramgen_curve:secp384r1 \
                  -keyout "$outdir/private/$1.key" -nodes \
                  -out "$outdir/csr/$1.csr" -subj "/CN=$1" ) && \
        ( set -x; openssl x509 -req -CAcreateserial $extfile \
                          -in "$outdir/csr/$1.csr" \
                          -CA "$outdir/certs/ca.crt" \
                          -CAkey "$outdir/private/ca.key" \
                          -days 3652 -out "$outdir/certs/$1.crt" )
    if [ $? -ne 0 ]; then
        echo "$scrptname: error: failed to generate host certs/keys for $1" >&2
        exit 1
    fi
}


while [ $# -ge 0 ]; do
    case "$1" in
        --help) usage ;;
        -d) outdir=$2; shift 2 || usage 1 ;;
        -a) aflag=1; shift || usage 1 ;;
        -*) echo "$scrptname: error: unknown flag: '$1'" >&2; exit 1 ;;
        *) break ;;
    esac
done

if [ $# -eq 0 ]; then
    echo "$scrptname: error: missing arguments" >&2
    exit 1
fi

mkdir -p "$outdir"
( cd "$outdir" && mkdir -p certs crl csr private )
chmod 700 "$outdir/private"

generate_self_signed_ca
[ $# -lt 2 ] || first=$1
while [ $# -gt 0 ]; do
    generate_host_certs "$1" "${2:-$first}"
    echo
    echo "in case the NSO installation is a system install, place the"
    echo "following certificates and key on the host '$1'"
    echo
    echo "  $outdir/certs/ca.crt in $1:/etc/ncs/dist/ssl/certs"
    echo "  $outdir/certs/$1.crt in $1:/etc/ncs/dist/ssl/certs"
    echo "  $outdir/private/$1.key in $1:/etc/ncs/dist/ssl/private"
    shift
done
