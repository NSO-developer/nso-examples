Package Authentication
======================

This example demonstrates configuring Package Authentication to
authenticate RESTCONF with a JSON Web Token (JWT). For further details,
see RFC 7519 JSON Web Token.

The example package `nso-example-jwt-auth` requires the `PyJWT` package to
decode the JWT received from NSO during authentication. A simple way to
fulfill this requirement is to create a Python virtualenv and start NSO
within this virtualenv.

When NSO is configured to use Package Authentication, it is possible to
supply a JWT as a bearer token to RESTCONF to authenticate.

Running the Example
-------------------

Build the necessary files, activate the Python virtualenv, and start NSO:

    make clean all
    . pyvenv/bin/activate
    (pyvenv) $ make start

Start the CLI and reload packages

    ncs_cli -u admin -g admin -C
    # packages reload

Configure the nso-example-jwt-auth model

    # config
    (config)# jwt-auth algorithm HS256
    (config)# jwt-auth secret my-secret
    (config)# commit
    (config)# exit
    # exit

Inspect the contents of the JWT in the example:

    sed 's/.*\.\(.*\)\.*/\1=/' < jwt | base64 -d | python -m json.tool

Note the addition of the equals character enabling base64 decoding of the
JSON claims payload. For brevity, `jwt.txt` contains the base64 decoded JSON
claims payload.

Pass the JWT as a bearer token to RESTCONF in the Authorization header:

    curl -is -H "Authorization: Bearer $(cat jwt)" \
    http://localhost:8080/restconf

Check the package log and audit log for debug and authentication information:

    tail logs/ncs-python-jwt-auth.log
    tail logs/audit.log

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Package Authentication
