import ncs as tm
import logging
import time

confd_port = 0


class MyIter(object):
    def __init__(self, log, name='slowpoke'):
        self.log = log
        self.name = name

    def iterate(self, kp, op, oldv, newv, state):
        self.log.info(
                   f'Read trans-delay setting from CDB, IPC port {confd_port}')
        with tm.maapi.single_read_trans('admin', 'combobulate',
                                        port=confd_port) as t:
            delay = int(t.get_elem('/sys/trans-delay'))
        self.log.info(f'Combobulating for {delay}s...')
        time.sleep(delay)
        self.log.info('Combobulating: DONE')
        return tm.ITER_STOP


def main(port):
    global confd_port
    log = tm.log.Log(logging.getLogger(__name__))
    confd_port = port

    sub = tm.cdb.Subscriber(log=log, port=port)
    sub.register('/r:sys/interfaces', MyIter(log))
    sub.start()


if __name__ == '__main__':
    import sys
    logging.basicConfig(level=logging.INFO, filename='logs/slowpoke.log',
                        format='%(asctime)s %(levelname)-8s %(message)s')
    main(int(sys.argv[1]))
