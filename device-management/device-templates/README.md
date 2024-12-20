Using Device Templates
======================

This example uses the same simulated network as the `router-network` example.
The simulated network and tools to manipulate it are described in that example.
This example will illustrate using device templates to initialize and
manipulate device configuration data.

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Starting the Simulated Network
------------------------------

The ncs-netsim network:

                    -------
                    | NSO |
                    -------
                        |
                        |
    ----------------------------------------------------------
        |                      |                      |
        |                      |                      |
      -------                -------                -------
      | ex0 |                | ex1 |                | ex2 |
      -------                -------                -------

To start the simulated devices, we need to build them using `make`:

    make all

This builds the packages and creates a netsim simulated network.

Start the "network" of three simulated "router" devices:

    $ ncs-netsim start
    DEVICE ex0 OK STARTED
    DEVICE ex1 OK STARTED
    DEVICE ex2 OK STARTED

Starting NSO
------------

Start NSO with the default configuration:

    ncs

NSO will load the data models, packages, and initialization data but will only
connect to real or simulated devices once instructed.

Login using the NSO Command Line Interface (CLI):

    ncs_cli -u admin

Sync from the devices:

    > request devices sync-from

Using a Template
----------------

As defined by the YANG data model for the simulated router, each has three
servers: DNS, NTP, and Syslog. In the example below, these three servers
will have their configuration data changed using device templates.

This example is divided into four sections to showcase the strength of device
templates. The techniques shown in the sections can also be combined.

* Static templates - The values are just static values set to the leaf values
  to be changed.
* Templates with variables - The values are set on the command line in the CLI
  using variables.
* Templates with expressions - The values are set as a result of an XPath
  expression evaluation.
* Templates combined - This is a combination of the above techniques.

Static Templates
----------------

New values replace the existing DNS, NTP, and Syslog servers by creating a
`servers-static` template and filling it with static data.

Set the IP address of the DNS server:

    ncs_cli -u admin
    > configure
    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys dns server 93.188.0.20

Add a tag so any existing servers are replaced by the below new one:

    % tag add devices template servers-static ned-id router-nc-1.0 \
      config r:sys dns replace

Set up NTP the same way:

    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys ntp server 83.227.219.208
    % tag add devices template servers-static ned-id router-nc-1.0 \
      config r:sys ntp replace
    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys ntp server 83.227.219.208 key 2
    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys ntp controlkey 2 key 2

Set up Syslog:

    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys syslog server 192.168.2.14
    % tag add devices template servers-static ned-id router-nc-1.0 \
      config r:sys syslog replace
    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys syslog server 192.168.2.14 enabled true
    % set devices template servers-static ned-id router-nc-1.0 \
      config r:sys syslog server 192.168.2.14 selector 8 \
      facility [ auth authpriv ]

Display the changes:

    % commit dry-run

Commit changes if they are correct:

    % commit

Change DNS, NTP, and Syslog servers by applying the template to devices `ex0`,
`ex1`, and `ex2`:

    % request devices device ex0..2 apply-template template-name servers-static

Check what the template has done:

    % commit dry-run

Revert changes as we are doing the same changes again but with different
methods:

    % revert no-confirm

Templates with Variables
------------------------

In this example, the data is provided on the command line, where we start by
defining the template. Variables are tokens with the dollar `$` character in
the first position.

The variable is enclosed within braces (curly brackets) `{}`. Strings within
braces will be evaluated. Any combination of constant strings and braces can
be used:

    foo_{$bar}_fuzz_{$nik}

If the variable bar = XXX and nik = YYYY the result will be:

    foo_XXX_fuzz_YYYY

Setup DNS:

    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys dns server {$dns}
    % tag add devices template servers-variables \
      ned-id router-nc-1.0 config r:sys dns replace


Setup NTP:

    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys ntp server {$ntp}
    % tag add devices template servers-variables \
      ned-id router-nc-1.0 config r:sys ntp replace
    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys ntp server {$ntp} key 2
    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys ntp controlkey 2 key 2


Setup Syslog:

    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys syslog server {$syslog}
    % tag add devices template servers-variables \
      ned-id router-nc-1.0 config r:sys syslog replace
    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys syslog server {$syslog} enabled true
    % set devices template servers-variables \
      ned-id router-nc-1.0 config r:sys syslog server {$syslog} selector 8 \
      facility [ auth authpriv ]

Review the template and commit the changes:

    commit dry-run
    commit

Apply the template to the `ex0` device:

    % request devices device ex0 apply-template \
      template-name servers-variables \
      variable { name syslog value '192.168.2.14' } \
      variable { name ntp value '83.227.219.208' } \
      variable { name dns value '93.188.0.20' }

Review the changes:

    % commit dry-run

