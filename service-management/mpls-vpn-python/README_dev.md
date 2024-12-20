MPLS Layer3 VPN Example
=======================

This example illustrates Layer3 VPNs in a service provider MPLS network. This
README file contains information related to the development of the example and
the l3vpn service.

Packages
--------

* `l3vpn` - This package contains the data model and service mapping for the
  actual L3VPN service.
* NED Packages - Three different NED packages are copied to the example
  environment during the build process: `cisco-ios`, `cisco-iosxr`, and
  `juniper-junos`.

Service Mapping
---------------

The service mapping in this example uses a mix of Python code and configuration
templates.

The Python code contains the logic for deriving what PE routers and QOS
templates to use (described in more detail below). The Python code only sets
abstract parameters used in the templates and is completely de-coupled from the
mapping to the actual device configuration.

The configuration templates use parameters set from the Python code and
declarative references directly to attributes in the service mode. The
templates map these parameters to the data model representation of device
configuration for all device types in the network.

     Service-Model
          !
     Python Mapping Logic
          !
          !-Vendor-independant variables
          !
     Template
          !
     Vendor Device Model

Python Service Logic
------------------

The Python service logic is found in the l3vpn package, and the relevant Python
file is `packages/l3vpn/python/service.py`.

A new l3vpn service instance is a list of a number of endpoints, where each
endpoint is a reference to a CE device and its local interface. The logic in
the Python code has the following outline:

    FOR EACH endpoint
    // VPN related logic
        lookup what PE device the CE device is connected to using the
            topology
        set global VPN config template parameters (as-number, etc.)
        set CE router config template parameters
        set PE router config template parameters
        apply the ce-router feature template
        apply the pe-router feature template

    // QOS related logic
        lookup QOS policy referenced from the l3vpn service instance
        set QOS classification parameters
        set QOS marking/policing parameters
        apply CE/PE classification template
        apply CE/PE marking template

        merge QOS policy global and service classification ACL information
        set QOS classification access list parameters
        apply QOS ACL template
     END

Feature Templates
-----------------

The l3vpn package includes the following feature configuration templates:

l3vpn-acl.xml            - Access list configuration related to QOS
l3vpn-ce.xml             - CE router VPN configuration
l3vpn-pe.xml             - PE router VPN configuration
l3vpn-qos-class.xml      - CE QOS classification configuration
l3vpn-qos-pe-class.xml   - PE QOS classification configuration
l3vpn-qos-pe.xml         - PE QOS marking and policing configuration
l3vpn-qos.xml            - CE QOS marking and policing configuration
