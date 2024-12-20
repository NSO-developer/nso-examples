Nano Service for Provisioning of an MPLS Layer3 VPN
===================================================

This example extends the `examples.ncs/service-provider/mpls-vpn` example with
nano services that launch simulated virtual routers using an Elastic Services
Controller (ESC) to complement the simulated physical ones. For more details on
the simulated MPLS network where layer 3 VPN tunnels will be provisioned,
please refer to the `mpls-vpn` example.

Nano services use a technique called reactive FASTMAP (RFM) and provide a
framework to safely execute actions with side effects by implementing the
service as several smaller (nano) steps or stages. In this example, using a
Network Function Virtualization (NFV) setup, the service L3VPN service starts
one or more Virtual Machines (VM), Virtual Network Functions (VNF), through a
VM manager service. However, the VMs do not yet exist, and the L3VPN nano
service `create()` code needs to trigger the VM manager nano service that
starts the VMs, and then later, when the VMs are operational, configures them.
This requires a multi-step process where nano services are the ideal framework:

1. When the L3VPN nano service `pe-create` state step in the `l3vpn-virtual`
   component of the `l3vpn-plan` create or delete a `/vm-manager/start` service
   configuration instance, the VM manager nano service instructs a VNF-M,
   called ESC, to start or stop the virtual device.

2. The VM manager nano service waits for the ESC to start or stop the virtual
   device monitoring, handling events as they are received from the ESC device.
   Here NETCONF notifications.

3. Mount the new device, simulated by netsim ConfD, in the NSO device tree.

4. Fetch the ssh-keys and perform a `sync-from` on the newly
   created device.

Upon deletion of the service instance, NSO restores the configuration. The only
delete step in the L3VPN nano service plan is the `l3vpn-virtual` component
`init` state pre-condition that checks that the VM manager service instance
created by the L3VPN service has been deleted. See the `l3vpn.yang` YANG model
for the plan implementation. The VM manager `vm-plan` `init` state step
will wait for the `VM_UNDEPLOYED` event before the deletion of the virtual
device is complete. See the `vm-manager.yang` YANG model for the plan
implementation.

See the `l3vpnRFS.java` for details on the Java application that implements
the nano service callbacks for the states/steps in the `l3vpn.yang` YANG model.
The `VmManager.java` application uses the `escstart.java` application to
implement the nano service callbacks for the states/steps in the
`vm-manager.yang` YANG model using the ESC device to deploy
VMs.

All physical and virtual devices are simulated using netsim network elements.
The ESC device is simulated using YANG models and an `escnotif.erl` Erlang
application that starts router VMs (other netsim instances) and sends NETCONF
notification status events which the VM manager application subscribes to.

Running the Example
-------------------

A shell script runs a demonstration where a `volvo` L3VPN service is
commissioned using several simulated physical and one virtual router. Run the
demo by typing:

    make showcase

The above shell script uses the NSO CLI as the northbound interface. The
`showcase.sh` shell script performs similar to the following steps to
commission and decommission the `volvo` L3VPN service:

1. Reset and setup the example

        make stop clean all start

2. Configure a VPN network by creating a new Volvo L3VPN nano service

        ncs_cli -u admin -C
        # devices sync-from
        # config
        (config)# load merge vpn_volvo.xml
        (config)# commit dry-run outformat native
        (config)# commit
        (config)# end

3. Show status after creating the new volvo L3VPN nano service instance

        # show vm-manager start volvo_vpn_CSR plan component * state * status

        TYPE  NAME  STATE           STATUS
        -------------------------------------
        self  self  init            reached
                    init-vm         reached
                    vm-initialized  reached
                    device-created  reached
                    device-ready    reached
                    device-keys     reached
                    device-synced   reached
                    ready           reached

        # show vpn l3vpn volvo plan component * state * status | tab

        TYPE            NAME           STATE              STATUS
        -----------------------------------------------------------
        self            self           init               reached
                                       ready              reached
        l3vpn-init      l3vpn-init     init               reached
                                       ready              reached
        l3vpn-physical  branch-office  init               reached
                                       dev-setup          reached
                                       qos-configured     reached
                                       ready              reached
        l3vpn-virtual   head-office    init               reached
                                       pe-created         reached
                                       ce-vpe-topo-added  reached
                                       vpe-p0-topo-added  reached
                                       dev-setup          reached
                                       qos-configured     reached
                                       ready              reached

        # show running-config topology
        # show running-config devices device esc config
        # show running-config devices device volvo_vpn_CSR_esc0 port
        # exit
        ncs-netsim list
        ...
        name=volvo_vpn_CSR0 netconf=12029 ...

4. Delete `head-office` endpoint

        ncs_cli -u admin -C
        # config
        (config)# no vpn l3vpn volvo endpoint head-office
        (config)# commit dry-run outformat native
        (config)# commit
        (config)# end
        # show zombies

5. Show status after deleting the `head-office` endpoint

        # show vpn l3vpn volvo plan component * state * status | tab

        TYPE            NAME           STATE           STATUS
        --------------------------------------------------------
        self            self           init            reached
                                       ready           reached
        l3vpn-init      l3vpn-init     init            reached
                                       ready           reached
        l3vpn-physical  branch-office  init            reached
                                       dev-setup       reached
                                       qos-configured  reached
                                       ready           reached

        # show devices device | display-level 1
        # show vm-manager start
        # show running-config devices device esc config
        # exit
        ncs-netsim list

6. Reconfigure the Volvo L3VPN nano service to include the 'head-office'
   endpoint again

        ncs_cli -u admin -C
        # config
        (config)# load merge vpn_volvo.xml
        (config)# commit dry-run outformat native
        (config)# commit
        (config)# end
        # exit
        ncs-netsim list
        ...
        name=volvo_vpn_CSR0 netconf=12029 ...

6. Decommission the Volvo VPN

        ncs_cli -u admin -C
        # config
        (config)# no vpn l3vpn volvo
        (config)# commit dry-run outformat native
        (config)# commit
        (config)# end

7. Show status after decommissioning the Volvo VPN

        # show vpn l3vpn
        # show devices device | display-level 1
        # show vm-manager start
        # show running-config devices device esc config
        # exit
        ncs-netsim list

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Nano Services Using Reactive FASTMAP Techniques
+ NSO Development Guide: Nano Services for Staged Provisioning
+ The showcase.sh script
+ The l3vpn.yang module and l3vpnRFS.java application
+ The vm-manager.yang and escstart.java VmManager.java applications
+ The tailf-ncs-plan.yang and tailf-ncs-services.yang modules
