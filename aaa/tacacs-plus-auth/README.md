Package authentication using TACACS+
====================================

This example demonstrates integrating and using the cisco-nso-tacacs-auth
Authentication Package to enable TACACS+ authentication for NSO.

This particular NSO authentication package depends on additional Python
packages, specified in its requirements.txt file. A simple way to fulfill
this requirement is to create a Python virtualenv and start NSO within
this virtualenv, in the way the Makefile target 'start' does.

For more information on using the `cisco-nso-tacacs-auth` package, refer to the
`README` file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-tacacs-auth`.

This example requires an existing TACACS+ server defining users.

Running the Example
-------------------

Build the necessary files, activate the Python virtualenv, and start NSO:

    make all start

Configure the `cisco-nso-tacacs-auth.yang` model. This is done by
loading a prepared config XML file, but it can also be done manually
in the CLI (see the `cisco-nso-tacacs-auth` documentation for details).

You need to specify the IP address of the TACACS+ server,
the Port used, and the Shared Secret. You can modify the supplied
file `cisco-nso-tacacs-auth.xml` and then load it as:

    ncs_load -l -m -u admin cisco-nso-tacacs-auth.xml

Make a login request of some sort, e.g., a RESTCONF request:

    curl -is -u admin:<password> http://127.0.0.1:8080/restconf

Check package and audit logs for debug and authentication information:

    tail ncs-run/logs/audit.log

You should see something like (output is formatted to fit):

    <INFO> 6-Oct-... audit user: admin/0 package authentication \
            using cisco-nso-tacacs-auth succeeded via rest from \
            127.0.0.1:60832 with http, member of groups: admin
    <INFO> 6-Oct-... audit user: admin/56 assigned to groups: admin

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

References
----------

The TACACS+ RFC 8907: https://datatracker.ietf.org/doc/html/rfc8907

For a description of how to configure the Cisco ISE TACACS+ server, see
"README-setuptacacs-ise.md" under https://github.com/ygorelik/tacacs-auth/

A very simple TACACS+ server for testing: https://github.com/etnt/etacacs_plus
