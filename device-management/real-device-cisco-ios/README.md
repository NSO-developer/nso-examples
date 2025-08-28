Setting up NSO with a Real CLI Device
=====================================

The simulated-cisco-ios example explains the basics around CLI NEDs and
packages and gives an overview of the NSO CLI commands. This example shows how
to set up NSO with a real device managed through a CLI interface instead of
pre-configuring NSO with netsim devices, and is valid for any CLI NED.

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

**Note**: The cisco-ios-netsim-cli-1.0 is an *example* Cisco IOS NED and will
not work well with real Cisco IOS devices. While this example applies to using
any CLI NED, when using a Cisco-provided CLI NED, always check the README of
the NED for additional information.

Setting up NSO to work with a CLI Device
----------------------------------------

Set up NSO with the Network Element Driver (NED) package for the device:

    ncs-setup --dest . --ned-package \
    ${NCS_DIR}/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0

This can be done in another empty directory as well. See the `demo.sh` script
for an example of a setup in a `nso-rundir` directory.

Start the NSO daemon:

    ncs

Start the CLI:

    ncs_cli -u admin

NSO will load the cisco-ios-netsim-cli-1.0 NED package at startup:

    > show packages
    packages package cisco-ios-netsim-cli-1.0
     package-version 3.0.0.4
     description     "NED package for Cisco IOS"
     ncs-min-version [ 3.0.2 ]
     directory       ./state/packages-in-use/1/cisco-ios-netsim-cli-1.0
     component upgrade-ned-id
      upgrade java-class-name com.tailf.packages.ned.ios.UpgradeNedId
     component cisco-ios
      ned cli ned-id  cisco-ios-netsim-cli-1.0
      ned cli java-class-name com.tailf.packages.ned.ios.IOSNedCli
      ned device vendor Cisco
      ned option show-tag
       value interface
     oper-status up

Add configuration for a new authentication group:

    > configure

Create the authgroup. Authgroups map the NSO user to the device authentication.
In this example, we map all NSO users to the device's `admin` user by default:

    % edit devices authgroups group mygroup
    % set default-map remote-name admin
    % set default-map remote-password admin
    % top

Add configuration to NSO for the simulated device:

    % edit devices device myrouter0
    % set address [ADD ADDRESS HERE]
    % set port 22
    % set authgroup mygroup
    % set device-type cli ned-id cisco-ios-netsim-cli-1.0
    % set ssh-algorithms public-key [ ssh-ed25519 ssh-rsa ]
    % set state admin-state unlocked

Note how the device `authgroup` configuration refers to the `mygroup`
authentication group created above.

By default, NSO requires a known host key for any device it connects to via
SSH. This can be changed per device and on a global level, but it is best
practice from a security point of view to fulfill the requirement. A convenient
way of doing this is to use the `fetch-host-keys` action, which connects to the
device, retrieves the host keys, and stores them in the configuration. Provided
the device is up and running, we can do it here, but it can also be done later:

    % request ssh fetch-host-keys

The action prints the "fingerprint" for each key so we can verify that we
received the correct keys if we have some independent information about what
the fingerprints should be. Let's commit the authentication group and device
configuration:

    % top
    % commit
    % show devices device myrouter0

Save the configuration to files in case you want to restore it later:

    % save authgr.cfg devices authgroups
    % save-device-cfg dev.cfg

To restore the saved configuration from the backup files:

    % load merge authgr.cfg
    % load merge dev.cfg
    % commit
    % show devices device myrouter0
    % exit

Configuring the Router
----------------------

Test connecting to the device:

    > request devices connect

Check the address, port, and authentication information if this does not work.

Get the current configuration from the device covered by the YANG model:

    > request devices sync-from

This ingests the configuration from the device into CDB. Only the parts covered
by the YANG model(s) will be loaded. The YANG data model is located under:

    $NCS_DIR/examples.ncs/common/packages/cisco-ios-netsim-cli-1.0/src/yang/\
    tailf-ned-cisco-ios.yang

Show the device configuration:

    > show configuration devices device myrouter0 config

Add some device configuration through NSO:

    > configure
    % set devices device myrouter0 config ios:interface GigabitEthernet 1 ip \
      address primary address 192.168.1.1 mask 255.255.255.0

Inspect the changes:

    % commit dry-run

Commit the changes:

    % commit

In case, for example, network engineers bypass NSO and do local modifications,
NSO can diff the NSO database with the actual device by:

    % request devices device myrouter0 compare-config

An NSO user can then synchronize in either direction:

    % request devices device myrouter0 sync-from

Or:

    % request devices device myrouter0 sync-to
    % exit
    > exit

The sync-to command has a dry-run option
Also try the WebUI at http://http://127.0.0.1:8080.

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

