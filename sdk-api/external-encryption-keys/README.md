Loading Crypto Keys From an External Application
================================================

This example demonstrates using an external application to configure the
encryption keys used by the built-in NSO crypto types.

The application `external_crypto_keys.py` will read the encryption keys from
the file pointed to by the environment variable `NCS_EXTERNAL_KEYS_ARGUMENT`.
`NCS_EXTERNAL_KEYS_ARGUMENT` contains the
`/ncs-config/encrypted-strings/external-keys/command-argument` value from
`ncs.conf`.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Build the example:

    make all

Start NSO

    ncs

Store some secrets in the database:

    ncs_load -lm example_clear.xml

Show the database content:

    ncs_load -Fp -p /string

Stop NSO:

    ncs --stop

Cleanup
-------

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Development Guide: Encryption Keys
+ The `demo.sh` script
+ The `Makefile` changes to `ncs.conf` and generating keys.