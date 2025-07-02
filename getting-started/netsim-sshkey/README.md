Nano Service for Provisioning of SSH Public Key NETCONF Client Authentication
=============================================================================

A sender can use a public key to encrypt a message or digital signature that
only the holder of the corresponding private key can decrypt. Each key pair is
unique, and the two keys work together. Using the private key, recipients can
prove they have it without sharing it. It's like proving you know a password
without showing someone the password.

A NETCONF SSH client, such as NSO's built-in NETCONF NED, uses its private and
public keys for authenticating the client with the server using a digital
signature and for the encryption part of setting up the Secure Shell (SSH)
connection.

This example automates the setup of SSH public key authentication using a nano
service (see the distkey.yang module for details). The nano service uses the
following steps in a plan that produces the `generated`, `distributed`, and
`configured` states:

1. Generates the NSO SSH client authentication key files using the OpenSSH
   `ssh-keygen` utility from a nano service side-effect action implemented
   in Python.

2. Distributes the public key to the netsim (ConfD) network elements to be
   stored as an authorized key using a Python service create() callback.

3. Configures NSO to use the public key for authentication with the netsim
   network elements using a Python service create() callback and service
   template.

4. Test the connection using the public key through a nano service side-effect
   executed by the NSO built-in `connect` action.

Upon deletion of the service instance, NSO restores the configuration. The only
delete step in the plan is the `generated` state side-effect action that
deletes the key files.

See the `distkey_app.py` for details on the Python application.

The netsim network elements implement a configuration subscriber Python
application triggered when a public key is added to or removed from the
`authkey` list. The application adds or removes the configured public key
to/from the user's .ssh/authorized_keys file, which the netsim (ConfD) network
element checks when authenticating public key authentication clients. See
the `ssh_authkey.py` application and the `distkey.yang` module for details.

Prerequisites
-------------

+ Python Requests

Running the Example
-------------------

First, if unsure, check that the OpenSSH `ssh-keygen` utility is installed
using, for example:

    type ssh-keygen

or

    which ssh-keygen

A shell script runs the example according to the steps described in the
Getting Started documentation. Run the script to provision three instances
(= three netsim network elements) of the `distkey` service by typing:

    make showcase

The above shell script uses the NSO CLI as the northbound interface, and a
Python script variant uses the NSO RESTCONF northbound interface and
notification events. Instead of polling for nano service state changes, as the
CLI script does, it uses the `service-state-changes` stream
`plan-notifications` to check when a nano service has reached a particular
state. Run it by typing:

    make showcase-rc

A third variant, a Python script that uses NSO MAAPI and notification events,
is available. Run it by typing:

    make showcase-maapi

The showcase scripts perform the following steps to generate, distribute, and
configure network elements with SSH public key authentication:

1. Reset and setup the example

        make stop clean all start

2. Generate keys, distribute the public key, and configure NSO for public key
   authentication

        $ ncs_cli -n -u admin -C
        # devices sync-from
        # config
        (config)# pubkey-dist key-auth ex0 admin remote-name admin \
        authgroup-name ex0-admin passphrase \"GThunberg18!\"
        (config)# pubkey-dist key-auth ex1 admin remote-name admin \
        authgroup-name ex1-admin passphrase \"GThunberg18!\"
        (config)# pubkey-dist key-auth ex2 admin remote-name admin \
        authgroup-name ex2-admin passphrase \"GThunberg18!\"
        (config)# commit dry-run
        (config)# commit
        (config)# end
        # show pubkey-dist key-auth plan component self self state ready status
        NODE  LOCAL
        NAME  USER   TYPE  NAME  STATE  STATUS
        -----------------------------------------
        ex0   admin  self  self  ready  reached
        ex1   admin  self  self  ready  reached
        ex2   admin  self  self  ready  reached

3. Show the plan status

        show pubkey-dist key-auth plan component | tab | nomore

4. Show the configuration added to NSO and network elements

        show running-config devices authgroups group umap admin | nomore
        show running-config devices device authgroup | nomore
        show running-config devices device config aaa authentication users \
        user admin authkey
        exit

5. List the generated private and public key files.

        ls -la -1 *ed25519*

6. Delete the nano service (`ctrl-c` to abort) to go back from the public key
   to password-based network element authentication

        $ ncs_cli -n -u admin -C
        # config
        (config)# no pubkey-dist
        (config)# commit dry-run
        (config)# commit
        (config)# end
        # show zombies service | icount
        Found 0 instances.

7. Show the restored configuration for password authentication

        show running-config devices authgroups group umap admin | nomore
        show running-config devices device authgroup | nomore
        show running-config devices device config aaa authentication users \
        user admin authkey

Further Reading
---------------

+ NSO Development Guide: Developing and Deploying a Nano Service
+ An NSO system-install variant of this example to demonstrate deploying the
  application:
  https://github.com/NSO-developer/sshkey-deployment-example
+ NSO Development Guide: Nano Services
+ The showcase.sh and showcase_rc.py scripts
+ The tailf-ncs-plan.yang and tailf-ncs-services.yang modules
