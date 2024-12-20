Hello World
===========

The purpose of this example is to start NSO, understand the different
directories and files NSO uses, and start the NSO CLI and Web UI.

Preparations
------------

1. Ensure you have sourced the `ncsrc` file in `$NCS_DIR` to set up paths
   and environment variables to run NSO.

   Optional:
   To avoid creating the example files in the NSO installation directory
   `$NCS_DIR/examples.ncs/getting-started/hello-world`, when, for example,
   several users are sharing the same installation, you can run the example in
   a separate directory.

2. Create an empty directory, for example, in your home directory. NSO will
   create files and directories in this example. Make sure you change to this
   directory before continuing.

NSO runs as a daemon and needs a few directories for logs, database, etc. Their
location is in a configuration file usually called `ncs.conf`.
Unless supplied as an argument using the `-c` option, the NSO daemon will look
for `./ncs.conf`, followed by `etc/ncs/ncs.conf` in the NSO installation
directory. NSO includes a script called `ncs-setup` to make it easy to get
started and will create the directories and files needed.

    $ ls -1a
    .
    ..
    $ ncs-setup --dest .
    $ ls -1a
    .
    ..
    README.ncs
    logs
    ncs-cdb
    ncs.conf
    packages
    scripts
    state

The `ncs-setup` script created an initial configuration file, `./ncs.conf`, and
the directories needed to run NSO. To start the NSO daemon, run `ncs`,
optionally providing the configuration file as an argument: `-c ./ncs.conf`

    ncs

The NSO daemon is now running. Use the `--status` option to verify:

    ncs --status

You can start the NSO CLI using the `ncs_cli` command. By default you start the
CLI as the user you run the shell as. Most examples will use a default
built-in user called `admin`. To start the J-style CLI as user `admin`:

    ncs_cli -u admin

or

    ncs_cli -u admin -J

For C-style CLI:

    ncs_cli -u admin -C

NSO also starts its built-in SSH server by default, listening on port `2024`.
So `ssh` can also be used to log into the CLI:

    $ ssh -l admin -p 2024 localhost
    admin@localhost's password:

The default password for the `admin` user is `admin`. Type `exit` to exit the
CLI:

    admin connected from 127.0.0.1 using ssh on host.example.org
    admin@host> show ncs-state | nomore
    ...
    admin@host> exit
    Connection to localhost closed.

NSO also starts a web server listening on port `8080`. By directing a
browser to http://localhost:8080/, you can log in using the Web UI with
the `admin` user.

Stop the NSO daemon by using the `--stop` option:

    ncs --stop

NSO will now have several logs in the `logs` directory. Open the `logs/ncs.log`
log file to find out when NSO was started, what files were loaded during start,
etc.

    less logs/ncs.log

To wipe all log files, restore all settings done in NSO, and revert to the
"empty" factory default configuration, use the `--reset` option with the
`ncs-setup` script.

    ncs-setup --reset

A shell script, `demo.sh`, implements the above steps by setting up the NSO
run-time directory in a `nso-rundir` and uses SSH public key authentication
instead of a password.

    ,/demo.sh

Further Reading
---------------

Man-pages: ncs-setup(1) ncs(1) ncs_cli(1)
