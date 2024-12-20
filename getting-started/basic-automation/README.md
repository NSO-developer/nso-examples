Basic Automation with Python Showcase
=====================================

This example is an implementation of the "Configuring DNS with Python" showcase
that is described in detail in the NSO Development Guide chapter "Basic
Automation with Python".

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

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Basic Automation with Python
+ The showcase.sh and showcase_rc.py scripts
