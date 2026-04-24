SAMLv2 Single Sign-On
=====================

This example demonstrates integrating and using the `cisco-nso-saml2-auth`
Authentication Package to enable SAMLv2 Single Sign-On for NSO.

This particular NSO authentication package depends on additional Python
packages, specified in its requirements.txt file. A simple way to fulfill
this requirement is to create a Python virtualenv and start NSO within
this virtualenv, in the way the Makefile target 'start' does.

For more information on using the `cisco-nso-saml2-auth` package refer to the
README file distributed with the package, e.g., in
`$NCS_DIR/packages/auth/cisco-nso-saml2-auth`.

This example requires a SAMLv2 Identity Provider (IdP) defining users. If
you do not have an existing server to use with the example, you can use
a demo IdP based on the `flask-saml2` Python package (requires git).

Note that the included demo IdP does not actually authenticate users and is
unsuitable for production use.

Running the Example
-------------------

Build the necessary files, activate the Python virtualenv, and start NSO:

    make all start

Configure the `cisco-nso-saml2-auth` model. This is done by loading the
prepared config XML file, but it can also be done manually in the CLI. See
`cisco-nso-saml2-auth` documentation for details:

    ncs_load -l -m -u admin cisco-nso-saml2-auth.xml

The generated `cisco-nso-saml2-auth.xml` contains URL endpoints and certificate
data that correspond to the demo IdP. Integration with a different IdP will
require you to update this configuration correspondingly.

If you are using the included demo IdP, start it with:

    make start-idp

Visit a resource that requires authentication in a web browser, e.g., the NSO
WebUI. Note that access is denied, and the normal login prompt is presented:

    http://localhost:8080/

Inspect the NSO SSO SAML metadata

    curl http://localhost:8080/sso/saml/metadata/

Visit the SAML SSO endpoint and note the trailing slash. The web browser is
redirected to the IdP server, where a simple login is presented. When login is
done, the web browser is redirected back to the NSO SAML ACS (Assertion
Consumer Service) and is presented with a HTTP 200, a session cookie, and the
payload `ok`.

    http://localhost:8080/sso/saml/login/

Visit a resource that again requires authentication in a web browser, e.g., the
NSO WebUI. This time, access is granted:

    http://localhost:8080/

Check package and audit logs for debug and authentication information:

    tail ncs-run/logs/ncs-python-saml2-auth.log
    tail ncs-run/logs/audit.log

Logout with Single Logout by visiting the SAML SLO endpoint. The NSO session is
deleted:

    http://localhost:8080/sso/saml/logout/


Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Single Sign-on (SSO)

References
----------

Assertions and Protocols for the OASIS Security Assertion Markup Language
(SAML) V2.0

    https://docs.oasis-open.org/security/saml/v2.0/saml-core-2.0-os.pdf


flask-saml2

    https://github.com/mx-moth/flask-saml2


License
-------

The demo IdP is based on example/idp.py from flask-saml2.
See idp/LICENSE and flask-saml2/LICENSE for further details.
