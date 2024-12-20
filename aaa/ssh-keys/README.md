SSH Keys
--------

How NSO uses SSH host keys has already been touched upon in, for example, the
`simulated-cisco-ios`, `real-device-cisco-ios`, and `real-device-juniper`
examples. Here, we go into some more detail about the options for managing and
using SSH host keys. We also describe how to set up authentication using a
private SSH key instead of a password ("publickey" authentication in SSH
terminology). See also the "SSH Key Management" chapter in the NSO Operation
& Usage Guide. The example uses two simulated devices, one emulated Cisco IOS
router (same as in example `simulated-cisco-ios`) accessed via a CLI NED, and
one NETCONF device (same as in example `netconf-device`).

Preparations
------------

Make sure you have sourced the ncsrc file in `$NCS_DIR`. This sets up paths
and environment variables to run NSO. To run the steps below in this README
from a demo shell script:

    ./demo.sh

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

First, create a package called `host` from the YANG file we have for the
NETCONF device:

    ncs-make-package --netconf-ned . host

We now need to build the YANG file in the package:

    make -C host/src

Then, we create a simulated network with one instance of this device:

    ncs-netsim create-network host 1 h

And add one instance of the Cisco IOS device to the simulated network:

    ncs-netsim add-to-network $NCS_DIR/packages/neds/cisco-ios 1 c

Finally, we create an NSO setup to use this simulated network:

    ncs-setup --netsim-dir ./netsim --dest .

And start everything:

    ncs-netsim start
    ncs
    ncs_cli -u admin

SSH Host Keys
-------------

The SSH protocol uses public key technology to allow a client to authenticate
the server, i.e., verify that it is really talking to the intended server and
not some man-in-the-middle intruder. This requires that the client has prior
knowledge of the server's public keys and the server proves its possession of
the corresponding private key by using it to sign some data. These keys are
normally called "host keys".

Host Keys for Devices
---------------------

In the case of NSO connecting to managed devices, NSo is the SSH client
and the device is the SSH server. Thus NSO needs to have the public SSH
host keys for the devices in order to do this verification. We saw
already in the `simulated-cisco-ios` example that `ncs-setup` picked up
the host keys from the simulated network created by ncs-netsim -
specifically, it uses `ncs-netsim ncs-xml-init` to create a CDB init
file with all the meta-data about the devices, including the SSH host
keys.

Thus we can already display these keys, which are stored under
`/devices/device{name}/ssh`:

In the case of NSO connecting to managed devices, NSO is the SSH client, and
the device is the SSH server. Thus, NSO needs to have the public SSH host keys
for the devices to verify this. We saw already in the `simulated-cisco-ios`
example that `ncs-setup` picked up the host keys from the simulated network
created by ncs-netsim - specifically, it uses `ncs-netsim ncs-xml-init` to
create a CDB init file with all the meta-data about the devices, including the
SSH host keys.

Thus, we can already display these keys, which are stored under
`/devices/device{name}/ssh`:

    > set paginate false
    > show configuration devices device * ssh
    device c0 {
        ssh {
            host-key ssh-rsa {
                key-data "AAAAB3...";
            }
            host-key ssh-ed25519 {
                key-data "AAAAC3...";
            }
        }
    }
    device h0 {
        ssh {
            host-key ssh-rsa {
                key-data "AAAAB3...";
            }
            host-key ssh-ed25519 {
                key-data "AAAAC3...";
            }
        }
    }

The keys are truncated in the transcript above since they are shown as a single
long line of base64-encoded binary data. Since we have the host keys, we can
also connect to the devices without issues:

    > request devices connect
    connect-result {
        device c0
        result true
        info (admin) Connected to c0 - 127.0.0.1:10023
    }
    connect-result {
        device h0
        result true
        info (admin) Connected to h0 - 127.0.0.1:12022
    }

This is all well and fine for simulated devices, but in a real network, we
don't have this luxury, of course. So let's simulate that situation by
disconnecting from the devices, and deleting the keys:

    > request devices disconnect
    > configure
    % delete devices device * ssh host-key
    % commit
    % request devices connect
    connect-result {
        device c0
        result false
        info Failed to authenticate towards device c0: Unknown SSH host key
    }
    connect-result {
        device h0
        result false
        info Failed to authenticate towards device h0: Unknown SSH host key
    }

