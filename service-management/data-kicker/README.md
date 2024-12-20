Reactive FASTMAP with a Data Kicker
===================================

*Note*: This example shows a legacy way of implementing a reactive FASTMAP
application that has since evolved into reactive FASTMAP nano services. See
`examples.ncs/nano-services` for nano services using reactive FASTMAP examples.

This example illustrates how to write a reactive FASTMAP application using a
data kicker help construct.

The idea behind the kicker is that service code can create a kicker
that re-deploys a service when something happens.

Kickers
-------

A kicker consists of these fields:

* A `monitor` is what we refer to as a `node-instance-identifier` pointing
  towards either configuration or operational data. A
  `node-instance-identifier` is like an `instance-identifier` except that the
  keys may be omitted. The monitor's purpose is to identify what part of CDB to
  watch for changes, and when that part changes, the data kicker triggers.

  For example, given a data model:

      container interfaces {
        list interface {
          key name;
          leaf name {
            type string;
          }
          leaf oper-state {
            config false;
            ....

      Trigger if the interface 'eth0' is changed:
      monitor: /interfaces/interface[name='eth0']

      Trigger if the oper-state of interface 'eth0' is changed:
      monitor: /interfaces/interface[name='eth0']/oper-state

      Trigger if any interface is changed:
      monitor: /interfaces/interface


* An optional `trigger-expr` is an XPath 1.0 expression evaluated when any node
  matching `monitor` changes. The `trigger-type` is used to determine if the
  kicker triggers or not.

  If no trigger-expr has been defined, the kicker is always triggered when any
  node matching `monitor` changes.

  This XPath is evaluated with the node that matched `monitor` as the context
  node.

  Trigger if the oper-state of interface `eth0` is set to `up`:

      monitor: /interfaces/interface[name='eth0']
      trigger-expr: oper-state = 'up'

  Or:

      monitor: /interfaces/interface[name='eth0']/oper-state
      trigger-expr: . = 'up'


* An optional `trigger-type` controls whether the kicker should trigger when
  the `trigger-expr` changes from or when it just changes to true.

* A list of variable bindings used when evaluating the XPath in the
  `trigger-expr`.

* A `kick-node`. When the kicker is triggered, the `action-name` action is
  invoked on the `kick-node` instance.

  If the `kick-node` is given as an XPath 1.0 expression, the expression is
  evaluated with the node that matched `monitor` as the context node, and the
  expression must return a node-set. The `action-name` action is invoked on the
  nodes in this node-set.

  For example, suppose a service `/bar` creates an entry in `/bar-data`, with
  the same ID as `/bar`, and the service needs to be re-deployed with the
  `bar-data` state changes.

     list bar-data {
       key id;
       leaf id { type string; }
       leaf state { type ... }
     }

  Then a single kicker with:

      monitor: '/bar-data/state'
      kick-node: '/bar[name=current()/../id]'
      action-name: 'reactive-re-deploy'

  can be created.

  Alternatively, every service instance can create its own kicker
  with:

      monitor: '/bar-data[name=<id>]/state'
      kick-node: '/bar[name=<id>]
      action-name: 'reactive-re-deploy'

* An `action-name`.

Kickers are defined in the `tailf-kicker.yang` file, which is part of
the distribution.

Use cases
---------

Kickers are almost always used as an implementation technique for Reactive
FASTMAP services.

Assume an NFV/ESC-based application that:

1. Ask ESC to start a VM.
2. Once the VM is ready, wants to configure the VM.

Such an application would create a kicker with a monitor for
`/devices/device[name=<vmname>]/ready`

Once the VM is `ready`, the service will be re-deployed, and it can continue
its Reactive FASTMAP execution further and provide configuration to the newly
started VM.

Before the kickers, it was common to use CDB subscriber code that:

1. Subscribed to some change.
2. Read that change and then re-deployed some service that the CDB subscriber
   code knew was waiting for that change.

Now, with kickers, we can simply such code by having a CDB subscriber
that simply:

1. Subscribes to some change, for example, a NETCONF notification listener.
2. Writes some operational data field somewhere.

The RFM service code is then responsible for setting up a kicker with the
monitor pointing to that field written by the CDB subscriber. Thus, the CDB
subscriber code is effectively decoupled from the RFM service code,
making them independent.

Another advantage is that the error handling code, when the re-deploy fails,
is unified inside NSO kicker implementation.

Running the Example
-------------------

The example uses two NSO packages: the `router` NED package and a service
package called '`ppp-accounting`, described in this section.

The example is a bit contrived, but since we want to exemplify the usage of
kickers, it's simplified and artificial.

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make all

Start the simulated network and NSO:

    make start

This will start NSO, and NSO will load the two packages, load the data models
defined by the two packages, and start the Java code defined by the packages.

The service data model:

    list ppp-accounting {

      uses ncs:service-data;
      ncs:servicepoint kickerspnt;

      key "interface";
      leaf interface {
        type string;
      }

    }

    list ppp-accounting-data {
      description "This is helper data, created by the service code for
                  /ppp-accounting";

      key "interface";
      leaf interface {
        type string;
      }
      leaf accounting {
        description "populated externally";
        type string;
      }
    }

The purpose of the service `/ppp-accounting` is to set the accounting field in
the provided PPP interface on all routers in our example network. The catch
here is that the name of the `accounting` field is not provided as an input
parameter to the service. Instead, it is populated externally, read, and used
by the service code.

The FASTMAP code tries to read the field
`/ppp-accounting-data[interface=<if>]/accounting`, and if it doesn't exist, the
code creates a kicker for that field and returns. If the `accounting` field
exists, it used, and data is written into the `/devices` tree for our routers.

To run the above:

    ncs_cli -u admin
    > request devices sync-from
    > configure
    % set ppp-accounting ppp0
    % commit dry-run
    % commit
    % request ppp-accounting ppp0 get-modifications
    cli {
        local-node {
            data
        }
    }
    % exit

We created a service instance and verified that it didn't do anything. Looking
at the service create() code in
`packages/ppp-accounting/src/java/src/com/example/kicker/KickerServiceRFS.java`,
we can see that the code created a kicker.

Let's take a look at that:

    > show configuration kickers
    ----------------^
    syntax error: element does not exist

The kicker data is hidden, and we cannot directly view it in the CLI. The
`tailf-kickers.yang` file says:

    container kickers {
      tailf:info "Kicker specific configuration";
      tailf:hidden debug;

      list data-kicker {
        key id;
        ...

To view the kicker's data, we must:

1. Provide an entry in the ncs.conf file:

       <hide-group>
         <name>debug</name>
       </hide-group>

2. Unhide in the CLI:

       > unhide debug
       > configure
       % show kickers
       data-kicker ncs-internal-side-effects {
           monitor     /ncs:side-effect-queue;
           kick-node   /ncs:side-effect-queue;
           action-name invoke;
       }
       data-kicker ppp-accounting-ppp0 {
           monitor     /ppp-accounting-data[interface='ppp0']/accounting;
           kick-node   /ppp-accounting[interface='ppp0'];
           action-name reactive-re-deploy;
       }

There, we can see our newly created kicker.

To trigger this kicker, which will then execute the re-deploy on the
`/ppp-accounting[interface='ppp0']` service, we need to assign some
data to the field that the kicker monitors:

    % set ppp-accounting-data ppp0 accounting radius
    % commit dry-run
    % commit

After the kicker triggered (no longer show under `kickers`):

    % request ppp-accounting ppp0 get-modifications
    cli {
        local-node {
            data  devices {
                      device ex0 {
                          config {
                              r:sys {
                                  interfaces {
                                      serial ppp0 {
                                          ppp {
                  -                            accounting acme;
                  +                            accounting radius;
                                          }
                                      }
                                  }
                              }
                          }
                      }
                      device ex1 {
                          config {
                              r:sys {
                                  interfaces {
                                      serial ppp0 {
                                          ppp {
                  -                            accounting acme;
                  +                            accounting radius;
                ...

Debugging kickers can be done by providing a `debug kicker` pipe option to
commit:

    % commit | debug kicker
    kicker: ppp-accounting-ppp0 at
    /ppp-accounting-data[interface='ppp0']/accounting changed,
    invoking 'reactive-re-deploy'

Another valuable tool when debugging kickers is the `devel.log`, which will
contain entries when we, for example, provide bad monitors or bad XPath
expressions in the instantiated kickers.

Final Reactive FASTMAP (RFM) Note
---------------------------------

Looking at the RFM java code, we see that a so-called `PRE_MODIFICATION` hook
creates the `/up-accounting-data` help entry.  This is a common trick in RFM
applications. We don't want that data to be part of the FASTMAP diffset.
Usually, the help entry is also used to contain various `config false` fields
of the service instance. If that data were part of FASTMAP diffset, the data
would disappear with every re-deploy turn, thus we use the `PRE_MODIFICATION`
trick.

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Kicker
+ NSO Development Guide: Nano Services
+ The `demo.sh` script