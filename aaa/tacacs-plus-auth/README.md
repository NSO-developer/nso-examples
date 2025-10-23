Package authentication using TACACS+
====================================

This example demonstrates integrating and using the cisco-nso-tacacs-auth
Authentication Package to enable TACACS+ authentication for NSO.

For more information on using the `cisco-nso-tacacs-auth` package, refer to the
`README` file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-tacacs-auth`.

For the `cisco-nso-tacacs-auth` package to run, the `tacacs_plus` Python
package is required. A simple way to fulfill this requirement is to create
a Python virtualenv and start NSO within this virtualenv. The build steps
needed are done with the `Makefile` targets, but note that the virtualenv
needs to be activated manually before starting NSO.

Running the Example
-------------------

Build the necessary files, copy the example `ncs.conf`, activate the Python
virtualenv, and start NSO:

    make clean all
    . pyvenv/bin/activate
    (pyvenv) $ make start

Start the CLI and reload packages

    ncs_cli -u admin -g admin -C
    packages reload

Configure the `cisco-nso-tacacs-auth.yang` model. This is done by
loading a prepared config XML file, but it can also be done manually
in the CLI (see the `cisco-nso-tacacs-auth` documentation for details).

You need to specify the IP address of the TACACS+ server,
the Port used, and the Shared Secret. You can modify the supplied
file: `cisco-nso-tacacs-auth.xml` and then load it as:

    ncs_load -l -m cisco-nso-tacacs-auth.xml

Make a login request of some sort, e.g., a RESTCONF request:

    curl -is -u admin:<password> http://127.0.0.1:8080/restconf/data

Check package and audit logs for debug and authentication information:

    tail logs/audit.log

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