To resolve this situation, it is possible to disable the host key verification
(see below), but of course, it is better from a security point of view to
ensure the keys are known to NSO. The keys can be input directly in the CLI
just as any other configuration data - for each device, NSO can have several
keys configured, one each for the `ssh-rsa` (RSA key) and `ssh-ed25519`
(ED25519 key) algorithms, respectively. Here is how we could configure an RSA
key for device `c0`, using the RFC 4716 format produced by `ssh-keygen -e` from
the OpenSSH implementation - other SSH implementations are usually also able to
provide this format:

    % set devices device c0 ssh host-key ssh-ed25519
    Value for 'key-data' (<SSH public host key>):
    [Multiline mode, exit with ctrl-D.]
    > ---- BEGIN SSH2 PUBLIC KEY ----
    > Comment: "ED25519, converted by per@mars.tail-f.com from OpenSSH"
    > AAAAC3NzaC1lZDI1NTE5AAAAIFM3gvv8XwVIvdEQ2iGdHPQ1O7dTjXW1fwbl0pLv4off
    > ---- END SSH2 PUBLIC KEY ----
    > ^D

    % commit
    % request devices device c0 connect
    result false
    info Failed to authenticate towards device c0: SSH host key mismatch

The connection fails here with a different error - since this wasn't the
correct key (the netsim host key is generated at NSO install time). NSO will
also accept the "native OpenSSH" public key format, a single line looking like
`ssh-ed25519`, as well as the plain base64-encoded data.

Fetching Host Keys from Devices
-------------------------------

Obviously, this method is quite useless if we need to do this for many devices,
and thus, NSO provides an action to fetch the host keys for one or more
devices. This invocation will fetch the keys for all devices (in our case,
there are only two):

    % request devices device * ssh fetch-host-keys
    devices device c0 ssh fetch-host-keys
        result updated
        fingerprint {
            algorithm ssh-ed25519
            value 19:0b:40:91:3b:f2:0c:b2:ba:7f:af:8a:15:ea:a0:e5
        }
        fingerprint {
            algorithm ssh-rsa
            value 03:64:fc:b7:87:bd:34:5e:3b:6e:d8:71:4d:3f:46:76
        }
    devices device h0 ssh fetch-host-keys
        result updated
        fingerprint {
            algorithm ssh-ed25519
            value 19:0b:40:91:3b:f2:0c:b2:ba:7f:af:8a:15:ea:a0:e5
        }

As we can see, the action reports that the result is "updated" for both
devices. The key we configured for device `c0` has been replaced with the
correct one, and for device `h0`, we now have a key where none was configured
before the action invocation. The action committed the new keys (it will do
this when possible, i.e., when the device entry is already committed), and
thus, we can immediately verify that successful connections can be made:

    % request devices connect
    connect-result {
        device c0
        result true
        info (admin) Connected to c0 - 127.0.0.1:10023
    }
    connect-result {
        device h0
        result true
        info (admin) Connected to h0 - 127.0.0.1:12022
    }

The `fetch-host-keys` invocation can, of course, take all the usual
wildcard/range expressions for the device name, i.e., we can use invocations
like:

    request devices device c* ssh fetch-host-keys

Or:

    request devices device c0..10 ssh fetch-host-keys

Additionally, the action can be invoked for a device group with some additional
input parameters:

    % set devices device-group netconf device-name h0
    % commit
    % request devices device-group netconf fetch-ssh-host-keys ?
    Description: Retrieve SSH host keys from all devices
    Possible completions:
      suppress-fingerprints     - Do not return key fingerprints
      suppress-positive-result  - Only return result if key retrieval failed
      suppress-unchanged-result - Do not return result if keys are unchanged

Finally it can be invoked on the "devices level":

    % request devices fetch-ssh-host-keys ?
    Possible completions:
      device                    - Only fetch host keys from these devices.
      suppress-fingerprints     - Do not return key fingerprints
      suppress-positive-result  - Only return result if key retrieval failed
      suppress-unchanged-result - Do not return result if keys are unchanged

There is also an action to request that the fingerprints of already
configured keys are shown:

    % request devices device * ssh host-key * show-fingerprint
    devices device c0 ssh host-key ssh-rsa show-fingerprint
        value 03:64:fc:b7:87:bd:34:5e:3b:6e:d8:71:4d:3f:46:76
    devices device c0 ssh host-key ssh-ed25519 show-fingerprint
        value 19:0b:40:91:3b:f2:0c:b2:ba:7f:af:8a:15:ea:a0:e5
    devices device h0 ssh host-key ssh-rsa show-fingerprint
        value 03:64:fc:b7:87:bd:34:5e:3b:6e:d8:71:4d:3f:46:76
    devices device h0 ssh host-key ssh-ed25519 show-fingerprint
        value 19:0b:40:91:3b:f2:0c:b2:ba:7f:af:8a:15:ea:a0:e5

