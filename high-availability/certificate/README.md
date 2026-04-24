Certificate Provisioning Example
================================

NSO requires TLS certificates to make communication secure in a high
availability cluster. This example shows how to configure NSO to use a
certificate from a private CA.

The example relies on the implementation of a simple private CA in the
`ca/` directory to generate the certificates and accompanying files.
The `ha-tls/` folder next to the `ncs.conf` file holds the TLS related
binary files from the CA and `ncs.conf.d/` contains the `ha.conf` file
with NSO HA-related configuration, referencing the TLS certificates.

Using `make ha` (the default target), NSO is configured to use rule-based
HA with TLS-encrypted connections. Alternatively, you can use `make raft`
to configure NSO Raft-based HA with the same certificates.


Further Reading
---------------

+ NSO Administration Guide: High Availability
