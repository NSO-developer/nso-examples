#!/bin/sh

set -e

usage=
newline=cat
file=

while [ $# -gt 0 ]; do
    case "$1" in
        --h)
            usage=1
            ;;
        --command)
            # Configuration of the command
            #
            # modes   - CLI mode (oper config)
            # styles  - CLI style (c i j)
            # cmdpath - Full CLI command path
            # help    - Command help text
            #
            # Configuration of the parameters
            #
            # name     - (optional) name of the parameter
            # presence - optional or mandatory
            # type     - void - A parameter without a value
            # words    - any - Multi word param. Only valid for the last param
            # flag     - Extra word added before the parameter value
            # prefix   - Extra string prepended to the parameter value
            # help     - Command help text
            cat << EOF
begin command
  modes: oper
  styles: c i j
  cmdpath: my script echo
  help: Display a line of text
end

begin param
 name: nolf
 type: void
 presence: optional
 flag: -n
 help: Do not output the trailing newline
end

begin param
 name: file
 presence: optional
 flag: -f
 help: Redirect output to file
end

begin param
 presence: mandatory
 words: any
 help: String to be displayed
end
EOF
            exit
            ;;
        -n)
            # Disable output of newline
            newline="tr -d '\012'"
            ;;
        -f)
            # Redirect output to file
            if [ $# -lt 2 ]; then
                echo "Too few arguments"
                usage=1
            else
                file=$2
                shift
            fi
            ;;
        *)
            break
            ;;
    esac
    shift
done

# String to be displayed.
string=$*

if [ x"$usage" != x ]; then
    echo
    echo "Usage: $0 [-n] [-f file] string"
    echo
    echo "  --command     Mandatory for command scripts"
    echo "  --params      Mandatory for command scripts"
    echo
    echo "  -h            Display this help and exit"
    echo "  -n            Do not output trailing newline"
    echo "  -f filename   Redirect output to file"
    echo "  string        String to be displayed"
    exit 1
fi

if [ "x$file" = "x" ]; then
    echo "$string" | $newline
else
    echo "$string" | $newline > $file
fi
