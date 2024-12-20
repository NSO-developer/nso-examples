#!/usr/bin/env python3
import select
import socket
import sys
import _ncs
import _ncs.events as events

from datetime import datetime


def get_dbname(event):
    if event['compaction']['dbfile'] == events.COMPACTION_A_CDB:
        return "A.cdb"
    elif event['compaction']['dbfile'] == events.COMPACTION_O_CDB:
        return "O.cdb"
    elif event['compaction']['dbfile'] == events.COMPACTION_S_CDB:
        return "S.cdb"


def handle_compaction_notification(event):
    timestamp_us = event['compaction']['time_start']
    dt = datetime.utcfromtimestamp(timestamp_us/1000000)
    time_str = dt.strftime("%FT%H:%M:%S")

    print("Received compaction notification for %s" % get_dbname(event))
    print("Time of compaction: %s UTC" % time_str)
    print("Number of transactions: %s" % event['compaction']['ntrans'])


def loop(event_sock):
    (readables, _, _) = select.select([event_sock], [], [])

    for readable in readables:
        try:
            event_dict = events.read_notification(event_sock)
            handle_compaction_notification(event_dict)
        except (_ncs.error.Error) as external_e:
            if external_e.ncs_errno is _ncs.ERR_EXTERNAL:
                print("csocket> " + str(external_e))
            else:
                raise external_e


def setup():
    if _ncs.PATH is None:
        event_sock = socket.socket()
    else:
        event_sock = socket.socket(family=socket.AF_UNIX)
    mask = events.NOTIF_COMPACTION
    noexists = _ncs.Value(init=1, type=_ncs.C_NOEXISTS)
    data = events.NotificationsData(heartbeat_interval=1000,
                                    health_check_interval=1000,
                                    stream_name="compaction_notification",
                                    start_time=noexists,
                                    stop_time=noexists)

    events.notifications_connect2(sock=event_sock,
                                  mask=mask,
                                  ip='127.0.0.1',
                                  port=_ncs.PORT,
                                  path=_ncs.PATH,
                                  data=data)
    return event_sock


def teardown(event_sock):
    event_sock.close()


def main(argv):
    event_sock = setup()
    print("Waiting for compaction notification...")

    loop(event_sock)

    teardown(event_sock)


if __name__ == "__main__":
    main(sys.argv[1:])
