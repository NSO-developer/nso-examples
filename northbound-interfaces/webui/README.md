NSO package with a simple webui
===============================

This example demonstrates how to use and update a custom Web UI package
using the JSON-RPC API to query data from NSO.

Overview
--------

The custom Web UI displays the following information:

- The running NSO version
- The currently logged-in user
- A list of packages loaded in NSO
- A list of devices loaded in NSO

Package contents
----------------

* **webui/webui.json**: This empty file is required for the package to be
                        recognized by NSO as a custom web UI package.
* **webui/index.html**: This is the required entry point for the package web
                        UI, where the user will land when clicking on the link
                        in the Home view.
* **webui/script.js**: Contains vanilla JavaScript to perform example requests
                       to the NSO JSON-RPC.
* **webui/style.css**: CSS styles.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

Setup the ncs with ncs-setup

    ncs-setup --dest .

Start the ncs

    ncs

The NSO webui is enabled by default at port 8080:

    http://localhost:8080

After authentication, `admin`/`admin`, the custom package UI can be accessed from the web UI
Home view under packages, or directly at:

    http://localhost:8080/custom/webui-basic-example

Web development with NSO
------------------------

1. To update the package, make changes to the source code, in this example
   mentioned in the Package contents section above.

2. Redeploy the package to update the NSO state

       ncs_cli -u admin <<< 'request packages package webui-basic-example redeploy'

   Alternatively, use a file-watching tool like __entr__ to automatically reload
   the package when a file is modified:

       find ./packages/webui-basic-example/webui -name '*' | entr sh -c "ncs_cli -u admin <<< 'request packages package webui-basic-example redeploy'"

3. Reload the browser to see updates

Cleanup
-------

Stop all daemons and clean all created files:

    make stop clean

Further Reading
---------------

NSO Development Guide: Web UI Development