Revert the changes:

    % revert no-confirm
    % exit
    > exit


Templates with Expressions
--------------------------

The string enclosed within braces is an XPath expression. In the previous
example, we used a straightforward expression of just one variable. In this
example, we will select the value from another node to retrieve its value. The
device `ex0` will be the primary device from where we get the values.

Getting XPath expressions right can be tricky at times. To see what is
selected or as a debug tool, the XPath expression can be evaluated in a
terminal shell by the use of the NSO `ncs_cmd` tool.

Select the address node of the DNS server, i.e., get the server's IP address.

    ncs_cmd -c "x /devices/device[name='ex0']/config/r:sys/dns/server/address"
    /devices/device{ex0}/config/r:sys/dns/server{10.2.3.4}/address [10.2.3.4]

Select all child nodes to the server node:

    ncs_cmd -c "x /devices/device[name='ex1']/config/r:sys/syslog/server/*"
    /devices/device{ex1}/config/r:sys/syslog/server{10.3.4.5}/name [10.3.4.5]
    /devices/device{ex1}/config/r:sys/syslog/server{10.3.4.5}/enabled [true]
    /devices/device{ex1}/config/r:sys/syslog/server{10.3.4.5}/selector{8} []

The `ncs_cmd` tool is useful to understand the XPath selection and to get
the path right.

The NSO C-style CLI can also be used to evaluate XPath expressions:

    ncs_cli -u admin -C
    # devtools true
    # config
    # xpath eval /devices/device[name='ex0']/config/r:sys/dns/server/address
      /devices/device[name='ex0']/config/r:sys/dns/server[address='10.2.3.4']\
      /address :: 10.2.3.4
    # xpath eval /devices/device[name='ex1']/config/r:sys/syslog/server/*
      /devices/device[name='ex1']/config/r:sys/syslog/server[name='10.3.4.5']\
      /name :: 10.3.4.5
      /devices/device[name='ex1']/config/r:sys/syslog/server[name='10.3.4.5']\
      /enabled :: true
      /devices/device[name='ex1']/config/r:sys/syslog/server[name='10.3.4.5']\
      /selector[name='8']
    # exit
    # exit

The NSO XPath trace is a valuable tool for understanding the XPath expression
when the template is applied. XPath trace is enabled for the examples from
`ncs.conf`.

In a second terminal window, issue the command:

    tail -f logs/xpath.trace

Keep an eye on the output when the template is applied.

Start by adding some new values for device `ex0` using the previous template:

    ncs_cli -u admin
    configure
    % request devices device ex0 apply-template \
      template-name servers-variables \
      variable { name syslog value '192.168.2.14' } \
      variable { name ntp value '83.227.219.208' } \
      variable { name dns value '93.188.0.20' }
    % commit dry-run
    % commit

Define a template using `ex0` as the primary for DNS, NTP, and Syslog. The
template uses XPath expressions to select the nodes to copy values.

Setup DNS:

    % set devices template servers-expr ned-id router-nc-1.0 config r:sys dns \
      server {/devices/device[name='ex0']/config/r:sys/dns/server/address}
    % tag add devices template servers-expr \
      ned-id router-nc-1.0 config r:sys dns replace

Setup NTP:

    % set devices template servers-expr ned-id router-nc-1.0 config r:sys ntp \
    server {/devices/device[name='ex0']/config/r:sys/ntp/server/name}
    % tag add devices template servers-expr \
      ned-id router-nc-1.0 config r:sys ntp replace
    % set devices template servers-expr ned-id router-nc-1.0 config r:sys ntp \
      server {/devices/device[name='ex0']/config/r:sys/ntp/server/name} \
      key {key}
    % set devices template servers-expr ned-id router-nc-1.0 config r:sys ntp \
      key {/devices/device[name='ex0']/config/r:sys/ntp/key/name} \
      trusted {trusted}
    % set devices template servers-expr ned-id router-nc-1.0 config r:sys ntp \
      controlkey {/devices/device[name='ex0']/config/r:sys/ntp/controlkey}

Setup syslog:

    % set devices template servers-expr ned-id router-nc-1.0 config r:sys \
      syslog server \
      {/devices/device[name='ex0']/config/r:sys/syslog/server/name}
    % tag add devices template servers-expr \
      ned-id router-nc-1.0 config r:sys syslog replace
    % set devices template servers-expr ned-id router-nc-1.0 config r:sys \
      syslog server \
      {/devices/device[name='ex0']/config/r:sys/syslog/server/name} \
      enabled true
    % set devices template servers-expr ned-id router-nc-1.0 config r:sys \
      syslog server \
      {/devices/device[name='ex0']/config/r:sys/syslog/server/name} \
      selector {selector/name} facility [ {facility} security ]

Preview the defined template:

    % commit dry-run

Note the difference between absolute and relative paths in the above
XPath expressions.

