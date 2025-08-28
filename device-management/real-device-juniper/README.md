Setting up NSO with a Real NETCONF Device
=========================================

The netconf-device and netconf-ned examples explains the basics around NETCONF
NEDs and packages. This example shows how to set up NSO with a real device
managed through a NETCONF interface when the NED is already available and does
not need to be downloaded and built as with the netconf-ned example.

To run the steps below in this README from a demo shell script using the NSO
C-style CLI instead of J-style after you sourced the `ncsrc` file (see
preparations below):

    ./demo.sh

Preparations
------------

1. Ensure you have sourced the `ncsrc` file in `$NCS_DIR` to set up paths and
   environment variables to NSO. This must be done before running NSO. Adding
   it to your profile is recommended.

2. Optionally, if you do not want to create the example files in this directory
   when, for example, several users are sharing the same NSO installation, you
   can run the example in a separate directory.

3. Create an empty directory, for instance, in your home directory. NSO and the
   simulator will create files and directories in this example. Change to this
   directory before continuing.

**Note**: The juniper-junos-netsim-nc-1.0 is an *example* Junos NED and will
not work well with real Junos devices. While this example applies to using any
NETCONF NED, when using a Cisco-provided NETCONF NED, always check the README
of the NED for additional information.

Setting up NSO to work with a NETCONF Device
--------------------------------------------

Set up NSO with the Network Element Driver (NED) package for the device:

    ncs-setup --dest . --ned-package \
    ${NCS_DIR}/examples.ncs/common/packages/juniper-junos-netsim-nc-1.0

This can be done in another empty directory as well. See the `demo.sh` script
for an example of a setup in a `nso-rundir` directory.

Start the NSO daemon:

    ncs

Start the CLI:

    ncs_cli -u admin

Here we assume:
Host name of Juniper router: olive0.lab and olive1.lab
user on Juniper router: admin
pass to Juniper router  Admin99

Add configuration for a new authentication group:

    > configure

Create the authgroup. Authgroups map the NSO user to the device authentication.
In this example, we map all NSO users to the device's `admin` user by default:

    % edit devices authgroups group junipers
    % set default-map remote-name admin
    % set default-map remote-password Admin99
    % top

Add configuration to NSO for the simulated device:

    % edit devices device olive0
    % set address olive0.lab
    % set port 22
    % set authgroup junipers
    % set device-type netconf ned-id juniper-junos-netsim-nc-1.0
    % set ssh-algorithms public-key [ ssh-ed25519 ssh-rsa ]
    % set state admin-state unlocked
    % top
    % edit devices device olive1
    % set address olive1.lab
    % set port 22
    % set authgroup junipers
    % set device-type netconf ned-id juniper-junos-netsim-nc-1.0
    % set ssh-algorithms public-key [ ssh-ed25519 ssh-rsa ]
    % set state admin-state unlocked

Note how the device `authgroup` configuration refers to the `junipers`
authentication group created above.

By default, NSO requires a known host key for any device it connects to via
SSH. This can be changed per device and on a global level, but it is best
practice from a security point of view to fulfill the requirement. A convenient
way of doing this is to use the `fetch-host-keys` action, which connects to the
device, retrieves the host keys, and stores them in the configuration. Provided
the device is up and running, we can do it here, but it can also be done later:

    % request ssh fetch-host-keys

If we want to initialize NSO with a set of Juniper routers when we start
NSO with an empty database, i.e., with no .cdb files, we can do so with an XML
init file in the ./ncs-cdb directory.

    % show devices authgroups | display xml | save ncs-cdb/authgr.xml
    % show devices device olive0..1 | display xml | save ncs-cdb/dev.xml
    % exit
    > exit

    ncs --stop
    rm ncs-cdb/*.cdb
    ncs

...and the authentication group plus device configuration is loaded at startup.

Configuring the Router
----------------------

Test connecting to the device:

    ncs_cli -u admin
    > request devices connect

Check the address, port, and authentication information if this does not work.

Get the current configuration from the device covered by the YANG model:

    > request devices sync-from

Show the device configuration:

    % show configuration devices device olive0..1 config junos:configuration

Add device configuration through NSO:

    > config
    % set devices device olive0..1 config configuration snmp contact the-boss
    % commit dry-run outformat native
    % commit

Or use device groups to add device configuration through NSO:

    % set devices device-group olives device-name [ olive0 olive1 ]
    % set devices template snmp ned-id juniper-junos-netsim-nc-1.0 config \
      configuration snmp contact the-boss2
    % commit

    % request devices device-group olives apply-template template-name snmp
    % commit dry-run
    % commit

Enable the trace to see what is going on between NSO and the NETCONF devices.
Trace settings only take effect for new connections so disconnect after
changing them:

    % set devices global-settings trace pretty
    % commit
    % request devices disconnect

Change some device configuration through NSO:

    % devices device olive0 config configuration snmp contact the-boss3
    % commit dry-run
    % commit
    % exit
    > exit

Inspect the NETCONF trace from the `olive0` device communication:

    cat ./nso-rundir/logs/netconf-olive0.trace

Cleanup
-------

Stop NSO:

    ncs --stop

If you want to restore the example to the initial configuration:

    ncs-setup --reset

Further Reading
---------------

+ NSO Administration Guide: NED Administration
+ The `demo.sh` script






