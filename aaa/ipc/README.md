Authenticated Inter-Process Communication (IPC)
===============================================

NSO uses sockets-based communication to coordinate different parts of the
system. Since NSO 6.4, sockets can be either Unix- or TCP-based. In either
case, the communication over IPC should be limited to trusted clients.
In the default configuration, IPC is available only to the local processes.

When using Unix domain sockets, NSO performs additional checks based on the
user ID of the calling process to disallow rogue processes access to IPC.

On the other hand, such checks are not possible when using TCP sockets. In
this case, the system can use an access check, verifying that the client is
in possession of a secret key. As the TCP socket is potentially exposed to
the network, a security best practice is to prefer Unix-based sockets or at
the least configure an access check.

This example shows how to set up an NSO instance to use Unix-based sockets
for IPC and authenticate other users when using it. The system has some
predefined users under `/aaa/authentication/users`. When another (non-root)
user connects, the system checks the `local_ipc_access` value to decide if
the user is allowed to connect.

Start the example by running:

    make demo

Inspect the `/aaa/authentication/users/user` configuration:

    admin@ncs# show running-config aaa authentication users user

User alice (uid 578) has `local_ipc_access false` and user bob (uid 579)
has `local_ipc_access true`. You can try starting `ncs_cli` (which uses IPC
towards NSO) as either of these users to exercise the IPC authentication
mechanism:

    admin@ncs# exit
    export NCS_IPC_PATH="$(pwd)/ncs-run/unix-ipc.socket"
    sudo -E python3 run_as_uid.py 578 ncs_cli -C
    Failed to connect to server

    sudo -E python3 run_as_uid.py 579 ncs_cli -C
    bob connected from 127.0.0.1 using console on HOST
    bob@ncs#

You can then freely change `local_ipc_access` value for` alice` and `bob` to
toggle whether each user is able to connect. When done:

    bob@ncs# exit

Using IPC Access Check
----------------------

The second part of the example configures TCP-based IPC with an access
check instead of Unix domain sockets.

Start this part by running:

    make demo-tcp

The script prints out the required `ncs.conf` configuration and shows how
to invoke `ncs_cli` to connect with the secret key. When done:

    # exit

Cleanup
-------

Stop NSO and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Authenticating IPC Access
