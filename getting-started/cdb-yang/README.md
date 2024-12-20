The Configuration Database and YANG Showcases
=============================================

This example is an implementation of the "Extending the CDB with Packages"
and "Building and Testing a Model" showcases that are described in detail in
the NSO Development Guide chapter "CDB and YANG".

Running the Example
-------------------

There is a shell script available that runs the example according to the
showcase steps described in the Development Guide documentation. Run the script
by typing:

    make showcase

The above shell script uses the NSO CLI as the northbound interface. There is
also a Python script variant that uses the NSO RESTCONF northbound interface.
Run it by typing:

    make showcase-rc

Further Reading
---------------

+ NSO Development Guide: CDB and YANG
+ The showcase.sh and showcase_rc.py scripts
