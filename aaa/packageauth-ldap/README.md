Package Authentication using LDAP
=================================

This example demonstrates how to configure Package Authentication to
authenticate RESTCONF with LDAP.

This particular NSO authentication package depends on additional Python
packages, specified in its requirements.txt file. A simple way to fulfill
this requirement is to create a Python virtualenv and start NSO within
this virtualenv, in the way the Makefile target 'start' does.

For more information on using the `cisco-nso-ldap-auth` package, refer
to the README file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-ldap-auth`.

This example requires a minimal LDAP server with some users and groups. If
you do not have an existing server to use with the example, you can either
use an OpenLDAP container with `make start-openldap` (requires Docker), or
use a demo Python server with `make start-ldap`.

Running the Example
-------------------

Build the necessary files, activate the Python virtualenv, and start NSO:

    make all start

In case you do not have a running LDAP server yet, start one with:

    make start-ldap

Optionally, verify that you can query the LDAP server (requires ldapsearch
utility from the OpenLDAP project):

    ldapsearch -x -b "uid=sbrown,ou=engineering,dc=example,dc=com" \
               -D "cn=admin,dc=example,dc=com" -w admin \
               -H ldap://localhost:1389 uid memberOf

Load the example configuration in the file `cisco-nso-ldap-auth.xml`, using
the `ncs_load` shell command:

    ncs_load -l -m -u admin cisco-nso-ldap-auth.xml

Make a RESTCONF request with the user `sbrown` that has been set up in the LDAP
server (if using the provided LDAP server):

    curl -isu sbrown:sbrown http://localhost:8080/restconf

A successful request will return an HTTP 200 OK return code together with NSO
YANG library version data.

Review the `audit.log` to verify that the LDAP package authentication was
invoked and what group the user was assigned to.

    tail ncs-run/logs/audit.log

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Package Authentication
