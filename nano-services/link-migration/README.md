Link Migration Nano Service
===========================

This example illustrates how to design a Reactive FASTMAP service using nano
Services.

To declare a nano service, three things are necessary in the service YANG
model:

1. The service must include the grouping:

        uses ncs:nano-plan-data;

   This is an augmented variant of the `ncs:plan-data` extended to make the
   plan executable. The grouping `ncs:plan-data` cannot be used for a nano
   service.

2. A plan definition must be declared with an `ncs:plan-outline` statement in
   the YANG module.

3. A service behavior tree must be declared with an `ncs:service-behavior-tree`
   statement in the YANG module.

The `ncs:plan-outline` statement creates a plan definition with a given name
and describes the possible component-types and their states that the service
plan instance will use.  Note that an instantiated service can have a plan with
zero, one, or many different components based on the same component type.

It is the responsibility of the `ncs:service-behavior-tree` to create the plan
components that are included in the instantiated service plan at all times. For
this, the `ncs:service-behavior-tree` consists of a tree of rules for when a
component is added or removed from the service instance plan.

The `link` package defines a link nano service setting up a VPN link. The
service has a list containing at most one element that constitutes the VPN link
and is keyed on `a-device`, `a-interface`, `b-device`, and `b-interface`. The
`list` element corresponds to the plan's component type `link:vlan-link`.

Plan Definition
---------------

The following is the nano service plan definition:

    identity vlan-link {
      base ncs:plan-component-type;
    }

    identity dev-setup {
      base ncs:plan-state;
    }

    ncs:plan-outline link-plan {
      description
        "Make before brake vlan plan";

      ncs:component-type "link:vlan-link" {
        ncs:state "ncs:init";
        ncs:state "link:dev-setup" {
          ncs:create {
            ncs:nano-callback;
          }
        }
        ncs:state "ncs:ready" {
          ncs:create {
            ncs:monitor "$SERVICE/endpoints" {
              ncs:trigger-expr "test-passed = 'true'";
            }
          }
          ncs:delete {
            ncs:pre-condition {
              ncs:monitor "$SERVICE/plan" {
                ncs:trigger-expr
                  "component[type = 'link:vlan-link'][back-track = 'false']"
                + "/state[name = 'ncs:ready'][status = 'reached']"
                + " or not(component[back-track = 'false'])";
              }
            }
          }
        }
      }
    }

To be noted in the plan definition is the following:

* There is only one nano service callback registered for the service. This is
  on the `link:dev-setup` state in the `link:vlan-link` component type. This is
  represented in the plan as:

      ncs:state "link:dev-setup" {
        ncs:create {
          ncs:nano-callback;
        }
      }

* The callback is a template file. See
  `packages/link/templates/link-template.xml`.

* There are both a `create` and a `delete` pre-condition for the state
  `ncs:ready` in the `link:vlan-link` component type.

  - The `create` pre-condition:

        ncs:create {
          ncs:monitor "{$SERVICE}/endpoints" {
            ncs:trigger-expr "current()[test-passed = 'true']";
          }
        }

    Implies that the components based on this component type will not be
    regarded as finished until the `test-passed` leaf is set to `true`. This
    will illustrate the scenario that, after the initial setup of a link is
    configured by the `link:dev-setup` state, a manual test and setting of the
    `test-passed` leaf is necessary before the link is regarded as finished.

  - The `delete` pre-condition:

        ncs:delete {
          ncs:pre-condition {
            ncs:monitor "$SERVICE/plan" {
              ncs:trigger-expr
                "component[type = 'link:vlan-link'][back-track = 'false']"
              + "/state[name = 'ncs:ready'][status = 'reached']"
              + " or not(component[back-track = 'false'])";
            }
          }
        }

    Implies that before starting to delete, backtracking, an old component,
    all new components must have reached the `ncs:ready` state, i.e., being
    tested. This illustrates a create-before-break scenario where the new link
    is created first, and only when this is set up is the old link removed.

Behavior Tree Definition
------------------------

The `ncs:service-behavior-tree` for the example is the following:

    ncs:service-behavior-tree link-servicepoint {
      description
        "Make before brake vlan example";

      ncs:plan-outline-ref link-plan;

      ncs:selector {

        ncs:multiplier {
          ncs:foreach "endpoints" {
            ncs:variable "LINKNAME" {
              ncs:value-expr "concat(a-device, '-',
                                    a-interface, '-',
                                    b-device, '-',
                                    b-interface)";
            }
          }
          ncs:create-component "$LINKNAME" {
            ncs:component-type-ref "link:vlan-link";
          }
        }
      }
      ...
    }

Here, the `ncs:service-behavior-tree` is registered on the servicepoint
`link-servicepoint` defined by the nano service. It refers to the plan
definition named `link:link-plan`.

The behavior tree has a selector on top that chooses to synthesize its
children depending on their pre-conditions. This tree has no pre-conditions, so
that all children will be synthesized.

A `self` component is added automatically to the behavior tree. It shows when
all components have reached their `init` and `ready` states.

The `multiplier` control node that selects a node-set creates a variable
named `VALUE` with a unique value for each node in that node-set and creates a
component of type `link:vlan-link` for each node in the selected node-set. The
name for each individual component is the value of the variable `VALUE`.

Since the selected node-set is the `endpoints` list that can contain at most
one element, this will produce only one component. However, if the link in the
service is changed, i.e., the old list entry is deleted and a new one is
created, then the multiplier will create a component with a new name.

This will force the old component, which is no longer synthesized, to be
back-tracked, and the plan definition above will handle the
"create-before-break" behavior of this back-tracking.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script.

