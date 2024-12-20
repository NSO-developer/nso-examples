NETCONF SSH Call Home
=====================

This example demonstrates the built-in support for NETCONF SSH Call Home client
protocol operations over SSH as defined in RFC 8071 section 3.1.

With NETCONF SSH Call Home, the NETCONF client listens for TCP connection
requests from NETCONF servers. The SSH client protocol is started when the
connection is accepted. The SSH client validates the server's presented host
key with credentials stored in NSO. The TCP connection will be closed
immediately if no matching host key is found. Otherwise, the SSH connection is
established, and NSO is enabled to communicate with the device. The SSH
connection is kept open until the device itself terminates the connection, an
NSO user disconnects the device, or the idle connection timeout is triggered.
The idle timeout is configurable from the `ncs.conf` file.

    NETCONF                             NETCONF
    Server                              Client
      |                                    |
      |   1. TCP connect                   |
      |----------------------------------->|
      |                                    |
      |                                    |
      |   2. SSH over the TCP session      |
      |<-----------------------------------|
      |                                    |
      |                                    |
      |   3. NETCONF over the SSH session  |
      |<-----------------------------------|
      |                                    |

            Call Home Sequence Diagram

NETCONF Call Home is enabled and configured under
`/ncs-config/netconf-call-home` in the `ncs.conf` file. By default, NETCONF
Call Home is disabled.

    <ncs-config xmlns="http://tail-f.com/yang/tailf-ncs-config">
    ...
      <netconf-call-home>
        <enabled>true</enabled>
        <transport>
          <ssh>
            <idle-connection-timeout>PT10S</idle-connection-timeout>
          </ssh>
        </transport>
      </netconf-call-home>
    ...
    </ncs-config>

See `tailf-ncs-config.yang` for additional configuration parameters.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Start clean, i.e., no old configuration data is present:

    ncs --stop
    ncs-netsim stop
    make clean

Setup the simulated network and build the packages:

    make all

Start the simulated network and the NSO nodes:

    ncs-netsim start
    ncs

Start a C-style CLI to begin our work:

    ncs_cli -C -u admin

Sync the configuration from the devices:

    # devices sync-from

A device can only be connected through the NETCONF Call Home client if
`/devices/device/state/admin-state` is set to `call-home`. This state prevents
any southbound communication to the device unless the connection has already
been established through the NETCONF Call Home client protocol:

    # config
    (config)# devices device ex0 state admin-state call-home
    (config-device-ex0)# local-user admin
    (config-device-ex0)# ssh host-key-verification reject-mismatch
    (config-device-ex0)# ssh host-key ssh-ed25519
    Value for 'key-data' (<SSH public host key>):
    [Multiline mode, exit with ctrl-D.]
    > [[ enter SSH key from netsim/ex/ex0/ssh/ssh_host_ed25519_key.pub here ]]
    >
    (config-host-key-ssh-ed25519)# exit
    (config-device-ex0)# commit
    (config-device-ex0)# sync-from
    sync-result {
        result false
        info Device ex0 has no call home connection established
    }

NSO will generate an asynchronous notification event whenever there is a
connection request. An application can subscribe to these events and, for
example, add an unknown device to the device tree with the information provided
or invoke actions on the device if it is known.

The `NCS_NOTIF_CALL_HOME_INFO` event is generated for a NETCONF Call Home
connection. The application receives a
`struct ncs_call_home_notification structure`. The received structure is
described in `confd_events.h` include file:

    /* When we receive ncs_call_home_notification structs, */
    /* the type field is either */
    /* of these values - indicating what happened */
    enum ncs_call_home_info_type {
        CALL_HOME_DEVICE_CONNECTED    = 1,
        CALL_HOME_UNKNOWN_DEVICE      = 2,
        CALL_HOME_DEVICE_DISCONNECTED = 3
    };

    /* event delivered from the NCS_NOTIF_CALL_HOME_INFO flag */
    struct ncs_call_home_notification {
        enum ncs_call_home_info_type type; /* type of call home event        */
        char* device;                    /* the name of the device that      */
                                         /* connected, NULL if the device is */
                                         /* unknown                          */
        struct confd_ip ip;                /* IP address of device           */
        uint16_t port;                     /* port of device                 */
        char* ssh_host_key;                /* host key of device             */
        char* ssh_key_alg;                 /* SSH key algorithm of device    */
    }

The `notif.py` file demonstrates an event listener listening to these events.
Start it in another shell:

    python3 notif.py -C </dev/null

NSO will push any outstanding configuration in the device commit queue if an
SSH connection is established and reconnect any NETCONF notification stream.

Tail the `netconf.log` in another shell for log print-outs:

    tail -f logs/netconf.log

Configure a device and commit through the commit queue:

    (config-device-ex0)# config r:sys syslog server 1.2.3.4
    (config-server-1.2.3.4)# commit commit-queue sync | details verbose
    ...
    2019-03-25T12:33:38.824 ncs: commit-queue-id 1553513618718: waiting
    2019-03-25T12:33:38.836 ncs: commit-queue-id 1553513618718: executing
    2019-03-25T12:33:38.837 ncs: commit-queue-id 1553513618718: device ex0: \
    calculating southbound diff...
    2019-03-25T12:33:38.849 ncs: commit-queue-id 1553513618718: device ex0: \
    calculating southbound diff ok [0.012 sec]
    2019-03-25T12:33:38.850 ncs: commit-queue-id 1553513618718: device ex0 \
    transient
    ...

Let the device call home:

    cd netsim/ex/ex0
    env CONFD_IPC_PORT=5010 confd_cmd -c "netconf_ssh_call_home 127.0.0.1 4334"

    ...
    2019-03-25T12:33:38.850 ncs: commit-queue-id 1553513618718: executing
    2019-03-25T12:33:38.907 ncs: commit-queue-id 1553513618718: device ex0: \
    calculating southbound diff...
    2019-03-25T12:33:38.914 ncs: commit-queue-id 1553513618718: device ex0: \
    calculating southbound diff ok [0.007 sec]
    2019-03-25T12:33:38.914 ncs: commit-queue-id 1553513618718: device ex0: \
    connect: device connect...
    2019-03-25T12:33:39.016 ncs: commit-queue-id 1553513618718: device ex0: \
    connect: device connect ok [0.101 sec]
    ...
    commit-queue {
        id 1553513618718
        status completed
    }
    Commit complete

Let an unknown device call home:

    cd netsim/ex/ex1
    env CONFD_IPC_PORT=5011 confd_cmd -c "netconf_ssh_call_home 127.0.0.1 4334"

The netconf.log should have these print-outs:

    <INFO> ... netconf new connection 127.0.0.1:46712 in the call home client
    <INFO> ... netconf device ex0 connected through the call home client
    <INFO> ... netconf new connection 127.0.0.1:55822 in the call home client
    <INFO> ... netconf unknown device 127.0.0.1:55822 tried to connect \
    through the call home client

The event listener should have printed these lines:

    Device connected: device=ex0 ip=127.0.0.1 port=46712 ssh_key_alg=ssh-rsa
    ssh_host_key=...
    Unknown device: ip=127.0.0.1 port=55822 ssh_key_alg=ssh-rsa
    ssh_host_key=...

Cleanup
-------

Stop all daemons and clean all created files:

    ncs --stop
    ncs-netsim stop
    make clean

Further Reading
---------------

+ NSO Operation & Usage Guide: NETCONF Call Home
+ The `demo.sh` script
