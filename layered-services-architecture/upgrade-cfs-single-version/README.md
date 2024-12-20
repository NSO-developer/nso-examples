Upgrading NSO for an LSA Upper Layer Instance
=============================================

This example extends the `lsa-single-version-deployment` example and implements
a simple shell script to show how an NSO version upgrade of the upper NSO
instance can performed. This example upgrade NSO for an LSA single version
deployment.

Running the Example
-------------------

There is a shell script available that runs the example. Run the script and
step through an upgrade by typing:

    make start

The shell script will then use default values and perform the upgrade steps to
upgrade the upper NSO instance of the `lsa-single-version-deployment` example
to the same NSO version for demo purposes. To, for example, upgrade the NSO
6.3.4 `lsa-single-version-deployment` example upper layer NSO instance to the
version that the `NCS_DIR` environment variable points to, replace the
`/Users/tailf/nso-6.4` path below with the location of NSO 6.4 in your
system and type:

    ./upper_nso_upgrade.sh -o 6.3.4 -d /Users/tailf/nso-6.4

Replace the version number above to match the version you want to upgrade from.
Use the `-p` and `-n` flags to point out an NSO version to upgrade the upper
LSA layer NSO to that is different than what the `NCS_DIR` environment variable
points to.

When, for example, upgrading the upper LSA NSO instance from NSO 4.7.10 to NSO
5.7, the following steps in the script are important to such upgrades:

0. Backup
1. Replace the old NSO 4.7.10 tailf/cisco-nso-nc-4.7 package with the NSO 5.7
   NETCONF NED package for 4.7.
2. Update the Makefile for the rfs-ned package and rebuild it.
3. Add the --ncs-ned-id tailf-ncs-ned:lsa-netconf flag when upgrading from NSO
   4.7 to 5.x.
4. Rebuild the RFS NED package.
5. Make the necessary changes to ncs.conf to upgrade from NSO 4.7.10 to 5.7.
6. When upgrading from NSO 4.7 to 5.6 or newer we need to add ssh-rsa to the
   list of supported algorithms to the 5.6 or newer upper NSO instance
   configuration.

See the `upper_nso_upgrade.sh` script for details.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Administration Guide: Layered Service Architecture
+ The upper_nso_upgrade.sh script
+ The `lsa-single-version-deployment` example.
