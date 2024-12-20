Package Authentication using LDAP
=================================

This example demonstrates how to configure Package Authentication to
authenticate RESTCONF with LDAP.

For more information on using the `cisco-nso-ldap-auth` package, refer to the
README file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-ldap-auth`.

It is assumed that you have an LDAP server available, e.g, OpenLDAP
or Active Directory (AD). If not, a quick way to get going is to
set up a Docker container running OpenLDAP.

See: https://hub.docker.com/r/bitnami/openldap/

Set up the Docker container using the provided `docker-compose.yaml` file:

    sudo docker-compose up -d

This should start an OpenLDAP server populated with the users and
groups we will use in this example.

Running the Example
-------------------

The Cisco LDAP Package Authentication requires the `python-ldap`
package. A simple way to fulfill this requirement is to create a
Python virtualenv and start NSO within this virtualenv.
The Makefile target 'start' will take care of that.

Build the necessary files, copy the example `ncs.conf`, activate the Python
virtualenv, and start NSO by typing:

    make clean all
    ncs-setup --dest .
    cp ncs.conf.example ncs.conf
    . pyvenv/bin/activate
    (pyvenv) $ make start

Start the CLI and reload the packages:

    ncs_cli -u admin -g admin -C
    # packages reload

Modify the example configuration in the file `cisco-nso-ldap-auth.xml`, and
then, from a shell, load it using the `ncs_load` command:

    ncs_load -l -m cisco-nso-ldap-auth.xml

Make a RESTCONF request with the user we have setup in the LDAP server:

    curl -is -u user01:password1 http://localhost:8080/restconf/data

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