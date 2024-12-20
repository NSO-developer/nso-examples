A Basic NSO Rule-based High Availability Two Node Automatic Failover Example
============================================================================

This basic example uses the NSO rule-based high availability manager to setup
and manage two NSO nodes, one primary and one secondary node, that implements a
single `dummy` service package. The high-availability configuration, see the
Makefile, enable automatic start-up and failover.

Running the Example
-------------------

There is a shell script available that runs the example. Run the script by
typing:

    make demo

The above shell script uses the NSO CLI and MAAPI as the northbound interfaces.
There is also a Python script variant that uses the NSO RESTCONF northbound
interface. Run it by typing:

    make demo-rc

See the Python pip `requirements.txt` file for the required Python packages.

This example require unique IP addresses for the two NSO nodes. Non-default IP
addresses can be set in the `Makefile` + using flags with the scripts:

    -a  IP address for node 1. Default: 127.0.1.1
    -b  IP address for node 2. Default: 127.0.2.1

On most Linux distributions the above default IP addresses are configured for
the loopback interface by default. On macOS the two unique IP addresses can be
created using for example the `ip` or `ifconfig` command.

macOS setup:

    sudo ifconfig lo0 alias 127.0.1.1/24 up
    sudo ifconfig lo0 alias 127.0.2.1/24 up

macOS cleanup:

    sudo ifconfig lo0 -alias 127.0.1.1
    sudo ifconfig lo0 -alias 127.0.2.1

The scripts will execute the following steps:

1. Reset, setup, start node 1 & 2, and enable HA assuming start-up settings
2. Add some dummy config to node 1, replicated to secondary node 2
3. Stop node 1 to make node 2 failover to primary role
4. Start node 1 that will now assume secondary role
5. Role-revert the nodes back to start-up settings
6. Done!

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: NSO Rule-based HA
+ The `demo.sh` and `demo_rc.py` scripts
