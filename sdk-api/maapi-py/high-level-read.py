#
# High level Python MAAPI read example
#
# By using the high level "with contexts" resource cleanup is simplified.
#
import ncs


def high_level_read():
    # Resources cleanup, i.e. closing cursor, finishing transaction,
    # end user session and closing the maapi socket is done when respective
    # with-block is exited

    with ncs.maapi.Maapi() as m:
        with ncs.maapi.Session(m, 'admin', 'system'):
            with m.start_read_trans() as t:
                root = ncs.maagic.get_root(t)
                for dev in root.ncs__devices.device:
                    print('hlr: {} {} {}'.format(
                        dev.name, dev.address, dev.port))


if __name__ == '__main__':
    high_level_read()
