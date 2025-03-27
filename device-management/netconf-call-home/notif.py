from __future__ import print_function
import socket
import select
import sys
import getopt

import _ncs
import _ncs.events as events


class EventSwitch(object):

    """
    """

    def __init__(self, sock, mask):
        """
        """
        self.sock = sock
        self._mask = mask
        self._event = None

    def switch(self, event_dict):
        self._event = event_dict
        switch_fun = self.get_switch_fun()
        switch_fun()

    def get_switch_fun(self):
        """
        Hacky python switch
        """
        return {
            events.NCS_NOTIF_CALL_HOME_INFO: self.call_home_info
        }.get(self._event['type'], self.default)

    def call_home_info(self):
        call_home_dict = self._event['call_home']
        info_type = call_home_dict['type']
        if info_type == events.CALL_HOME_DEVICE_DISCONNECTED:
            device = "device=" + str(call_home_dict['device'])
            print("Device disconnected:", device)
        else:
            af = call_home_dict['af']
            if af == socket.AF_INET:
                ip = "ip=" + str(call_home_dict['ip4'])
            elif af == socket.AF_INET6:
                ip = "ip=" + str(call_home_dict['ip6'])
            port = "port=" + str(call_home_dict['port'])
            ssh_host_key = "ssh_host_key=" + call_home_dict['ssh_host_key']
            alg = "ssh_key_alg=" + call_home_dict['ssh_key_alg']
            if info_type == events.CALL_HOME_DEVICE_CONNECTED:
                device = "device=" + str(call_home_dict['device'])
                print("Device connected:", device, ip, port, alg)
            elif info_type == events.CALL_HOME_UNKNOWN_DEVICE:
                print("Unknown device:", ip, port, alg)
            print(ssh_host_key)

    def default(self):
        print("Unhandled type:", self._event['type'])


def run(argv):
    """
    """
    if _ncs.PATH is None:
        event_sock = socket.socket()
        path = ""
    else:
        event_sock = socket.socket(family=socket.AF_UNIX)
        path = _ncs.PATH

    mask = None
    try:
        opts, _ = getopt.getopt(argv, "C")
    except getopt.GetoptError:
        sys.exit(2)
    for opt, _ in opts:
        if opt == '-C':
            mask = events.NCS_NOTIF_CALL_HOME_INFO

    noexists = _ncs.Value(init=1, type=_ncs.C_NOEXISTS)
    data = events.NotificationsData(heartbeat_interval=1000,
                                    health_check_interval=1000,
                                    stream_name="whatever",
                                    start_time=noexists,
                                    stop_time=noexists)

    events.notifications_connect2(sock=event_sock,
                                  mask=mask,
                                  ip=_ncs.ADDR,
                                  port=_ncs.PORT,
                                  path=path,
                                  data=data)

    print("Connected")

    event_switch = EventSwitch(sock=event_sock, mask=mask)

    loop(event_switch)

    event_sock.close()


def loop(event_switch):
    event_sock = event_switch.sock
    while True:
        (readables, _, _) = select.select([event_sock, sys.stdin], [], [])

        for readable in readables:
            if readable == event_sock:
                try:
                    event_dict = events.read_notification(event_sock)
                    event_switch.switch(event_dict)
                except (_ncs.error.Error) as external_e:
                    if external_e.confd_errno is _ncs.ERR_EXTERNAL:
                        print("csocket> " + str(external_e))
                    else:
                        raise external_e
            if readable == sys.stdin:
                user_input = sys.stdin.readline().rstrip()
                if user_input == "exit":
                    print("Bye!")
                    return False


if __name__ == "__main__":
    run(sys.argv[1:])
