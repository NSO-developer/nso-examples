#!/usr/bin/env bash

set -e

while [ $# -gt 0 ]; do
    case "$1" in
        --command)
            # Configuration of the command
            #
            # modes   - CLI mode (oper config)
            # styles  - CLI style (c i j)
            # cmdpath - Full CLI command path
            # help    - Command help text
            #
            # Configuration of each parameter
            #
            # name     - (optional) name of the parameter
            # more     - (optional) true or false
            # presence - optional or mandatory
            # type     - void - A parameter without a value
            # words    - any - Multi word param. Only valid for the last param
            # flag     - Extra word added before the parameter value
            # prefix   - Extra string prepended to the parameter value
            # help     - Command help text
            cat << EOF

begin command
  modes: config
  styles: c i j
  cmdpath: user-wizard
  help: Add a new user
end
EOF
            exit
            ;;
        *)
            break
            ;;
    esac
    shift
done

## Ask for user name
while true; do
    echo -n "Enter user name: "
    read user

    if [ ! -n "${user}" ]; then
        echo "You failed to supply a user name."
    elif ncs-maapi --exists "/aaa:aaa/authentication/users/user{${user}}"; then
        echo "The user already exists."
    else
        break
    fi
done

## Ask for password
while true; do
    echo -n "Enter password: "
    read -s pass1
    echo

    if [ "${pass1:0:1}" == "$" ]; then
        echo -n "The password must not start with $. Please choose a "
        echo    "different password."
    else
        echo -n "Confirm password: "
        read -s pass2
        echo

        if [ "${pass1}" != "${pass2}" ]; then
            echo "Passwords do not match."
        else
            break
        fi
    fi
done

groups=`ncs-maapi --keys "/nacm/groups/group"`
while true; do
    echo "Choose a group for the user."
    echo -n "Available groups are: "
    for i in ${groups}; do echo -n "${i} "; done
    echo
    echo -n "Enter group for user: "
    read group

    if [ ! -n "${group}" ]; then
        echo "You must enter a valid group."
    else
        for i in ${groups}; do
            if [ "${i}" == "${group}" ]; then
                # valid group found
                break 2;
            fi
        done
        echo "You entered an invalid group."
    fi
    echo
done

echo "Creating user"

ncs-maapi --create "/aaa:aaa/authentication/users/user{${user}}"
ncs-maapi --set "/aaa:aaa/authentication/users/user{${user}}/password" \
                "${pass1}"

echo "Setting home directory to: /homes/${user}"
ncs-maapi --set "/aaa:aaa/authentication/users/user{${user}}/homedir" \
            "/homes/${user}"

echo "Setting ssh key directory to: /homes/${user}/ssh_keydir"
ncs-maapi --set "/aaa:aaa/authentication/users/user{${user}}/ssh_keydir" \
            "/homes/${user}/ssh_keydir"

ncs-maapi --set "/aaa:aaa/authentication/users/user{${user}}/uid" "1000"
ncs-maapi --set "/aaa:aaa/authentication/users/user{${user}}/gid" "100"

echo "Adding user to the ${group} group."
gusers=`ncs-maapi --get "/nacm/groups/group{${group}}/user-name"`

for i in ${gusers}; do
    if [ "${i}" == "${user}" ]; then
        echo "User already in group"
        exit 0
    fi
done

ncs-maapi --set "/nacm/groups/group{${group}}/user-name" "${gusers} ${user}"


