Service Progress Monitoring
===========================

*Note*: This example shows a legacy way of implementing a reactive FASTMAP
application that has since evolved into reactive FASTMAP nano services. See
`examples.ncs/nano-services` for nano services using reactive FASTMAP examples.

This example illustrates how:

- Service Progress Monitoring (SPM) interacts with plans and services.
- To write Python user code that invokes SPM actions depending on plan
  progression.

The example uses two NSO packages. The `router` package and a package called
`myserv`. The `myserv` package is a Python example implementing an example
plan.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean, i.e., no old configuration data is present:

    make stop clean

Setup the simulated network and build the packages:

    make all

All the code for this example resides under `./packages/myserv`. A log of the
Python VM is available in logs/ncs-python-vm-myserv.log

There will be one log file for each Python VM that has started. In this
example, only one VM has been started.

To study this example, first, take a look at the
`packages/myserv/package-meta-data.xml` file, which defines one component:

    <component>
      <name>myserv</name>
      <application>
        <python-class-name>myserv.Main</python-class-name>
      </application>
    </component>

Further, see the YANG file in the `myserv` package
`./packages/myserv/src/yang/myserv.yang`, where the service and action points
are defined. Specifically, for using SPM with timeout support, two `uses`
constructs are needed:

    ...
    uses ncs:service-progress-monitoring-data;
    ...
    container timeout-test {
        uses ncs:service-progress-monitoring-trigger-action {
          refine timeout {
            tailf:actionpoint myserv-timeout-point;
          }
        }
      }

Reviewing the corresponding Python code `packages/myserv/python/myserv.py`,
we find that `myserv.Main` registers a service and two actions, one
corresponding to the `myserv-timeout-point` actionpoint.

    class Main(ncs.application.Application):
        def setup(self):
            self.log.info('--- myserv.Main setup')
            self.register_service('myserv-servicepoint', MyService)
            self.register_action('myserv-self-test', SelfTest)
            self.register_action('myserv-timeout-point', TimeoutHandler)

In `myserv.py`, you can see that the `TimeoutHandler` logs the service and the
type of SPM timeout that occurred: `jeopardy`, `violation`, or `success`.
Logging is done in the abovementioned file `logs/ncs-python-vm-myserv.log`.

A policy and a trigger are needed to put an SPM on a service. The policy
configures a `jeopardy` and a `violation` timeout, what conditions to put on
the plan, and a `timeout` action if you want one. For this example, we will use
the `service-ready` policy defined in `self_policy_plus_action.xml`.

    <config xmlns="http://tail-f.com/ns/config/1.0">
      <service-progress-monitoring xmlns="http://tail-f.com/ns/ncs">
        <policy>
          <name>service-ready</name>
          <jeopardy-timeout>600</jeopardy-timeout>
          <violation-timeout>1200</violation-timeout>
          <condition>
            <name>self-ready</name>
            <component-type>
              <!-- The component name is specified in self_trigger.xml -->
              <type>component-name</type>
              <what>at-least-one</what>
              <plan-state>ready</plan-state>
              <status>reached</status>
            </component-type>
          </condition>
          <action>
            <action-path xmlns:myserv="http://com/tailf/examples/myserv">
              /myserv:timeout-test/myserv:timeout
            </action-path>
            <always-call>true</always-call>
          </action>
        </policy>
      </service-progress-monitoring>
    </config>

You can find a trigger tying the `service-ready` policy to the `myserv` service
instance and the `self` component in `self_trigger.xml`.

    <config xmlns="http://tail-f.com/ns/config/1.0">
      <service-progress-monitoring xmlns="http://tail-f.com/ns/ncs">
        <trigger>
          <name>self</name>
          <policy>service-ready</policy>
          <target xmlns:myserv="http://com/tailf/examples/myserv">
            /myserv:myserv[myserv:name='m1']
          </target>
          <component>self</component>
        </trigger>
      </service-progress-monitoring>
    </config>

I.e., all together, we specify that the service plan `self` component needs to
reach the `ready` state within:

- 600 seconds to avoid putting the service instance SPM in jeopardy.
- 1200 seconds to avoid considering the service instance SPM violated.

Start the simulated network and NSO:

    make start

This will start NSO and load the two packages, load the
data models defined by the two packages, and start the Python code implemented
by the `myserv` package.

