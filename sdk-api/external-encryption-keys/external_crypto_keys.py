#!/usr/bin/env python3

import os
import sys


def main():
    key_file = os.getenv('NCS_EXTERNAL_KEYS_ARGUMENT', None)
    if key_file is None:
        error('NCS_EXTERNAL_KEYS_ARGUMENT environment not set')
    if len(key_file) == 0:
        error('NCS_EXTERNAL_KEYS_ARGUMENT is empty')

    try:
        with open(key_file, 'r') as f_obj:
            keys = f_obj.read()
            sys.stdout.write(keys)
    except Exception as ex:
        error('unable to open/read {}: {}'.format(key_file, ex))


def error(msg):
    print('ERROR={}'.format(msg))
    sys.exit(1)


if __name__ == '__main__':
    main()
