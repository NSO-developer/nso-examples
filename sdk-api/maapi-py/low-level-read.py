#
# Low level Python MAAPI read example
#

import socket
import _ncs


def read_and_print_device_info(s, th, deviceName):
    path = '/ncs:devices/device{"' + deviceName + '"}'

    def read(deviceLeaf):
        v = _ncs.maapi.get_elem(s, th, path + '/' + deviceLeaf)
        return v

    address = str(read('address'))
    port = int(read('port'))

    print('llr: %s %s %s' % (deviceName, address, port))


def low_level_read():

    if _ncs.PATH is None:
        s = socket.socket()
        path = ""
    else:
        s = socket.socket(family=socket.AF_UNIX)
        path = _ncs.PATH

    _ncs.maapi.connect(s, '127.0.0.1', _ncs.NCS_PORT, path)

    _ncs.maapi.start_user_session(s, 'admin',
                                  'low-level-read', [], '127.0.0.1',
                                  _ncs.PROTO_TCP)

    th = _ncs.maapi.start_trans(s, _ncs.RUNNING, _ncs.READ)

    # Read explicitly known names
    read_and_print_device_info(s, th, 'ex0')
    read_and_print_device_info(s, th, 'ex1')
    read_and_print_device_info(s, th, 'ex2')

    _ncs.maapi.finish_trans(s, th)
    _ncs.maapi.end_user_session(s)
    _ncs.maapi.close(s)


if __name__ == '__main__':
    low_level_read()
