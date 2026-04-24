Package authentication using OpenID Connect (OIDC)
==================================================

This example demonstrates integrating and using the cisco-nso-oidc-auth
Authentication Package to enable OpenID Connect authentication for NSO.

This particular NSO authentication package depends on additional Python
packages, specified in its requirements.txt file. A simple way to fulfill
this requirement is to create a Python virtualenv and start NSO within
this virtualenv, in the way the Makefile target 'start' does.

For more information on using the `cisco-nso-oidc-auth` package, refer to the
`README` file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-oidc-auth`.

This example requires an OIDC Identity Provider (IdP) defining users. If
you do not have an existing server to use with the example, see Example IdP
section below on using a Keycloak container (requires Docker).

Running the Example
-------------------

Build the necessary files, activate the Python virtualenv, and start NSO:

    make all start

Configure the `cisco-nso-oidc-auth.yang` model. This is done by
loading a prepared config XML file, but it can also be done manually
in the CLI (see the `cisco-nso-oidc-auth` documentation for details).

You need to configure NSO with the IP address of the OIDC server,
the Client ID, and the Shared Secret. You can modify the supplied
file `cisco-nso-oidc-auth.xml` and then load it as:

    ncs_load -l -m -u admin cisco-nso-oidc-auth.xml

Ensure you have a working IdP and make a login request using the web
interface:

    In a browser visit http://127.0.0.1:8080/

Check package and audit logs for debug and authentication information:

    tail ncs-run/logs/audit.log

You should see something like (output is formatted to fit):

    <INFO> 10-Mar-... audit user: admin/0 package authentication \
            using cisco-nso-oidc-auth succeeded via webui from \
            127.0.0.1:50357 with http, member of groups: admin
    <INFO> 10-Mar-... audit user: admin/47 assigned to groups: admin

Example IdP
-----------

You can use Keycloak (https://www.keycloak.org/) OIDC IdP for testing. A
sample Keycloak realm to use with the example (NSO-realm.json.zip) is included
in the `misc` directory. After unzipping the realm file, start a development
Keycloak container with a command such as:

    docker run --name testkeycloak -p 127.0.0.1:8081:8080 \
    -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
    -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
    -v ./misc/NSO-realm.json:/opt/keycloak/data/import/NSO-realm.json \
    quay.io/keycloak/keycloak:26.4.2 start-dev --import-realm --verbose

The administration account for the Keycloak realm file is called admin
(with the username as password). Users in the NSO-realm are admin,
oper and nsouser (also with respective username as password). The oper
user will use authorization stored in the IdP, and admin will use
authorization from the NSO server (local). There is no supplied or
recorded authorization for nsouser so this user will fail to log in.

Understand that the browser will establish a session with the IdP and
this session will be tried for subsequent authentication attempts
towards NSO. IdP sessions can be signed out of in Keycloak at
`http://127.0.0.1:8081/admin/master/console/#/NSO/sessions`. Signing
out of IdP sessions will not affect established NSO sessions.

Note that the realm file (`misc/NSO-real.json`) and the OIDC package
configuration example file (`cisco-nso-oidc-auth.xml`) contain test
secrets, certificates, keys and high entropy strings that are security
sensitive and must be replaced for an actual production deployment!

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

References
----------

The OAuth 2.0 RFC 6749: https://datatracker.ietf.org/doc/html/rfc6749

This repository is based on
* https://pypi.org/project/oidc-client/
* https://gitlab.com/yzr-oss/oidc-client
modified towards NSO Package Authentication with existing API.