If we have configured but not yet committed keys, this action reports the
fingerprint of those not yet committed keys when run in "configure mode". If we
want to check the fingerprints of the keys already committed when we have
uncommitted keys, we must run it from "operational mode". I.e., in configure
mode, we would invoke it as run `request devices device ...`.

Modifying the Host Key Verification Level
-----------------------------------------

If we don't want to deal with the issues of host key verification, we can
disable it completely by setting `/ssh/host-key-verification` to `none`:

    % set ssh host-key-verification ?
    Description: Level of host key verification
    Possible completions:
      [reject-unknown]
      none            - Accept any host key
      reject-mismatch - Reject host keys that do not match the stored key
      reject-unknown  - Reject unknown host keys
    % set ssh host-key-verification none
    % commit

This is, of course, not good for security, but it can be a reasonable choice
during development. The examples in the NSO example collection set the value to
`none` where relevant using an XML init file for CDB. The `reject-mismatch`
setting offers a "middle ground", where the device host keys will be verified
against the keys that we actually have configured. Still, connections will
succeed even if we don't have any keys configured. The verification level can
also be set per device, overriding the above global setting:

    % set devices device h0 ssh host-key-verification reject-mismatch

Host Keys for NSO Cluster Nodes
-------------------------------

When NSO is set up in a cluster configuration, the nodes in the cluster
communicate via NETCONF over SSH. In this case, the management of SSH host keys
is outside the scope of this example, but it is very similar to the management
of devices. However, no method is equivalent to `ncs-netsim`'s
auto-configuration of SSH keys. Thus, this will always require some
configuration.

Samples of relevant commands:

    % set cluster remote-node ncs2 ssh host-key ssh-rsa
    % request cluster remote-node * ssh fetch-host-keys
    % request cluster remote-node * ssh host-key * show-fingerprint
    % set cluster remote-node * ssh host-key-verification reject-unknown

Note that the global setting of `/ssh/host-key-verification` applies to both
devices and NSO cluster nodes. Thus, the last command above overrides the
global `none` setting we did earlier with the most restrictive `reject-unknown`
setting for the cluster nodes configured when the command is given.

SSH Publickey Authentication
-----------------------------

In SSH publickey authentication, the server is configured with one or more
public keys authorized to authenticate a user. The client proves possession of
one of the corresponding private keys by using it to sign some data. I.e., the
exact reverse of the server authentication provided by host keys.

Publickey Authentication for Devices
------------------------------------

In this case, NSO is the SSH client and thus needs a private SSH key for
authentication. Of course, the device must also be configured with the
corresponding public key authorized for authentication.

The netsim devices we set up in this example will look for an `authorized_keys`
file in the user's `ssh_keydir` directory. The top-level directory of the
example contains an Ed25519 private/public key pair in the files
`id_ed25519` and `id_ed25519.pub`, respectively. We must change the default
`ssh_keydir` directory to one inside the example directory tree and create the
`authorized_keys` file. Real devices may, of course, have some completely
different procedures for this setup.

First, we use `ncs-netsim` from the Linux shell prompt to connect to the CLI of
the `h0` device and configure the ssh_keydir:

    ncs-netsim cli h0
    >configure
    % set aaa authentication users user admin ssh_keydir home/admin/.ssh
    % commit
    % exit
    > exit

Using a relative path for this directory is not a good idea in practice, but it
is convenient in the context of this example. So, let's create and populate the
directory:

    mkdir -p netsim/h/h0/home/admin/.ssh
    cp id_ed25519.pub netsim/h/h0/home/admin/.ssh/authorized_keys

Now, we can configure NSO to use the corresponding private key. As with all
authentication towards devices, this uses the `authgroup` indirection and the
`umap` selection mechanisms. These were already set up for password
authentication using `ncs-setup`. We will only change the settings for user
`admin` in the default `authgroup`:

    ncs_cli -u admin
    > configure
    % edit devices authgroups group default
    % set umap admin public-key
    % commit

This chooses public key authentication with the default parameters, using a
file called `id_ed25519` in the local user's `ssh_keydir` directory. To see
these defaults, we need to use the details pipe command:

    % show umap admin public-key | details
    private-key {
        file {
            name id_ed25519;
        }
    }

Having the private key in the file system may be convenient in some cases, but
generally, it is probably preferable to have it configured in the NSO CDB data
store. Thus, we will not cover this case further here other than noting that
this default assumes an unencrypted private key, i.e., a file without a
passphrase, which is a very bad practice from a security point of view. It is
possible to configure a passphrase for a private key in the file system either
explicitly via:

    % set umap admin public-key private-key file passphrase
    (<AES encrypted string>):

