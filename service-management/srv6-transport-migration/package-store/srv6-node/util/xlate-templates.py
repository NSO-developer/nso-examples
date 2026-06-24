#!/usr/bin/env python3
#
# Read old=new value pairs from arg1 file and translate stdin accordingly.
#
import sys

with open(sys.argv[1]) as f:
    subs = [x.strip().split('=', maxsplit=1) for x in f.readlines() if x]

for line in sys.stdin:
    for s in subs:
        line = line.replace(s[0], s[1])
    print(line, end='')