Sync the configuration from the devices:

    ncs_cli -u admin
    > request devices sync-from

Create a service instance:

    % configure
    % set myserv m1 dummy 1.1.1.1
    % commit dry-run
    % commit

The Python `myserv` package creates a plan for the service:

    % run show myserv m1 plan
    NAME     TYPE    STATE               STATUS       WHEN
    ---------------------------------------------------------------------
    self     self    init                reached      2018-05-08T11:03:34
                     ready               not-reached  -
    router   router  init                reached      2018-05-08T11:03:34
                     syslog-initialized  not-reached  -
                     ntp-initialized     not-reached  -
                     dns-initialized     not-reached  -
                     ready               not-reached  -
    router2  router  init                reached      2018-05-08T11:03:34
                     ready               not-reached  -

The `init` states for all three components have been reached. Next, Load the
policy:

    % load merge self_policy_plus_action.xml

Load the trigger:

    % load merge self_trigger.xml
    % commit dry-run
    % commit

The SPM will be fulfilled when the `self`'s `ready` state has been reached:

    % run show myserv m1 service-progress-monitoring
    service-progress-monitoring trigger-status self
     policy         service-ready
     start-time     2018-05-08T13:14:18
     jeopardy-time  2018-05-08T13:24:18
     violation-time 2018-05-08T13:34:18
     status         running

As we can see, we are not there yet, and some initialization is needed:

    % request myserv m1 self-test syslog true
    % request myserv m1 self-test ntp true
    % request myserv m1 self-test dns true
    % run show myserv m1 plan
    NAME     TYPE    STATE               STATUS       WHEN
    ---------------------------------------------------------------------
    self     self    init                reached      2018-05-08T11:03:34
                     ready               not-reached  -
    router   router  init                reached      2018-05-08T11:03:34
                     syslog-initialized  reached      2018-05-08T11:18:08
                     ntp-initialized     reached      2018-05-08T11:18:19
                     dns-initialized     reached      2018-05-08T11:18:27
                     ready               reached      2018-05-08T11:18:27
    router2  router  init                reached      2018-05-08T11:03:34
                     ready               not-reached  -

    % set myserv m1 router2ready true
    % commit dry-run
    % commit

    % run show myserv m1 plan
    NAME     TYPE    STATE               STATUS   WHEN
    -----------------------------------------------------------------
    self     self    init                reached  2018-05-08T11:03:34
                     ready               reached  2018-05-08T11:19:10
    router   router  init                reached  2018-05-08T11:03:34
                     syslog-initialized  reached  2018-05-08T11:18:08
                     ntp-initialized     reached  2018-05-08T11:18:19
                     dns-initialized     reached  2018-05-08T11:18:27
                     ready               reached  2018-05-08T11:18:27
    router2  router  init                reached  2018-05-08T11:03:34
                     ready               reached  2018-05-08T11:19:10

    % run show myserv m1 service-progress-monitoring
    service-progress-monitoring trigger-status self
     policy           service-ready
     start-time       2018-05-08T13:14:18
     jeopardy-time    2018-05-08T13:24:18
     jeopardy-result  passed
     violation-time   2018-05-08T13:34:18
     violation-result passed
     status           successful
     success-time     2018-05-08T13:19:10

We completed the plan ~5 minutes before our SPM would be put in `jeopardy` and
~15 minutes before it was `violated`.

You can also find a log entry in `logs/ncs-python-vm-myserv.log` from the
`timeout` action indicating success from an SPM point of view, as we succeeded
in completing the plan before any timeout triggered:

    ... timeout(service=/myserv:myserv[myserv:name='m1'], result=success)

Try rerunning the example, first editing `self_policy_plus_action.xml`,
changing `violation` and `jeopardy` timeouts to lower values, and restarting.

Cleanup
-------

To have NSO re-initialized from the ncs-cdb/*.xml files when restarted:

    ncs --stop
    ncs-setup --reset
    ncs

To reset and restart the netsim network:

    ncs-netsim stop
    ncs-netsim reset
    ncs-netsim start

Or:

    ncs-netsim restart

To clean all created files after stopping NSO and the simulated devices:

    make clean

Further Reading
---------------

+ NSO Development Guide: PlanComponent
+ NSO Development Guide: Nano Services
+ The `demo.sh` script
