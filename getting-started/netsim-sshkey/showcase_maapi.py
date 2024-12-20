#!/usr/bin/env python3

"""NSO nano service example.

Python MAAPI demo script

See the README file for more information
"""
import subprocess
import os
import time
import select
import socket
import sys
from datetime import datetime, timezone
import ncs
import _ncs
from _ncs import events

dt_string = ''
HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'

NNES = int(sys.argv[1])


def handle_state_changes(dt_string, istate, ioperation, istatus):
    """Handle service state change stream events"""
    if _ncs.PATH is None:
        event_sock = socket.socket()
    else:
        event_sock = socket.socket(family=socket.AF_UNIX)
    mask = events.NOTIF_STREAM_EVENT
    noexists = _ncs.Value(1, _ncs.C_NOEXISTS)
    dt_value = _ncs.Value(dt_string, _ncs.C_DATETIME)
    notif_data = events.NotificationsData(heartbeat_interval=1000,
                                          health_check_interval=1000,
                                          stream_name="service-state-changes",
                                          start_time=dt_value,
                                          stop_time=noexists)
    events.notifications_connect2(sock=event_sock,
                                  mask=mask,
                                  ip='127.0.0.1',
                                  port=_ncs.NCS_PORT,
                                  path=_ncs.PATH,
                                  data=notif_data)
    ns_hash = _ncs.str2hash("http://tail-f.com/ns/ncs")
    nready = 0
    while True:
        (readables, _, _) = select.select([event_sock], [], [])
        for readable in readables:
            try:
                event_dict = events.read_notification(event_sock)
                state = operation = status = ''
                if 'values' in event_dict['stream']:
                    tag_values = event_dict['stream']['values']
                    tv_len = len(tag_values)
                    for j in range(0, tv_len):
                        tag_value = tag_values[j]
                        if str(tag_value) == 'state':
                            state = str(tag_value.v)
                        elif str(tag_value) == 'operation':
                            operation = tag_value.v.val2str((
                                ns_hash,
                                '/plan-state-change/operation'))
                        elif str(tag_value) == 'status':
                            status = tag_value.v.val2str((
                                ns_hash,
                                '/plan-state-change/status'))
                if state == istate and operation == ioperation and \
                   status == istatus:
                    nready += 1
            except (_ncs.error.Error) as external_e:
                if external_e.confd_errno is _ncs.ERR_EXTERNAL:
                    print("csocket> " + str(external_e))
                else:
                    raise external_e
        if nready == NNES:
            break


def maapi_save_config(trans, c_flags, path):
    """Use MAAPI to get data"""
    c_root = ncs.maagic.get_root(trans)
    maapi = ncs.maagic.get_maapi(c_root)
    c_id = _ncs.maapi.save_config(maapi.msock, trans.th, c_flags, path)
    c_sock = socket.socket(maapi.msock.family)
    peername = maapi.msock.getpeername()
    if type(peername) is tuple:
        (ncsip, ncsport) = peername
        _ncs.stream_connect(c_sock, c_id, 0, ncsip, ncsport)
    else:
        _ncs.stream_connect(c_sock, c_id, 0, path=peername)
    c_data = ''
    while True:
        buf = c_sock.recv(4096)
        if buf:
            c_data += buf.decode('utf-8')
        else:
            c_sock.close()
            break
    return c_data


print(f'\n{OKGREEN}##### Reset and setup the example\n{ENDC}')
subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
               encoding='utf-8')

print(f'\n{OKBLUE}##### Generate keys, distribute the public key and'
      f' configure NSO for public key authentication with {NNES} network'
      f' elements\n{ENDC}')
