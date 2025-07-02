Package Authentication using LDAP
=================================

This example demonstrates how to configure Package Authentication to
authenticate RESTCONF with LDAP.

For more information on using the `cisco-nso-ldap-auth` package, refer to the
README file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-ldap-auth`.

This example builds a minimal OpenLDAP server populated with the users and
groups we will use in this example. If you already have an LDAP server it
is possible to modify the example to use credentials from that server.

Running the Example
-------------------

The Cisco LDAP Package Authentication requires the `python-ldap`
package. A simple way to fulfill this requirement is to create a
Python virtualenv and start NSO within this virtualenv.
The Makefile target 'start' will take care of that.

Build the necessary files, copy the example `ncs.conf`, activate the Python
virtualenv, and start NSO by typing:

    make all start

Verify that we can query to the LDAP server:

    ldapsearch -x -b "uid=sbrown,ou=engineering,dc=example,dc=com" \
               -D "cn=admin,dc=example,dc=com" -w admin \
               -H ldap://localhost:1389 uid memberOf

Start the CLI and reload the packages:

    ncs_cli -u admin -g admin -C
    # packages reload

Load the example configuration in the file `cisco-nso-ldap-auth.xml`, using
the `ncs_load` shell command:

    ncs_load -l -m cisco-nso-ldap-auth.xml

Make a RESTCONF request with the user `sbrown` that we have setup in the LDAP
server:

    curl -isu sbrown:sbrown http://localhost:8080/restconf/data

A successful request will return a HTTP 200 OK return code together with NSO
configuration data.

Review the `audit.log` to verify that the LDAP package authentication was
invoked and what group the user was assigned to.

    tail logs/audit.log

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Package Authentication
