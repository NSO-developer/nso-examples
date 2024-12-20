#!/usr/bin/env python3

import sys


class LogInfo(object):
    """Log stream information"""

    def __init__(self, argv):
        self.id = argv[0]
        self.type = argv[1]
        self.format = argv[2]


def main():
    if len(sys.argv) != 6:
        usage()
    elif sys.argv[1] != '1':
        abort('unsupported version {}, version 1 supported'.format(
            sys.argv[1]))
    elif sys.argv[2] != 'log':
        abort('unsupported command {}, command log supported'.format(
            sys.argv[1]))

    log_info = LogInfo(sys.argv[3:])
    process_log_data(log_info)


def usage():
    abort('usage: {} version command log-id log-type log-format'.format(
        sys.argv[0]))


def abort(msg):
    sys.stderr.write(msg)
    sys.stderr.write('\n')
    sys.exit(1)


def process_log_data(log_info):
    # only filter data for raw netconf-trace data, send all other log
    # data to the void.
    if log_info.type == 'ned-trace-java' and log_info.format == 'pretty':
        process_trace(log_info)
    else:
        abort('unsupported log type {} and/or format {}'.format(
            log_info.type, log_info.format))


def process_trace(log_info):
    with open('{}_filtered.trace'.format(log_info.id), 'a') as f_obj:
        line = sys.stdin.readline()
        while line:
            if line.startswith('banner motd'):
                f_obj.write(line.replace('secret', '********'))
            else:
                f_obj.write(line)
            f_obj.flush()
            line = sys.stdin.readline()


if __name__ == '__main__':
    main()
