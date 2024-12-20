Upgrading an LSA Upper Layer Instance from Single to Multi-version Deployment
=============================================================================

This example extends the `lsa-single-version-deployment` example and implements
a simple shell script to show how an NSO version upgrade of the upper NSO
instance can performed while at the same time switching from a single version
to a multi-version deployment.

Note: to upgrade from a pre-5.x single version deployment to a multi-version
NED. First upgrade the pre-5.x instance to a 5.x single version NED. Then
upgrade the 5.x single version to a multi-version NED as described for this
example.

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

When, for example, upgrading the upper LSA NSO instance from NSO 5.4.5 to NSO
5.7, while switching from a single version deployment to a multi-version
deployment the following steps in the script are important to such upgrades:

0. Backup
1. Replace the old NSO 5.4.5 tailf/cisco-nso-nc-5.4 package with the NSO 5.7
   NETCONF NED package for 5.4.
2. Copy the rfs-vlan-ned to rfs-vlan-nc-5.4.
3. Have the RFS NED makefile --ncs-ned-id flag use the multi-version NED ID.
4. Update the package-meta-data.xml for the rfs-vlan-nc-5.4 package.
5. Rebuild the single and multi-version RFS package.
6. Make the necessary changes to ncs.conf to upgrade from NSO 5.4.5 to 5.7.
   Restart the upper NSO instance and upgrade to 5.7.
7. Migrate the upper NSO single version NED ID to a multi-version NED ID.
8. Delete the single version rfs-vlan-ned package and reload packages.
   Warning: If the migrate to the multi-version NED ID earlier failed, we will
   loose data if we delete the single version NED.

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