When a list element is created, the current context is changed to the context
of the key for that list element. See the printout from the `commit dry-run`.

Take a look at the Syslog server list element with its key:

    {/devices/device[name='ex0']/config/r:sys/syslog/server/name}

The XPath expression is evaluated to a `node-set`. A list element is created
for every node in the `node-set`.

For every created server element, the XPath context is changed to:

    /devices/device[name='ex0']/config/r:sys/syslog/server[name=KEY]

`KEY` takes the value of each node in the `node-set`.

The expression `{selector/name}` is evaluated in this context, and therefore
the relative path. When the selector list element is created, the context is
changed again, and `{facility}` is evaluated in this new context.

Review the Changes
------------------

    % commit

Use the template to setup `ex1` and `ex2` with values from `ex0`:

    % request devices device ex1..2 apply-template template-name servers-expr

Review the changes:

    % commit dry-run

Revert the changes:

    % revert no-confirm

Templates Combined
------------------

The above techniques can be combined to receive the desired result. This
example shows the combined usage of variables and selections. We use a variable
to select which device to copy the relevant data from.

Setup DNS:

    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys dns \
      server {/devices/device[name=$primary]/config/r:sys/dns/server/address}
    % tag add devices template servers-combined ned-id router-nc-1.0 \
      config r:sys dns replace

Setup NTP:

    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys ntp \
      server {/devices/device[name=$primary]/config/r:sys/ntp/server/name}
    % tag add devices template servers-combined \
      ned-id router-nc-1.0 config r:sys ntp replace
    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys ntp \
      server {/devices/device[name=$primary]/config/r:sys/ntp/server/name} \
      key {key}
    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys ntp \
      key {/devices/device[name=$primary]/config/r:sys/ntp/key/name} \
      trusted {trusted}
    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys ntp \
      controlkey {/devices/device[name=$primary]/config/r:sys/ntp/controlkey}

Setup Syslog:

    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys syslog \
      server {/devices/device[name=$primary]/config/r:sys/syslog/server/name}
    % tag add devices template servers-combined \
      ned-id router-nc-1.0 config r:sys syslog replace
    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys syslog \
      server {/devices/device[name=$primary]/config/r:sys/syslog/server/name} \
      enabled true
    % set devices template servers-combined ned-id router-nc-1.0 \
      config r:sys syslog \
      server {/devices/device[name=$primary]/config/r:sys/syslog/server/name} \
      selector {selector/name} facility [ security {facility} ]
    % commit dry-run
    % commit

Apply the template to `ex1` and `ex2` and use `ex0` as the primary:

    % request devices device ex1..2 apply-template template-name \
      servers-combined variable { name primary value 'ex0' }

Commit the changes:

    % commit dry-run
    % commit
    % exit
    > exit

See the second terminal window running the `tail` command or:

    cat logs/xpath.trace

RESTCONF
--------

The above examples can be requested over the RESTCONF interface instead of the
`apply-template` action. Using the `curl` tool and the same names as
above:

Apply the `servers-static` template:

    curl -s -X POST -T RESTCONF/servers-static.xml \
    admin:admin@localhost:8080/restconf/data/\
    tailf-ncs:devices/device=ex0/apply-template

Apply the `servers-variables` template:

    curl -s -X POST -T RESTCONF/servers-variables.xml \
    admin:admin@localhost:8080/restconf/data/\
    tailf-ncs:devices/device=ex0/apply-template

Apply the `servers-expr` template:

    curl -s -X POST -T RESTCONF/servers-expr.xml \
    admin:admin@localhost:8080/restconf/data/\
    tailf-ncs:devices/device=ex0/apply-template

Apply the `servers-combined` template:

    curl -s -X POST -T RESTCONF/servers-combined.xml \
    admin:admin@localhost:8080/restconf/data/\
    tailf-ncs:devices/device=ex0/apply-template

NETCONF
-------

The above examples can be requested over the NETCONF interface instead of the
`apply-template` action. Using the NSO `netconf-console` tool and the same
names as above:

Apply the `servers-static` template:

    netconf-console --rpc=NETCONF/nc-servers-static.xml

Apply the `servers-variables` template:

    netconf-console --rpc=NETCONF/nc-servers-variables.xml

Apply the `servers-expr` template:

    netconf-console --rpc=NETCONF/nc-servers-expr-ex1.xml

Apply the `servers-combined` template:

    netconf-console --rpc=NETCONF/nc-servers-combined-ex2.xml

Cleanup
-------

When you finish this example, make sure all daemons are stopped. Stop NSO and
the simulated network:

    ncs --stop
    ncs-netsim stop

Clean all created files:

    make clean

Further Reading
---------------

+ NSO Operation & Usage: NSO Device Manager
+ The `demo.sh` script
