Certificate Management Scripts
==============================

Here you will find a set of scripts to simplify TLS certificate management
for a private Certificate Authority (CA). The shell scripts wrap the `openssl`
command to do the heavy lifting and use Python for a few specific tasks.

These scripts are licensed under an MIT license.


Usage
-----

First, you need a CA that will sign the actual host (node) certificates. Use

    # ./init-new-ca

to create one. This step is optional as the `create-cert` utility automatically
provisions a CA if one does not exist yet.

As a best practice, the CA private key (used for signing) is encrypted with
a password that you provide when setting up a new CA. Every signing operation
then requires this password. You can disable this behavior via environment
variables, but disabling it is highly discouraged in production use.

If you run this command multiple times, multiple CAs are created. The system
is designed to work by default with the CA that was created last. Each CA is
also given a random name to make it easier to distinguish when multiple CAs
are in use.

Create a certificate for a host by running:

    # ./create-cert IP.or.somehost.somedomain

You may specify multiple hostnames or IP addresses; the first one is used as
the name of the certificate and all of them are added to the SAN field.

Then export the files required for a specific host:

    # ./export-cert IP.or.somehost.somedomain .../somedir

Repeat the last two steps to create as many certificates as required.

The private keys for host certificates are by default not encrypted neither
when generated nor when exported. If you wish to encrypt them, setting
NCSCERT_ENCIPHER_KEY environment variable will prompt for a password, which
(separately) controls encryption for `create-cert` and `export-cert`.
For improved security, you may delete the host private key with `remove-key`;
but make sure you have exported it first, as the key is erased and cannot
be recovered!

You can list the certificates already created by the CA with

    # ./list-certs

and verify individual certificate is valid with

    # ./verify-cert IP.or.somehost.somedomain

This command is also useful for troubleshooting issues, such as expiry.
The certificates (except for the CA) have a validity of one year. You can
re-sign an existing certificate with `renew-cert`.


Revoking Certificates
---------------------

In case of a key compromise, the simplest solution is to provision a new set
of certificates from a new CA. Use `init-new-ca` and `create-cert` to create
all host certificates anew.

Alternatively, you can revoke a certificate:

    # ./revoke-cert IP.or.somehost.somedomain

However, revoking by itself does nothing to prevent someone from using the
certificate. The revoking procedure creates a Certificate Revocation List
(CRL) that you must distribute to the hosts that perform authentication of
certificates signed by this CA.

**IMPORTANT**: _All_ hosts must always have the latest CRL(s). Otherwise, the
               host will see the revoked certificate as valid.


License
-------

MIT License

Copyright (c) 2026 Cisco Systems, Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
