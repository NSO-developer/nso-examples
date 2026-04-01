from datetime import datetime
import logging
import multiprocessing
from multiprocessing import Process
import sys
import ncs as tm
import _ncs as _tm


class StateIter:
    def __init__(self, log, name='state-iter'):
        self.log = log

    def iterate(self, kp, op, oldv, newv, state):
        self.log.info(f'iterate dummy/state kp={kp} op={op} oldv={oldv}'
                      f' newv={newv}')


class NameIter:
    def __init__(self, log, name='name-iter'):
        self.log = log

    def iterate(self, kp, op, oldv, newv, state):
        self.log.info(f'iterate dummy/name kp={kp} op={op} oldv={oldv}'
                      f' newv={newv}')


class DoSomethingAction(tm.dp.Action):
    def init(self, init_args):
        self.port = init_args[0]

    @tm.dp.Action.action
    def cb_action(self, uinfo, name,  kp, input, output):
        self.log.info(f'do-something action called with input: {input}')
        d = tm.dp.Daemon("dummy-notifier", port=self.port, log=self.log)
        nctx = _tm.dp.register_notification_stream(
            d.ctx(),
            None,
            tm.dp.take_worker_socket(
                d,
                'dummynotif',
                'dummynotif-key'),
            'something_done_notifications')
        d.start()
        now = datetime.now()
        tmnow = tm.DateTime(year=now.year,
                            month=now.month,
                            day=now.day,
                            hour=now.hour,
                            min=now.minute,
                            sec=now.second,
                            micro=now.microsecond,
                            timezone=0,
                            timezone_minutes=0)
        cs = _tm.cs_node_cd(None, "/something-done")
        ns = cs.ns()
        tag = cs.tag()
        scs = _tm.cs_node_cd(None, "/something-done/payload")
        sns = scs.ns()
        stag = scs.tag()
        tvs = [
                tm.TagValue(xmltag=tm.XmlTag(ns, tag),
                            v=tm.Value((tag, ns), tm.C_XMLBEGIN)),
                tm.TagValue(xmltag=tm.XmlTag(sns, stag),
                            v=tm.Value(f"hello world {now}", tm.C_BUF)),
                tm.TagValue(xmltag=tm.XmlTag(ns, tag),
                            v=tm.Value((tag, ns), tm.C_XMLEND))
            ]
        _tm.dp.notification_send(nctx, tmnow, tvs)
        d.finish()
        self.log.info(f'Sent notification with payload: "hello world {now}"')
        output.result = 'something done'


def oper_change_sub(port, log):
    with tm.maapi.Maapi(port=port, path=None) as m:
        m.wait_start(2)
    sub = tm.cdb.OperSubscriber(port=port, path=None)
    sub.register('/dummy:dummy/state',
                 StateIter(log),
                 subtype=tm.cdb.SUB_OPERATIONAL)
    sub.start()


def config_change_sub(port, log):
    with tm.maapi.Maapi(port=port, path=None) as m:
        m.wait_start(2)
    sub = tm.cdb.Subscriber(port=port, path=None)
    sub.register('/dummy:dummy/name',
                 NameIter(log),
                 subtype=tm.cdb.SUB_RUNNING)
    sub.start()


def main(port):
    log = tm.log.Log(logging.getLogger(__name__))
    multiprocessing.set_start_method('fork')
    p1 = Process(target=oper_change_sub, args=(port, log))
    p1.start()
    p2 = Process(target=config_change_sub, args=(port, log))
    p2.start()
    d = tm.dp.Daemon(name='myactiond', port=port, log=log)
    init_args = [port]
    a = []
    a.append(DoSomethingAction(daemon=d,
                               actionpoint='do-something',
                               log=log,
                               init_args=init_args))
    d.start()


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, filename='logs/dummy.log',
                        format='%(asctime)s %(levelname)-8s %(message)s')
    main(int(sys.argv[1]))