Or, tell NSO to use the password of the local user as the passphrase, which
only works if we have logged in to NSO using password authentication via:

    % set umap admin public-key private-key file use-password

To configure a private key in CDB, we create an entry in the `/ssh/private-key`
list:

    % top
    % run file show id_ed25519
    -----BEGIN OPENSSH PRIVATE KEY-----
    b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABCc5s4icM
    8h0SVxG52a46EpAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIFM3gvv8XwVIvdEQ
    2iGdHPQ1O7dTjXW1fwbl0pLv4offAAAAoDbnwxKqWaPuq0/M9MIbA9YHVQzc4GxPBs/gUF
    5AEG9zc+H24E0fQkqXTcJKpLHFpe5NsYOJBQAyN4wn9ojmXaXXOZ4dbwoHh/Iup9anm4pw
    JT1BJnMJcg/svr6vrCf1Rk4KOk9ac/exuHqqhkshl0aVoz7OqoxALWDcfChDBgogIhgnYX
    0q3x8Q7t6t0oAk76uenFKOW4XZ2puI+6QyPRQ=
    -----END OPENSSH PRIVATE KEY-----

    % set ssh private-key admin
    Value for 'key-data' (<SSH private user key>):
    [Multiline mode, exit with ctrl-D.]
    > -----BEGIN OPENSSH PRIVATE KEY-----
    > b3BlbnNzaC1rZXktdjEAAAAACmFlczI1Ni1jdHIAAAAGYmNyeXB0AAAAGAAAABCc5s4icM
    > 8h0SVxG52a46EpAAAAGAAAAAEAAAAzAAAAC3NzaC1lZDI1NTE5AAAAIFM3gvv8XwVIvdEQ
    > 2iGdHPQ1O7dTjXW1fwbl0pLv4offAAAAoDbnwxKqWaPuq0/M9MIbA9YHVQzc4GxPBs/gUF
    > 5AEG9zc+H24E0fQkqXTcJKpLHFpe5NsYOJBQAyN4wn9ojmXaXXOZ4dbwoHh/Iup9anm4pw
    > JT1BJnMJcg/svr6vrCf1Rk4KOk9ac/exuHqqhkshl0aVoz7OqoxALWDcfChDBgogIhgnYX
    > 0q3x8Q7t6t0oAk76uenFKOW4XZ2puI+6QyPRQ=
    > -----END OPENSSH PRIVATE KEY-----
    > ^D
    % set ssh private-key admin passphrase
    (<AES encrypted string>): ******
    % show ssh private-key
    private-key admin {
        key-data   "$8$...";
        passphrase "$8$...";
    }

Here, we displayed the file with the `file show` operational-mode command and
then just cut and pasted it into the prompt from set SSH private key admin.
Since the key is encrypted with a passphrase, we also need to set the
passphrase (which is secret, without any quotes). NSO will always store the key
in an encrypted form, so it can be OK to use a key without a passphrase here.
But the security-conscious user will prefer not to have the cleartext key
available at any point.

We chose `admin` as the key in the list, but it is an entirely arbitrary name.
The selection of a specific key is made by name in the `umap` list:

    % edit devices authgroups group default
    % set umap admin public-key private-key name admin
    % commit
    % top

Now we're all done and can verify that we can authenticate with this private
key:

    % request devices disconnect
    % request devices device h0 connect
    result true
    info (admin) Connected to h0 - 127.0.0.1:12022

We can do the same exercise for the `c0` device, but the device setup is a bit
more complex since the Cisco emulation hides the `tailf-aaa.yang` module that
is used to set the `ssh_keydir`. Thus, it is not covered here.

Publickey Authentication for MSO Cluster Nodes
----------------------------------------------

As for host keys, the public key authentication setup for NSO cluster nodes is
outside the scope of this example, but the client-side configuration is
identical to that for devices, only in a different location. The `authgroups`
for cluster node authentication are found in the `/cluster/authgroup` list
instead of in `/devices/authgroups/group` - everything else is the same.

However, for cluster nodes, the server side must also be configured - this
requires the creation of an `authorized_keys` file along the lines of what we
did for the `h0` device above.

Cleanup
-------

Stop NSO and clean all created files:

    ncs-netsim stop
    ncs --stop
    rm -rf netsim README.ncs README.netsim host logs ncs-cdb ncs.conf packages
    rm -rf scripts state

Further Reading
---------------

+ NSO Operation & Usage Guide: SSH Key Management
+ The `demo.sh` script