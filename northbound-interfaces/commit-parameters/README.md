Using NSO Commit Parameters from Four Northbound Interfaces
===========================================================

This example shows how the shared `tailf-ncs-commit-params.yang` model is
exposed through the NSO CLI, RESTCONF, NETCONF, and JSON-RPC.

The example demonstrates:

* CLI commit parameters such as `label`, `comment`, and `dry-run`
* The RESTCONF `params` query parameter, which carries URL-encoded base64 JSON
* The `X-Cisco-NSO-Commit-Params` header, which carries the same base64 JSON
* NETCONF commit parameters on the standard RFC 6241 `edit-config` operation
* JSON-RPC commit parameters through the structured `params` object
* Separate CLI, RESTCONF, NETCONF, and JSON-RPC demo scripts. The RESTCONF and
  JSON-RPC variants use Python `requests`
* A self-contained `enterprise-dns` service package that configures the
  `router-nc-1.1` simulated routers from a local `nso-run` directory

The example is self-contained. It builds a small `enterprise-dns` service
package, creates an `nso-run` directory with `ncs-setup`, starts
`router-nc-1.1` netsim devices, synchronizes them into NSO, and then applies
commit parameters while changing the service configuration through each
northbound interface.

Running the Example
-------------------

To run the CLI demo:

    make demo

To run the RESTCONF demo:

    make demo-rc

To run the NETCONF demo:

    make demo-nc

To run the JSON-RPC demo:

    make demo-jrpc

The CLI walkthrough is implemented in `demo.sh`, the RESTCONF walkthrough in
`demo_rc.py`, the NETCONF walkthrough in `demo_nc.py`, and the JSON-RPC
walkthrough in `demo_jrpc.py`.

What the Demo Covers
--------------------

The CLI demo script performs the following steps:

1. Builds and starts the local `enterprise-dns` example and synchronizes the
   simulated routers into NSO.
2. Creates a base `enterprise-dns/enterprise-dns-instances` service instance
   for `ex0`.
3. Shows CLI `commit ?` completions for the available commit parameters.
4. Applies a CLI service change with `commit dry-run outformat cli-c` and then
   performs the actual CLI commit using `comment` and `label`.
5. Verifies the committed service and resulting device DNS configuration and
   inspects commit history in the CLI.

The RESTCONF demo script performs the following steps:

1. Builds and starts the local `enterprise-dns` example and prepares a base
   `enterprise-dns/enterprise-dns-instances` service instance for `ex0`.
2. Creates a structured RESTCONF commit-parameter payload with `label` and
   `dry-run`.
3. Sends a RESTCONF `PATCH` request using the `params` query parameter and
   prints the returned dry-run result.
4. Creates a second structured RESTCONF payload with `label` and `comment`.
5. Sends a RESTCONF `PATCH` request using the `X-Cisco-NSO-Commit-Params`
   header and performs the actual commit.
6. Uses the NSO CLI to verify the commit `label` and `comment`.
7. Verifies the committed service value over RESTCONF.

The NETCONF demo script performs the following steps:

1. Builds and starts the local `enterprise-dns` example and prepares a base
   `enterprise-dns/enterprise-dns-instances` service instance for `ex0`.
2. Sends a standard RFC 6241 `edit-config` request that includes structured
   `label` and `comment` parameters.
3. Uses the NSO CLI to verify the commit `label` and `comment`.
4. Verifies the committed service value over NETCONF.

The JSON-RPC demo script performs the following steps:

1. Builds and starts the local `enterprise-dns` example and prepares a base
   `enterprise-dns/enterprise-dns-instances` service instance for `ex0`.
2. Logs in to the JSON-RPC API.
3. Creates a read-write transaction, stages a service change with `set_value`,
   and passes structured dry-run parameters to `validate_commit` and `commit`.
4. Prints the dry-run result returned by the JSON-RPC `commit` method.
5. Creates a second transaction, stages the real service change, and passes
   structured `label` and `comment` parameters to `validate_commit` and
   `commit`.
6. Uses the NSO CLI to verify the commit `label` and `comment`.
7. Verifies the committed service value over JSON-RPC with `get_value`.

The RESTCONF JSON payload matches the contents of
`tailf-ncs-commit-params:commit-params`. For example:

```json
{
  "label": "restconf-demo",
  "dry-run": {
    "outformat": "cli-c"
  }
}
```

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

+ NSO Development Guide: Commit Parameters
+ NSO Development Guide: Introduction to NSO CLI
+ NSO Development Guide: CLI Commands
+ NSO Development Guide: RESTCONF API
+ NSO Development Guide: NSO NETCONF Server
+ NSO Development Guide: JSON-RPC API
+ `examples.ncs/sdk-api/maapi-commit-parameters`
