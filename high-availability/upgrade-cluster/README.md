An NSO Rule-based High Availability Three Node Cluster Upgrade Example
======================================================================

This cluster example uses the NSO built-in high availability manager to setup
and manage three NSO nodes, one primary and two secondary nodes, that
implements a single `dummy` service package. Either the NSO version or the
example package version are then upgraded on all three nodes.

Running the Example
-------------------

There are shell scripts available that runs the upgrade examples. Run the NSO
version upgrade script by typing:

    make upgrade-nso

Run a package version upgrade script that simulates a NSO system install
environment and use upgrade commands that work with system install by typing:

    make upgrade-pkgs-sys

The above shell scripts uses the NSO CLI and MAAPI as the northbound interfaces.
There are also Python script variants that uses the NSO RESTCONF northbound
interface. Run them by typing:

    make upgrade-nso-rc
    make upgrade-pkgs-sys-rc

See the Python pip `requirements.txt` file for the required Python packages
that are not built in.

This example requires unique IP addresses for the three NSO nodes. Non-default
IP addresses can be set in the `Makefile` + using flags with the scripts:

    -a  IP address for node 1. Default: 127.0.1.1
    -b  IP address for node 2. Default: 127.0.2.1
    -c  IP address for node 3. Default: 127.0.3.1

On most Linux distributions the above default IP addresses are configured for
the loopback interface by default. On macOS the three unique IP addresses can
be created using for example the `ip` or `ifconfig` command:

macOS example setup:

    sudo ifconfig lo0 alias 127.0.1.1/24 up
    sudo ifconfig lo0 alias 127.0.2.1/24 up
    sudo ifconfig lo0 alias 127.0.3.1/24 up

macOS cleanup:

    sudo ifconfig lo0 -alias 127.0.1.1
    sudo ifconfig lo0 -alias 127.0.2.1
    sudo ifconfig lo0 -alias 127.0.3.1

The NSO version upgrade scripts will execute the following steps:
1.  Reset, setup, start node 1-3, and enable HA with start-up settings
2.  Add some dummy config to node 1, replicated to secondary nodes 2 and 3
3.  Enable read-only mode and create a backup before upgrading
4.  Disable high availability for node 3
5.  Disable node 1 high availability for node 2 to automatically failover and
    assume primary role in read-only mode
6.  Rebuild node 1 package(s) with {new NSO version}
7.  Upgrade node 1 to {new NSO version}
8.  Disable high availability for node 2
9.  Enable high availability for node 1 that will assume primary role
10. Rebuild node 2 package(s) with {new NSO version}
11. Upgrade node 2 to {new NSO version}
12. Enable high availability for node 2 that will assume secondary role
13. Rebuild node 3 package(s) with {new NSO version}
14. Upgrade node 3 to {new NSO version}
15. Enable high availability for node 3 that will assume secondary role
16. Done!

The package upgrade scripts, simulating system install, will execute the
following steps:
1.  Reset, setup, start node 1-3, and enable HA with start-up settings
2.  Add some dummy config to node 1, replicated to secondary nodes 2 and 3
3.  Backup before upgrading
4.  Upgrade node 1 system install packages and sync the packages to node 2 & 3
5.  Add some new config through node 1
6.  Done!

Cleanup
-------

Stop all daemons and clean all created files:

    make -C stop clean

Further Reading
---------------

+ NSO Administrator Guide: NSO HA Version Upgrade and Package Upgrade.
+ The `upgrade_nso.sh`, `upgrade_nso_rc.py`, `upgrade_pkgs_sys.sh`, and 
  `upgrade_pkgs_sys_rc.py` scripts.
