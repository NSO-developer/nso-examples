Kicker Example
==============

The following is an illustration of the NSO kicker functionality. Kickers are a
declarative way of invoking actions from events like CDB data changes
(data-kicker) or NETCONF notifications (notification-kicker). The programming
part of a kicker consists only of implementing a suitable action if it does
not already exist.

The action invocation is performed in one of two ways:

* If the action input parameters are consistent with the
  `kicker:action-input-param` grouping in the `tailf-kicker.yang`, the
  following parameters are supplied with the invocation:

      Id    - The kicker id
      Path  - The path to the subtree containing the change
      Tid   - The transaction ID for a synthetic transaction containing the
              change

* The the action is invoked with an empty parameter list in cases other than
  the one above,

The supplied transaction is synthetic because it contains the changes that lead
to the kicker triggering. However, it is a copy of the original transaction
already committed. Therefore, the transaction may no longer reflect the current
CDB data. This transaction is typically used for attachment and diff-iteration
to process the action further.

A data kicker has a `monitor` that pinpoints the subtree under which changes
should trigger the kicker. There is also a `trigger-expr`, an XPath expression
that acts as a filter for which changes should trigger the kicker.

A notification-kicker knows all notifications received by subscriptions
under `/ncs:devices/device/notification/subscription`. The notification-kicker
has a `selector-expr`, an XPath expression that filters out the notifications
that should trigger the kicker. Under evaluation of the `selector-expr`, three
variable bindings are predefined for the XPATH evaluation:

* DEVICE            - The name of the device emitting the notification.
* SUBSCRIPTION_NAME - The name of the subscription receiving the notification
* NOTIFICATION_NAME - The name of the current notification

For more details on how to define kickers, see the `tailf-kicker.yang` YANG
module.

This example illustrates both the setup of a simple data-kicker as well as a
notification-kicker. Both kickers will use the same action, implemented
by the `website-service` package. The following is the YANG snippet for the
action definition from the `web-site.yang` file:

    module web-site {
      namespace "http://examples.com/web-site";
      prefix wse;

      ...

      augment /ncs:services {

        ...

        container actions {
          tailf:action diffcheck {
            tailf:actionpoint diffcheck;
            input {
              uses kicker:action-input-params;
            }
            output {
            }
          }
        }
      }
    }

The implementation of the action can be found in the `WebSiteServiceRFS.java`
file. Since it takes the `kicker:action-input-params` as input, the transaction
ID for the synthetic transaction is available. This transaction is attached and
diff-iterated. The result of the diff-iteration is printed in the
`ncs-java-vm.log`.

    class WebSiteServiceRFS {

        ...

        @ActionCallback(callPoint="diffcheck", callType=ActionCBType.ACTION)
        public ConfXMLParam[] diffcheck(DpActionTrans trans, ConfTag name,
                                      ConfObject[] kp, ConfXMLParam[] params)
        throws DpCallbackException {
            try {

                System.out.println("-------------------");
                System.out.println(params[0]);
                System.out.println(params[1]);
                System.out.println(params[2]);

                ConfUInt32 val = (ConfUInt32) params[2].getValue();
                int tid = (int)val.longValue();

                Socket s3 = new Socket("127.0.0.1", Conf.NCS_PORT);
                Maapi maapi3 = new Maapi(s3);
                maapi3.attach(tid, -1);

                maapi3.diffIterate(tid, new MaapiDiffIterate() {
                    // Override the Default iterate function in the TestCase class
                    public DiffIterateResultFlag iterate(ConfObject[] kp,
                                                        DiffIterateOperFlag op,
                                                        ConfObject oldValue,
                                                        ConfObject newValue,
                                                        Object initstate) {
                        System.out.println("path = " + new ConfPath(kp));
                        System.out.println("op = " + op);
                        System.out.println("newValue = " + newValue);
                        return DiffIterateResultFlag.ITER_RECURSE;

                    }

                });


                maapi3.detach(tid);
                s3.close();


            return new ConfXMLParam[]{};

            } catch (Exception e) {
                throw new DpCallbackException("diffcheck failed", e);
            }
        }
    }

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo-kicker

The below steps are similar to the demo script.

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make all

Start the simulated network and NSO:

    make start

Sync the configuration from the devices:

    ncs_cli --user=admin -C
    # devices sync-from

The kickers are defined under the hidden group `debug`. To be able to show and
declare kickers, we need first to unhide this hidden group:

    # unhide debug