Build the packages:

    make all

Start the `ncs-netsim` network:

    ncs-netsim start

Start NSO:

    ncs

Sync the configuration from the devices:

    ncs_cli --user=admin -C
    # request devices sync-from

Create a service instance that sets up a VPN link between devices `ex1` and
`ex2`, and complete it immediately since the `test-passed` leaf is set to
`true`.

    # config
    (config)# link t2 unit 17 vlan-id 1
    (config-link-t2)# link t2 endpoints ex1 eth0 ex2 eth0 test-passed true
    (config-endpoints-ex1/eth0/ex2/eth0)# commit dry-run
    (config-endpoints-ex1/eth0/ex2/eth0)# commit
    (config-endpoints-ex1/eth0/ex2/eth0)# top
    (config)# exit

Review the result of the commit:

    # link t2 get-modifications
    cli  devices {
              device ex1 {
                  config {
                      r:sys {
                          interfaces {
                              interface eth0 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
                              }
                          }
                      }
                  }
              }
              device ex2 {
                  config {
                      r:sys {
                          interfaces {
                              interface eth0 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
                              }
                          }
                      }
                  }
              }
          }

The service set up the link between the devices. Inspect the plan:

    # show link t2 plan component * state * status

    NAME               STATE      STATUS
    ---------------------------------------
    self               init       reached
                       ready      reached
    ex1-eth0-ex2-eth0  init       reached
                       dev-setup  reached
                       ready      reached

All components in the plan have reached their `ready` state.

Change the link by changing the interface on one of the devices. To do this, we
must remove the old list entry in `endpoints` and create a new one:

    # config
    (config)# no link t2 endpoints ex1 eth0 ex2 eth0
    (config)# link t2 endpoints ex1 eth0 ex2 eth1

Commit dry-run to review what will happen:

    (config-endpoints-ex1/eth0/ex2/eth1)# commit dry-run
    cli  devices {
            device ex1 {
                config {
                    r:sys {
                        interfaces {
                            interface eth0 {
                            }
                        }
                    }
                }
            }
            device ex2 {
                config {
                    r:sys {
                        interfaces {
        +                    interface eth1 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
        +                    }
                        }
                    }
                }
            }
        }
        link t2 {
        -    endpoints ex1 eth0 ex2 eth0 {
        -        test-passed true;
        -    }
        +    endpoints ex1 eth0 ex2 eth1 {
        +    }
        }

The service will at commit just add the new interface at this point and not
remove anything. This is because the `test-passed` leaf will not be set to
`true` for the new component. We commit this change and inspect the plan:

    (config-endpoints-ex1/eth0/ex2/eth1)# commit
    (config-endpoints-ex1/eth0/ex2/eth1)# top
    (config)# exit
    # show link t2 plan
                                  BACK                                 ...
    NAME               TYPE       TRACK  GOAL  STATE      STATUS       ...
    -------------------------------------------------------------------...
    self               self       false  -     init       reached      ...
                                               ready      reached      ...
    ex1-eth0-ex2-eth1  vlan-link  false  -     init       reached      ...
                                               dev-setup  reached      ...
                                               ready      not-reached  ...
    ex1-eth0-ex2-eth0  vlan-link  true   -     init       reached      ...
                                               dev-setup  reached      ...
                                               ready      reached      ...

The new component `ex1-eth0-ex2-eth1` has not yet reached its ready state.
Therefore, the old component `ex1-eth0-ex2-eth0` still exists in backtrack
mode, but we are still waiting for the new component to be finished.

If we check what the service has configured at this point, we get the
following:

    # link t2 get-modifications
    cli  devices {
              device ex1 {
                  config {
                      r:sys {
                          interfaces {
                              interface eth0 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
                              }
                          }
                      }
                  }
              }
              device ex2 {
                  config {
                      r:sys {
                          interfaces {
                              interface eth0 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
                              }
        +                    interface eth1 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
        +                    }
                          }
                      }
                  }
              }
          }

So, both the old and the new links exist at this point. We set the
`test-passed` leaf to `true` to force the new component to reach its `ready`
state:

    (config)# link t2 endpoints ex1 eth0 ex2 eth1 test-passed true
    (config-endpoints-ex1/eth0/ex2/eth1)# commit

If we now check the service plan, we see the following:

    (config-endpoints-ex1/eth0/ex2/eth1)# top
    (config)# exit
    # show link t2 plan
                                  BACK                             ...
    NAME               TYPE       TRACK  GOAL  STATE      STATUS   ...
    ---------------------------------------------------------------...
    self               self       false  -     init       reached  ...
                                               ready      reached  ...
    ex1-eth0-ex2-eth1  vlan-link  false  -     init       reached  ...
                                               dev-setup  reached  ...
                                               ready      reached  ...

The old component has been completely backtracked and is removed because the
new component is finished. We can also check the service modifications and see
the resulting configuration changes from when the old link endpoint is removed:

    # link t2 get-modifications
    cli  devices {
              device ex1 {
                  config {
                      r:sys {
                          interfaces {
                              interface eth0 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
                              }
                          }
                      }
                  }
              }
              device ex2 {
                  config {
                      r:sys {
                          interfaces {
        +                    interface eth1 {
        +                        unit 17 {
        +                            vlan-id 1;
        +                        }
        +                    }
                          }
                      }
                  }
              }
          }

Cleanup
-------

Stop all daemons and clean all created files:

    ncs-netsim stop
    ncs --stop
    make clean

Further Reading
---------------

+ NSO Development Guide: Nano Services
+ The `demo.sh` script