with ncs.maapi.single_write_trans('admin', 'showcase_context') as t:
    root = ncs.maagic.get_root(t)
    root.devices.sync_from.request()
    for i in range(0, NNES):
        root.pubkey_dist.key_auth.create(f'ex{i}', 'admin')
        root.pubkey_dist.key_auth[f'ex{i}', 'admin'].remote_name = 'admin'
        root.pubkey_dist.key_auth[f'ex{i}', 'admin'].authgroup_name = \
            f'ex{i}-admin'
        root.pubkey_dist.key_auth[f'ex{i}', 'admin'].passphrase = \
            'GThunberg18!'
    params = ncs.maapi.CommitParams()
    params.dry_run_xml()
    result = t.apply_params(True, params)
    print(result['local-node'])
    dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    t.apply_params(True, t.get_params())

print(f"\n{HEADER}##### Waiting for plan notifications for all created nano"
      f" service components to have reached the ready state...{ENDC}")
handle_state_changes(dt_string, 'ready', 'modified', 'reached')

print(f'\n{OKBLUE}###### Show the plan status\n{ENDC}')
with ncs.maapi.single_read_trans('admin', 'showcase_context',
                                 db=ncs.OPERATIONAL) as t:
    flags = (_ncs.maapi.CONFIG_XML_PRETTY +
             _ncs.maapi.CONFIG_XPATH +
             _ncs.maapi.CONFIG_OPER_ONLY)
    XPATH = '/pubkey-dist/key-auth/plan/component/state/' +\
            '*[self::post-action-status or self::status]'
    data = maapi_save_config(t, flags, XPATH)
    print(data)

print(f'\n{OKBLUE}###### Show the configuration added to NSO and network'
      f' elements\n{ENDC}')
with ncs.maapi.single_read_trans('admin', 'showcase_context') as t:
    flags = (_ncs.maapi.CONFIG_XML_PRETTY +
             _ncs.maapi.CONFIG_XPATH)
    XPATH = '/devices/authgroups/group/umap'
    data = maapi_save_config(t, flags, XPATH)
    print(data)
    XPATH = '/devices/device/authgroup'
    data = maapi_save_config(t, flags, XPATH)
    print(data)
    XPATH = '/devices/device/config/aaa/authentication/users/user/authkey'
    data = maapi_save_config(t, flags, XPATH)
    print(data)

print(f'\n{OKBLUE}###### The generated private and public keys\n{ENDC}')
for f_name in os.listdir('.'):
    if "ed25519" in f_name:
        print(f_name)

print(f'\n{OKBLUE}###### Delete the nano service (ctrl-c to abort) to go back'
      f' from public key to password base network element'
      f' authentication{ENDC}')
i = 5
while i > 0:
    try:
        print(f'{OKBLUE}{i}{ENDC}')
        time.sleep(1)
        i -= 1
    except KeyboardInterrupt:
        sys.exit()

with ncs.maapi.single_write_trans('admin', 'showcase_context') as t:
    root = ncs.maagic.get_root(t)
    root.pubkey_dist.delete()
    params = ncs.maapi.CommitParams()
    params.dry_run_xml()
    result = t.apply_params(True, params)
    print(result['local-node'])
    dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
    t.apply_params(True, t.get_params())

print(f"\n{HEADER}##### Waiting for plan notifications for all deleted nano"
      f" service components to have reached the init state...{ENDC}")
handle_state_changes(dt_string, 'init', 'deleted', '')

print(f'\n{OKBLUE}###### Show the restored configuration for password'
      f' authentication\n{ENDC}')
with ncs.maapi.single_read_trans('admin', 'showcase_context') as t:
    flags = (_ncs.maapi.CONFIG_XML_PRETTY +
             _ncs.maapi.CONFIG_XPATH)
    XPATH = '/devices/authgroups/group/umap'
    data = maapi_save_config(t, flags, XPATH)
    print(data)
    XPATH = '/devices/device/authgroup'
    data = maapi_save_config(t, flags, XPATH)
    print(data)
    XPATH = '/devices/device/config/aaa/authentication/users/user/authkey'
    data = maapi_save_config(t, flags, XPATH)
    print(data)

print(f"{OKGREEN}##### Done!{ENDC}")