Data Kicker
-----------

We now define a data-kicker for the "profile" list under the by the service
augmented container "/services/properties/wsp:web-site":

    # config
    (config)# kickers data-kicker a1 \
              monitor /services/properties/wsp:web-site/profile \
              kick-node /services/wse:actions action-name diffcheck
    (config-data-kicker-a1)# commit dry-run
    (config-data-kicker-a1)# commit
    (config-data-kicker-a1)# top
    (config)# show full-configuration kickers data-kicker a1
    kickers data-kicker a1
    monitor     /services/properties/wsp:web-site/profile
    kick-node   /services/wse:actions
    action-name diffcheck

We now commit a change in the profile list, and we use the `debug kicker` pipe
option to be able to follow the kicker invocation:

    (config)# services properties web-site profile lean lb lb0
    (config-profile-lean)# commit | debug kicker
    kicker: a1 at /ncs:services/... changed, invoking diffcheck
    Commit complete.
    (config-profile-lean)# top
    (config)# exit

We can also check the result of the action by looking into the
`ncs-java-vm.log` log file:

    # file show logs/ncs-java-vm.log

At the end of the log file, we will find the following printout from the
`diffcheck` action:

    ...
    {[669406386|id], a1}
    {[669406386|monitor], /ncs:services/properties/web-site/profile{lean}}
    {[669406386|tid], 168}
    path = /ncs:services/properties/wsp:web-site/profile{lean}
    op = MOP_CREATED
    newValue = null
    path = /ncs:services/properties/wsp:web-site/profile{lean}/name
    op = MOP_VALUE_SET
    newValue = lean
    path = /ncs:services/properties/wsp:web-site/profile{lean}/lb
    op = MOP_VALUE_SET
    newValue = lb0

Notification Kicker
-------------------

The `website-service` example uses devices that generate NETCONF notifications
on an `interface` notification stream. We start with defining the notification
kicker for a certain `SUBSCRIPTION_NAME = "mysub"`. This subscription does not
exist for the moment, and the kicker will, therefore, not be triggered:

    # config
    (config)# kickers notification-kicker n1 \
              selector-expr "$SUBSCRIPTION_NAME = 'mysub'" \
              kick-node /services/wse:actions \
              action-name diffcheck
    (config-notification-kicker-n1)# commit
    (config-notification-kicker-n1)# top
    (config)# show full-configuration kickers notification-kicker n1
    kickers notification-kicker n1
    selector-expr "$SUBSCRIPTION_NAME = 'mysub'"
    kick-node     /services/wse:actions
    action-name   diffcheck

Now, we create the `mysub` subscription for device `www0` and refer to the
notification stream `interface`. As soon as this definition is committed, the
kicker will start triggering:

    (config)# devices device www0 notifications \
              subscription mysub \
              local-user admin stream interface
    (config-subscription-mysub)# commit dry-run
    (config-subscription-mysub)# commit
    (config-profile-lean)# top
    (config)# exit

If we now inspect the `ncs-java-vm.log`, we will see several notifications that
have been received. We also see that the diff-iterated transaction contains the
notification as data under the path
`/devices/device/notifications/received-notifications/notification/data`. This
is an operational data list. However, this transaction is synthetic and will
not be committed. If the notification will be stored, CDB is optional and does
not depend on the notification kicker functionality:

    # file show logs/ncs-java-vm.log
    ...
    {[669406386|id], n1}
    {[669406386|monitor], /ncs:devices/device{www0}/netconf.../data/linkUp}
    {[669406386|tid], 758}
    path = /ncs:devices/device{www0}
    op = MOP_MODIFIED
    newValue = null
    path = /ncs:devices/device{www0}/netconf...
    op = MOP_CREATED
    newValue = null
    path = /ncs:devices/device{www0}/netconf.../event-time
    op = MOP_VALUE_SET
    newValue = 2017-02-15T16:35:36.039204+00:00
    path = /ncs:devices/device{www0}/netconf.../sequence-no
    op = MOP_VALUE_SET
    newValue = 0
    path = /ncs:devices/device{www0}/netconf.../data/notif:linkUp
    op = MOP_CREATED
    newValue = null
    ...

Removing the kicker and the subscription:

    # config
    (config)# no kickers notification-kicker
    (config)# no devices device www0 notifications subscription
    (config)# commit dry-run
    (config)# commit
    (config)# exit
    # exit

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Kicker
+ The `demo-kicker.sh` script