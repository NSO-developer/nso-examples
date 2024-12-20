Using the NSO RESTCONF API
==========================

This example shows how to work with the RESTCONF API using the
`examples.ncs/service-management/website-service` example.

The API is demonstrated by walking through some queries. In this example, we
use the command line tool `curl` to communicate with NSO.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start the Website Service Example
---------------------------------

Setup the `website-server` example simulated network, build the packages, and
start the website-server example netsim network and NSO:

    make -C ../../service-management/website-service all start

See the `website-server` example README for additional details.

Sending RESTCONF Requests
-------------------------

In this example, we will authenticate as the user `admin` with password
`admin`.

*Note*: RESTCONF requires YANG module name prefixes to resources when they are
first used.

Root Resource Discovery
-----------------------

With RESTCONF, the root part of the URL path is configurable by setting it
in the `ncs.conf` file. A mechanism exists to discover the RESTCONF root by
getting the `/.well-known/host-meta` resource and using the `<Link>` element
containing the `restconf` attribute:

    curl -v -u admin:admin http://localhost:8080/.well-known/host-meta
    *   Trying 127.0.0.1:8080...
    * Connected to localhost (127.0.0.1) port 8080 (#0)
    * Server auth using Basic with user 'admin'
    > GET /.well-known/host-meta HTTP/1.1
    > Host: localhost:8080
    > Authorization: Basic YWRtaW46YWRtaW4=
    > User-Agent: curl/7.82.0
    > Accept: */*
    >
    * Mark bundle as not supporting multiuse
    < HTTP/1.1 200 OK
    < Date: Thu, 28 Apr 2022 11:37:58 GMT
    < Content-Length: 107
    < Content-Type: application/xrd+xml
    < Content-Security-Policy: default-src 'self'; \
    block-all-mixed-content; base-uri 'self'; frame-ancestors 'none';
    < Strict-Transport-Security: max-age=15552000; includeSubDomains
    < X-Content-Type-Options: nosniff
    < X-Frame-Options: DENY
    < X-XSS-Protection: 1; mode=block
    <
    <XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>
      <Link rel='restconf' href='/restconf'/>
    </XRD>

As can be seen from the result above, our RESTCONF root part is `/restconf`,
which also is the default unless configured to be something else.

Top-level GET
-------------

Send the first RESTCONF query to get a representation of the top-level
resource, which is accessible through the path `/restconf`:

    curl -v -u admin:admin http://localhost:8080/restconf
    *   Trying 127.0.0.1:8080...
    * Connected to localhost (127.0.0.1) port 8080 (#0)
    * Server auth using Basic with user 'admin'
    > GET /restconf HTTP/1.1
    > Host: localhost:8080
    > Authorization: Basic YWRtaW46YWRtaW4=
    > User-Agent: curl/7.82.0
    > Accept: */*
    >
    * Mark bundle as not supporting multiuse
    < HTTP/1.1 200 OK
    < Date: Thu, 28 Apr 2022 11:40:22 GMT
    < Cache-Control: private, no-cache, must-revalidate, proxy-revalidate
    < Content-Length: 157
    < Content-Type: application/yang-data+xml
    < Pragma: no-cache
    < Content-Security-Policy: default-src 'self'; \
    block-all-mixed-content; base-uri 'self'; frame-ancestors 'none';
    < Strict-Transport-Security: max-age=15552000; includeSubDomains
    < X-Content-Type-Options: nosniff
    < X-Frame-Options: DENY
    < X-XSS-Protection: 1; mode=block
    <
    <restconf xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
      <data/>
      <operations/>
      <yang-library-version>2019-01-04</yang-library-version>
    </restconf>

As can be seen from the result, the server in this example exposes
two additional resources: `operations` and `yang-library-version`.

*Note*: See the `Content-Type` header. It contains the `media-type` for the
resource.

If you prefer JSON format, add an Accept header, e.g.:

    curl -u admin:admin -H "Accept: application/yang-data+json" \
    http://localhost:8080/restconf/data
    {"restconf":{"data":{},
                "operations":{},
                "yang-library-version":"2019-01-04"}}

GET of Datastore
----------------

We can get the datastore by following the link below:

    curl -s -u admin:admin http://localhost:8080/restconf/data

The RESTCONF API follows the data-model structure. First, the device
configuration is to be synced via the CLI

    > request devices sync-from

Show the `lb0` device config using the CLI:

    > show configuration devices device lb0 config lb:lbConfig

Show device configuration over RESTCONF:

    curl -s -u admin:admin http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig

    <lbConfig xmlns="http://pound/lb"  xmlns:lb="http://pound/lb"
    xmlns:ncs="http://tail-f.com/ns/ncs">
      <system>
        <ntp-server>18.4.5.6</ntp-server>
        <resolver>
          <search>acme.com</search>
          <nameserver>18.4.5.6</nameserver>
        </resolver>
      </system>
    </lbConfig>

GET with Selector
-----------------

We can also get more or less of the data tree:

    curl -s -u admin:admin http://localhost:8080/\
    restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig?depth=2

    <lbConfig xmlns="http://pound/lb" xmlns:lb="http://pound/lb"
    xmlns:ncs="http://tail-f.com/ns/ncs">
      <system>
        <ntp-server>18.4.5.6</ntp-server>
        <resolver/>
      </system>
    </lbConfig>

Delete Parts of the Config
--------------------------

First, we find a resource to delete. For example, the NTP server:

    curl -s -u admin:admin http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/ntp-server

Then save the resource we want to delete to a local file, in this
case, the subnet resource:

    curl -s -u admin:admin http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/ntp-server \
    > saved-ntp-server

Now we can delete the resource:

    curl -v -X DELETE -u admin:admin http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/ntp-server

    < HTTP/1.1 204 No Content

Use the CLI to verify it was deleted:

    > show configuration devices device lb0 config lb:lbConfig
    system {
        ntp-server 18.4.5.6;
        resolver {
            search     acme.com;
            nameserver 18.4.5.6;
        }
    }

Using RESTCONF:

    curl -u admin:admin http://localhost:8080/\
    restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/

    <system xmlns="http://pound/lb"  xmlns:lb="http://pound/lb"
    xmlns:ncs="http://tail-f.com/ns/ncs">
      <resolver>
        <search>acme.com</search>
        <nameserver>18.4.5.6</nameserver>
      </resolver>
    </system>

Create a New Resource
---------------------

Let's re-create the subnet resource we just deleted.

To create a resource, we POST the resource to its parent. Remember, the
`saved-ntp-server` is the file saved above.

    curl -v -X POST -T saved-ntp-server -u admin:admin \
    http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/\
    config/lb:lbConfig/system

    < HTTP/1.1 204 No Content

Verify that the NTP server is back using the CLI:

    > show configuration devices device lb0 config lb:lbConfig
    system {
        ntp-server 18.4.5.6;
        resolver {
            search     acme.com;
            nameserver 18.4.5.6;
        }
    }

RESTCONF:

    curl -u admin:admin http://localhost:8080/\
    restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/

    <lbConfig xmlns="http://pound/lb"  xmlns:lb="http://pound/lb"
    xmlns:ncs="http://tail-f.com/ns/ncs">
      <system>
        <ntp-server>18.4.5.6</ntp-server>
        <resolver>
          <search>acme.com</search>
          <nameserver>18.4.5.6</nameserver>
        </resolver>
      </system>
    </lbConfig>

Modify an Existing Resource
---------------------------

The PATCH method is used to modify an existing resource. Imagine you would
like to modify the search attribute of the resolver. Change it from
`acme.com` to `foo.com`.

Prepare a file `patch-search` with the following content:

    <search>
      foo.com
    </search>

Modify the search leaf:

    curl -v -X PATCH -T patch-search -u admin:admin \
    http://localhost:8080/restconf/data/tailf-ncs:devices/device=lb0/\
    config/lb:lbConfig/system/resolver/search

    < HTTP/1.1 204 No Content

Verify the change in the CLI:

    > show configuration devices device lb0 config lb:lbConfig system \
      resolver
    search     acme.com;
    nameserver 18.4.5.6;

Verify the change in RESTCONF, and note the use of `?fields=search` to select
one leaf, like below:

    curl -u admin:admin  http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/\
    resolver\?fields=search

    <resolver xmlns="http://pound/lb"  xmlns:lb="http://pound/lb"
    xmlns:ncs="http://tail-f.com/ns/ncs">
      <search>acme.com</search>
    </resolver>

Replace an Existing Resource

PUT is used to replace an existing resource. For example, save the resolver
settings to a file:

    curl -u admin:admin http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/\
    resolver > saved-resolver

Modify the values in the file `saved-resolver`.

Apply the modifications by using PUT:

    curl -vs -X PUT -T saved-resolver -u admin:admin http://localhost:8080/\
    restconf/data/tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/\
    resolver

    < HTTP/1.1 204 No Content

Verify the changes

    curl -u admin:admin http://localhost:8080/restconf/data/\
    tailf-ncs:devices/device=lb0/config/lb:lbConfig/system/resolver

Cleanup
-------

Stop all daemons and clean all created files:

    make -C ../../service-management/website-service stop clean

Further Reading
---------------

+ NSO Development Guide: RESTCONF API
+ The `demo.sh` script