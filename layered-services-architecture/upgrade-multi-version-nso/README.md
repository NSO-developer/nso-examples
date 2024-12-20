Upgrading NSO for an LSA Multi-Version Deployment
=================================================

This example extends the `lsa-multi-version-deployment` example and implements
a simple shell script to show how an NSO version upgrade of the upper NSO
instance and one of the lower NSO instances can be performed.

Running the Example
-------------------

There is a shell script available that runs the example. Run the script and
step through an upgrade by typing:

    make start

The shell script will then use default values and perform the upgrade steps to
upgrade the `upper-nso` and `lower-nso-1` instances of the
`lsa-multi-version-deployment` example to the same NSO version for demo
purposes. To, for example, upgrade the NSO 6.3.4 `lsa-multi-version-deployment`
example upper layer NSO instance to the version that the `NCS_DIR` environment
variable points to (e.g. 6.4), replace the "/Users/tailf/nso-6.45" path below
with the location of NSO 6.4 in your system and type:

    ./multiver_nso_upgrade.sh -o 6.3.4 -d /Users/tailf/nso-6.4

Replace the version number above to match the version you want to upgrade from.
Use the `-p` and `-n` flags to point out an NSO version to upgrade the upper
and one of the lower LSA layer NSO instances to that is different than what the
`NCS_DIR` environment variable points to. Get more details using the `-h` flag.

When, for example, upgrading the upper and one of the lower LSA NSO instances
from NSO 5.4.5 to NSO 5.7, the following steps in the script are important for
such upgrades:

0. Backup
1. Rebuild the lower-nso-1 packages with the new NSO 5.7.
2. Make the necessary changes to the lower-nso-1 ncs.conf to upgrade from NSO
   5.4.5 to 5.7.
3. Restart the lower-nso-1 NSO instance and upgrade to 5.7.
4. Rebuild the cfs-vlan package with the new NSO 5.7.
5. Replace the old NSO 5.4.5 tailf/cisco-nso-nc-5.4 package with the NSO 5.7
   NETCONF NED package for 5.4 and add the 5.7 to support both.
6. Copy the rfs-vlan-nc-5.4 to rfs-vlan-nc-5.7 and update the
   package-meta-data.xml + Makefile for the rfs-vlan-nc-5.7 package and rebuild
   the RFS NEDs.
7. Make the necessary changes to the upper-nso ncs.conf to upgrade from NSO
   5.4.5 to 5.7.
8. Restart the upper NSO instance and upgrade to 5.7.
9. Migrate the lower-nso-1 5.4.5 NED ID to 5.7.

See the `multiver_nso_upgrade.sh` script for details.

Further Reading
---------------

+ NSO Administration Guide: Layered Service Architecture
+ The multiver_nso_upgrade.sh script
+ The `lsa-multi-version-deployment` example.
