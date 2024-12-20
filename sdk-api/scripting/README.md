Scripting
=========

This example illustrates how to use plug-and-play scripts to add:

- CLI commands
- Policy scripts
- Post-commit callbacks

In this example, we rely on this part of `ncs.conf`:

    <scripts>
      <dir>./scripts</dir>
    </scripts>

The above registers `./scripts` as a directory where to look for scripts.
We have one CLI command, `echo.sh`, one policy script, `check_dir.sh`,
and one post-commit hook, `show_diff.sh`. The directory structure looks like
this:

    find scripts
    ./scripts
    ./scripts/command
    ./scripts/command/echo.sh
    ./scripts/policy
    ./scripts/policy/check_dir.sh
    ./scripts/post-commit
    ./scripts/post-commit/show_diff.sh

The scripts themselves define the interface of the scripts. NSO invokes
the scripts to determine their interfaces. This is automatically done at
startup and manually on demand via the `script reload` CLI command. The new CLI
command will be named `my script echo` in this
case.

Running the Example
-------------------

To run the steps below in this README from a demo shell script:

    make demo

The below steps are similar to the demo script using the J-style CLI instead of
the C-style CLI.

Start clean:

    ncs --stop
    make clean

Start NSO:

    ncs

Try out the new CLI command `my script echo`:

    > my script ?
    Possible completions:
      echo - Display a line of text
    > my script echo ?
    Possible completions:
      String to be displayed
      file - Redirect output to file
      nolf - Do not output the trailing newline
    > my script echo hello world
    hello world

Echo is a trivial command that echoes a string, but the
`scripts/command/echo.sh` script demonstrates how the parameter handling for
CLI command scripts works.

The policy script `scripts/policy/check_dir.sh` provides an example of how
policies can be enforced. Here, we try to change the `trace-dir` leaf to a
value the policy script does not allow.

    > configure
    % set devices global-settings trace-dir ?
    Description: The directory where trace files are stored
    Possible completions:
      <string>[./logs]
    % set devices global-settings trace-dir ./mybad
    % validate
    Failed: /devices/global-settings/trace-dir: must retain its original value\
            (./logs)

The post-commit script `scripts/post-commit/show_diff.sh` provides a simple
example of a side effect that can be performed just before a transaction is
ended. The script relies on an environment variable, so we need to restart NSO
to make it take effect.

    % exit
    > exit
    ncs --stop

Now we set the variable and start NSO again:

    cat my_trace_file.txt
    cat: my_trace_file.txt: No such file or directory
    export TEST_POST_COMMIT_SHOW_DIFF_FILE=my_trace_file.txt
    ncs
    ncs_cli -u admin
    > configure
    % set devices global-settings read-timeout ?
    Description: Timeout in seconds used when reading data
    Possible completions:
      <unsignedInt>[20]
    % set devices global-settings read-timeout 30
    % commit
    Commit complete.

Now we exit from the CLI and take a look at the side effects:

    % exit
    > exit
    cat my_trace_file.txt

    Fri Jan 16 14:07:56 CET 2015
    scripting/scripts/post-commit/show_diff.sh
    CONFD_MAAPI_USID=23
    CONFD_MAAPI_THANDLE=17

    --- transaction diff ---
    value set  : /devices/global-settings/read-timeout

When plug-and-play scripts have been created, updated, or deleted, there is no
need to restart NSO. The scripts may be reloaded with the command
`script reload`. The reload command may also be used to get various details of
information about the scripts

    ncs_cli -u admin
    > script reload ?
    Possible completions:
      all    - Show info about all scripts
      debug  - Show additional debug info about the scripts
      diff   - Show info about scripts changed since the last reload
      errors - Show info about erroneous scripts
    > script reload all
    examples.ncs/extension-apis/scripting/scripts: ok
        command:
            add_user.sh: unchanged
            echo.sh: unchanged
        policy:
            check_dir.sh: unchanged
        post-commit:
            show_diff.sh: unchanged
    > script reload all debug
    examples.ncs/extension-apis/scripting/scripts: ok
        command:
            add_user.sh: unchanged
    --- Output from examples.ncs/extension-apis/scripting/scripts/command/\
    add_user.sh --command ---
    1:
    2: begin command
    3:   modes: config
    4:   styles: c i j
    5:   cmdpath: user-wizard
    6:   help: Add a new user
    7: end
    8:
    ---
            echo.sh: unchanged
    --- Output from examples.ncs/extension-apis/scripting/scripts/command/\
    echo.sh --command ---
    1: begin command
    2:   modes: oper
    3:   styles: c i j
    4:   cmdpath: my script echo
    5:   help: Display a line of text
    6: end
    7:
    8: begin param
    9:  name: nolf
    10:  type: void
    11:  presence: optional
    12:  flag: -n
    13:  help: Do not output the trailing newline
    14: end
    15:
    16: begin param
    17:  name: file
    18:  presence: optional
    19:  flag: -f
    20:  help: Redirect output to file
    21: end
    22:
    23: begin param
    24:  presence: mandatory
    25:  words: any
    26:  help: String to be displayed
    27: end
    28:
    ---
        policy:
            check_dir.sh: unchanged
    --- Output from examples.ncs/extension-apis/scripting/scripts/policy/\
    check_dir.sh --policy ---
    1: begin policy
    2:   keypath: /devices/global-settings/trace-dir
    3:   dependency: /devices/global-settings
    4:   priority: 2
    5:   call: each
    6: end
    7:
    ---
        post-commit:
            show_diff.sh: unchanged
    --- Output from examples.ncs/extension-apis/scripting/scripts/post-commit/\
    show_diff.sh --post-commit ---
    1: begin post-commit
    2: end
    3:
    ---
    > exit

Cleanup
-------

Stop NSO and the simulated network, and clean all created files:

    ncs --stop
    make clean

Further Reading
---------------

+ NSO Operation & Usage: Plug-and-Play Scripting
+ The `demo.sh` script