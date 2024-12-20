Management Agent API (MAAPI)
============================

This example shows some aspects of the MAAPI and NAVU API and uses two NSO
packages, the `router-nc-1.1` package and a package named `maapi`. The `maapi`
package includes Java code examples and a `maapi-types.yang` model. The
`maapi-types.yang` can be found in the `./packages/maapi/src/yang` directory
and is a model used to illustrate the concepts of MAAPI and NAVU.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Build the two packages:

    make all

Start the simulated network:

    ncs-netsim start

Start NSO:

    ncs

Sync the configuration from the devices:

    ncs_cli -u admin
    > request devices sync-from

Eclipse
-------

If you use the Eclipse IDE, you can use `ncs-setup` to scan packages and set up
a working environment.

    ncs-setup --eclipse-setup

The command `ncs-setup --eclipse-setup` can be executed many times when you
change or add packages unless you want to set up class paths from Eclipse
itself manually.

Start Eclipse and create a new Java project, File:New:Java Project. Uncheck
'Use default location', browse to the directory of this example, and press
'Finish'.

You can now examine the examples and run the code from inside Eclipse.

Console
-------

To study this example, first take a look at the file:

    packages/maapi/src/java/build.xml

Java code in a package gets compiled into jar files. The jar files that reside
in either `./shared-jar` or `./private-jar` in the package get loaded by the
NSO Java VM classloader. This example contains standalone code we do not want
to be loaded into the NSO Java VM.

All the examples are started through different ant targets. To, for example,
run the `cursor` target with the Java code in
`packages/maapi/src/java/test/com/example/maapi/ExampleMaapiCursor.java`:

    ant -f ./packages/maapi/src/java/ cursor

To list the specified targets:

    ant -f ./packages/maapi/src/java/ -p

Main targets:

    cursor          Exemplify use of Maapi Cursor
    dns             Illustrates the usage of move in NavuList
    prepset         Setting values to devices using Navu and PreparedXML
    printdevschema  Prints tree structure over schema nodes and values
    query           XPathQuery (usage: ant query -Darg="'<Query>:XPath-expr' \
                    '<selects>:node0,node1...,nodeN' \
                    -n '<num>:result-iterations' \
                    -c '<chunksize>:num' -o '<offset>:1..N' \
                    -x  '<initial-context>:QueryCTX(default '/')' \
                    -r <[t:ConfXMLParam|v:KeyPath and \
                    ConfValue|p:KeyPath|s:String]>'")
    setelems        Maapi.setElem() examples for different types
    setvalues       Maapi setValues() examples
    simplequery1    Simple XPath Query 1
    valtodevices    Set values to devices using Navu/Maapi and PreparedXML
    xmlshow         Shows values in XML-format for devices

Cleanup
-------

Stop NSO, the simulated network, and clean all created files:

    ncs --stop
    ncs-netsim stop
    make clean

Further Reading
---------------

+ NSO Development Guide: NSO SNMP Agent
+ The `demo.sh` script
