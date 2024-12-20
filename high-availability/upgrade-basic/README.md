A Basic NSO Rule-based High Availability Two Node Upgrade Example
=================================================================

This basic example uses the NSO rule-based high availability manager to setup
and manage two NSO nodes, one primary and one secondary node, that implements a
single `dummy` service package. Either the NSO version or the example package
version are then upgraded on both nodes.

Running the Example
-------------------

There are shell scripts available that run the upgrade examples. Run the
NSO version upgrade script by typing:

    make upgrade-nso

Run the package version upgrade script by typing:

    make upgrade-pkgs

Run a package version upgrade script that simulates a NSO system install
environment and use upgrade commands that work with system install by typing:

    make upgrade-pkgs-sys

The above shell scripts uses the NSO CLI and MAAPI as the northbound
interfaces. There are also Python script variants that uses the NSO RESTCONF
northbound interface. Run them by typing:

    make upgrade-nso-rc
    make upgrade-pkgs-rc
    make upgrade-pkgs-sys-rc

See the Python pip `requirements.txt` file for the required Python packages
that are not built in.

This example requires unique IP addresses for the two NSO nodes. Non-default IP
addresses can be set in the `Makefile` + using flags with the scripts:

    -a  IP address for node 1. Default: 127.0.1.1
    -b  IP address for node 2. Default: 127.0.2.1

On most Linux distributions the above default IP addresses are configured for
the loopback interface by default. On macOS the two unique IP addresses can be
created using for example the `ip` or `ifconfig` command:

macOS example setup:

    sudo ifconfig lo0 alias 127.0.1.1/24 up
    sudo ifconfig lo0 alias 127.0.2.1/24 up

macOS cleanup:

    sudo ifconfig lo0 -alias 127.0.1.1
    sudo ifconfig lo0 -alias 127.0.2.1

The NSO version upgrade scripts will execute the following steps:
1.  Reset, setup, start node 1 & 2, and enable HA with start-up settings
2.  Add some dummy config to node 1, replicated to secondary node 2
3.  Enable read-only mode and create a backup before upgrading
4.  Disable node 1 high availability for node 2 to automatically failover and
    assume primary role in read-only mode
5.  Rebuild node 1 package(s) with {new NSO version}
6.  Upgrade node 1 to {new NSO version}
7.  Disable high availability for node 2
8.  Enable high availability for node 1 that will assume primary role
9.  Rebuild node 2 package(s) with {new NSO version}
10. Upgrade node 2 to {new NSO version}
11. Enable high availability for node 2 that will assume secondary role
12. Done!

The local install package upgrade scripts will execute the following steps:
1.  Reset, setup, start node 1 & 2, and enable HA with start-up settings
2.  Add some dummy config to node 1, replicated to secondary node 2
3.  Enable read-only mode and create a backup before upgrading
4.  Disable node 1 high availability for node 2 to automatically failover and
    assume primary role in read-only mode
5.  Upgrade node 1 local install packages
6.  Disable high availability for node 2
7.  Enable high availability for node 1 that will assume primary role
8.  Upgrade node 2 local install packages and enable high availability
    to assume secondary role
9.  Add some new config through node 1
10. Done!

The system install package upgrades scripts use the
`/packages/ha/sync and-reload` command to greatly simplify the package upgrade:
1.  Reset, setup, start node 1 & 2, and enable HA with start-up settings
2.  Add some dummy config to node 1, replicated to secondary node 2
3.  Backup before upgrading
4.  Upgrade node 1 system install packages and sync the packages to node 2.
5.  Add some new config through node 1
6.  Done!

Cleanup
-------

Stop all daemons and clean all created files:

    make -C stop clean

Further Reading
---------------

+ NSO Administrator Guide: NSO HA Version Upgrade and Package Upgrade.
+ The `upgrade_nso.sh`, `upgrade_nso_rc.py`, `upgrade_pkgs.sh`,
  `upgrade_pkgs_rc.py`, `upgrade_pkgs_sys.py`, and `upgrade_pkgs_sys_rc.py`
  scripts.
