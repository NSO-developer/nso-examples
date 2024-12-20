MPLS Layer3 VPN Example
=======================

This version of the MPLS VPN example illustrates a template-centric
implementation where the template drives the main logic while the Java code
performs auxiliary computations. The example is the same functionality-wise as
the `mpls-vpn-java` example.

This example illustrates Layer3 VPNs in a service provider MPLS
network. This README file contains information related to the development of
the example and the l3vpn service.

Packages
--------

* `l3vpn` - This package contains the data model and service mapping for the
  actual L3VPN service.
* NED Packages - Three different NED packages are copied to the example
  environment during the build process: `cisco-ios`, `cisco-iosxr`, and
  `juniper-junos`.

Service Mapping
---------------

This example's service mapping is template-centric but involves some Java code.

The main task of the Java code is to perform IP address/mask computations such
as network prefix to network mask transformation or finding the following
available IP address. The calculation result is stored in the operational data
in the transaction and later picked up by the template. After performing the
necessary calculations, the Java code invokes the template.

The configuration templates use XPath to select from both the service
configuration data and auxiliary operational data created by the service. The
templates map this input data to the data model representation of device
configuration for all device types in the network.

    Service-Model ------ Java Logic
         |                   |
         |             Auxiliary data
         \            /
          \          /
           \        /
            Template
               |
        Vendor Device Model

         Service-Model
              !
         Java Mapping Logic
              !
              !-Vendor-independant variables
              !
         Template
              !
         Vendor Device Model

Java Logic
----------

The Java logic is in the l3vpn package and the relevant Java file:
`packages/l3vpn/src/java/src/com/example/l3vpn/l3vpnRFS.java`.

There are a number of `config false` leafs in the `l3vpn.yang data` model. All
of them are the auxiliary data that is populated by the Java code.

The approach taken in this example is to populate the auxiliary data in the
`PRE_MODIFICATION` callback. This is primarily beneficial for the auxiliary
data located in the shared structures to ensure it does not get deleted when
the service is deleted. But also applies to the data inside the service
instance itself as it will be recomputed every time the service is
touched or deleted if the subtree this data belongs to is removed.

Template
--------

There is only one template in the l3vpn package: `l3vpn.xml`. It contains the
main service logic. Yet because the service is implemented as a mix of Java and
template, the template must be invoked from the Java code.

No variables are passed to the template when it is invoked. Instead, the
template reads all of the input from the transaction. However, for convenience,
the template does use several variables, set in the template itself.
