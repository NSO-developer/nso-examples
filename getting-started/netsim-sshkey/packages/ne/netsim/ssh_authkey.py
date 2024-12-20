"""A subscriber that sync a list of authorized keys with a file used by the
netsim/ConfD SSH server to authenticate users using public keys.

See the README file for more information
"""
import logging
import os
import ncs as tm


class MyIter:
    """Iterate configuration changes"""
    def __init__(self, log, name='authkey'):
        """Init"""
        self.log = log
        self.name = name

    def iterate(self, kp, op, oldv, newv, state):
        """Sync the users authorized keys file with configuration changes to
        the authkey list in CDB.
        """
        self.log.info(f'iterate kp={kp} op={op} oldv={oldv} newv={newv}')
        cwd = os.getcwd()
        if op is tm.MOP_CREATED:
            self.log.info(f'create user={kp[2][0]} pubkey_data={kp[0][0]}')
            with open(f'{cwd}/homes/{kp[2][0]}/.ssh/authorized_keys', 'a+',
                      encoding='utf-8') as file:
                file.write(f"{kp[0][0]}\n")
        elif op is tm.MOP_DELETED:
            self.log.info(f'delete user={kp[2][0]} pubkey_data={kp[0][0]}')
            with open(f'{cwd}/homes/{kp[2][0]}/.ssh/authorized_keys', 'r',
                      encoding='utf-8') as file:
                lines = file.readlines()
            with open(f'{cwd}/homes/{kp[2][0]}/.ssh/authorized_keys', 'w',
                      encoding='utf-8') as file:
                for line in lines:
                    if line not in (kp[0][0], "\n"):
                        file.write(line)
        return tm.ITER_CONTINUE


def main(port):
    """Register a CDB subscriber to be notified of config changes under the
    authkey list.
    """
    log = tm.log.Log(logging.getLogger(__name__))
    sub = tm.cdb.Subscriber(log=log, port=port, path=None)
    sub.register('/aaa:aaa/aaa:authentication/aaa:users/aaa:user/sa:authkey',
                 MyIter(log))
    sub.start()


if __name__ == '__main__':
    import sys
    logging.basicConfig(level=logging.INFO, filename='logs/ssh-authkey.log',
                        format='%(asctime)s %(levelname)-8s %(message)s')
    main(int(sys.argv[1]))
