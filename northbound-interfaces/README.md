Northbound Interfaces Examples
==============================

Northbound programmatic APIs in NSO: NETCONF, RESTCONF, WebUI/JSON-RPC, and
SNMP.

See each example for a detailed description, additional requirements, and
pointers to further reading.

Suggested Order of Consumption:
-------------------------------

### restconf
A README and demo script showcasing how to work with the RESTCONF API using the
`website-service` example.

### restconf-netconf-notifications
Showcases how to generate and subscribe to NSO northbound RESTCONF and NETCONF
notifications, including YANG-Push notifications. Supports the NSO Development
Guide on the NETCONF and RESTCONF Northbound APIs.

### webui
Demonstrates how to use and update a custom Web UI package using the
JSON-RPC API to query data from NSO.

### snmp-mib
Show how a simple proprietary SNMP MIB is used to access data from a YANG
module called `simple.yang`.

### snmp-alarm
A README and demo script showcasing integrating the NSO northbound SNMP alarm
agent into an SNMP-based alarm management system using the `website-service`
example. It also introduces how to use Net-SNMP tools to inspect the alarm
interface.
