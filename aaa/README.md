Authentication and Authorization Examples
=========================================

Use NSO's AAA mechanism.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### ssh-keys
A README showcasing the options for managing and using SSH host keys and how to
set up authentication for CLI and NETCONF NEDs using a private SSH key instead
of a password ("publickey" authentication in SSH terminology). Used by the SSH
Key Management chapter under NSO as SSH Client in the NSO Operation & Usage
Guide.

### ipc
Shows how to set up an NSO instance to use Unix-based sockets for IPC and
authenticate other users when using it. A second part of the example configures
TCP-based IPC with an access check instead of Unix domain sockets. Used by the
Administration Guide section Authenticating IPC Access.

### packageauth
This example demonstrates configuring Package Authentication to authenticate
RESTCONF with a JSON Web Token (JWT). See the NSO Administration Guide chapter
The AAA Infrastructure section Authentication for package authentication
documentation.

### packageauth-ldap
This example demonstrates configuring Package Authentication to authenticate
RESTCONF with LDAP. See the NSO Administration Guide chapter The AAA
Infrastructure section Authentication for package authentication
documentation.

### single-sign-on
This example demonstrates integrating and using the `cisco-nso-saml2-auth`
Authentication Package to enable SAMLv2 Single Sign-On for NSO. See the
Development Guide chapter Single Sign-on for documentation.
