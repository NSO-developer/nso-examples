#!/usr/bin/env python3

"""NSO NETCONF notification example.
Demo script

See the README file for more information
"""

import subprocess
import os
from datetime import datetime, timezone
import ncs
import _ncs
from ncs.maapi import Maapi

HEADER = '\033[95m'
OKBLUE = '\033[94m'
OKGREEN = '\033[92m'
ENDC = '\033[0m'
BOLD = '\033[1m'

print(f'\n{OKGREEN}##### Reset and setup the example\n{ENDC}')
subprocess.run(['make', 'stop', 'clean', 'all', 'start'], check=True,
               encoding='utf-8')

print(f'\n{OKGREEN}##### Send a "dummy-notif" notification over the'
      ' "dummy-notifications" stream (see ncs.conf) using NSO Data'
      ' Provider API and receive it from the NSO NETCONF northbound'
      f' interface{ENDC}')
with Maapi():  # Load schemas
    pass

print(f'\n{HEADER}##### Send a "dummy-notif" notification using the NSO DP API'
      f'{ENDC}')
d = ncs.dp.Daemon("dummy-notifier")
nctx = _ncs.dp.register_notification_stream(d.ctx(), None,
                                            ncs.dp.take_worker_socket(
                                                            d,
                                                            'dummynotif',
                                                            'dummynotif-key'),
                                            'dummy-notifications')
d.start()
now = datetime.now()
tmnow = ncs.DateTime(year=now.year,
                     month=now.month,
                     day=now.day,
                     hour=now.hour,
                     min=now.minute,
                     sec=now.second,
                     micro=now.microsecond,
                     timezone=0,
                     timezone_minutes=0)
cs = _ncs.cs_node_cd(None, "/dummy-notif")
ns = cs.ns()
tag = cs.tag()
scs = _ncs.cs_node_cd(None, "/dummy-notif/dummy-value")
sns = scs.ns()
stag = scs.tag()
tvs = [
        ncs.TagValue(xmltag=ncs.XmlTag(ns, tag),
                     v=ncs.Value((tag, ns), ncs.C_XMLBEGIN)),
        ncs.TagValue(xmltag=ncs.XmlTag(sns, stag),
                     v=ncs.Value(f"hello world {now}", ncs.C_BUF)),
        ncs.TagValue(xmltag=ncs.XmlTag(ns, tag),
                     v=ncs.Value((tag, ns), ncs.C_XMLEND))
    ]
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
_ncs.dp.notification_send(nctx, tmnow, tvs)
d.finish()

print(f'\n{HEADER}##### Waiting for the dummy notification {ENDC}')
proc = subprocess.Popen('exec netconf-console --create-subscription='
                        f'dummy-notifications --start-time={dt_string}',
                        shell=True,
                        stdout=subprocess.PIPE,
                        text=True)
do_print = False
while True:
    line = proc.stdout.readline()
    if '<notification' in line:
        do_print = True
    if do_print:
        print(f'{line}', end="")
    if '</notification>' in line:
        break
proc.kill()

print(f'\n{OKGREEN}##### Start a yang-push periodic subscription for device'
      f' configuration changes{ENDC}')
yp_proc = subprocess.Popen('exec netconf-console -i -v 1.1',
                           shell=True,
                           stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT,
                           stdin=subprocess.PIPE,
                           text=True)
cmd = '''<establish-subscription
  xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications"
  xmlns:yp="urn:ietf:params:xml:ns:yang:ietf-yang-push">
  <yp:datastore
    xmlns:ds="urn:ietf:params:xml:ns:yang:ietf-datastores">
    ds:running
  </yp:datastore>
  <yp:datastore-subtree-filter>
    <devices xmlns="http://tail-f.com/ns/ncs"/>
  </yp:datastore-subtree-filter>
  <yp:periodic>
    <yp:period>100</yp:period>
  </yp:periodic>
</establish-subscription>

'''
try:
    yp_proc.communicate(input=cmd, timeout=0.1)
except subprocess.TimeoutExpired:
    pass

print(f'\n{OKGREEN}##### Sync with the device and receive the NETCONF base'
      f' notification the config changes generates over the built-in'
      f' NETCONF stream when the device config is added to NSO CDB{ENDC}')
print(f'\n{HEADER}##### Sync with the c0 device{ENDC}')
proc = subprocess.Popen('exec netconf-console --rpc=-',
                        shell=True,
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True)
cmd = '''<action xmlns="http://tail-f.com/ns/netconf/actions/1.0"
          xmlns:ncs="http://tail-f.com/ns/ncs">
  <data><ncs:devices><ncs:sync-from/></ncs:devices></data>
</action>'''
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
reply, errs = proc.communicate(input=cmd)
if '<rpc-error>' in reply or errs != "":
    print(f'{reply}\n{errs}')
proc.kill()

print(f'\n{HEADER}##### Waiting for the NETCONF stream notification {ENDC}')
proc = subprocess.Popen('exec netconf-console --create-subscription=NETCONF'
                        f' --start-time={dt_string} --subtree-filter=-',
                        shell=True,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT,
                        stdin=subprocess.PIPE,
                        text=True
                        )
cmd = '''<netconf-config-change
 xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications"/>'''
try:
    proc.communicate(input=cmd, timeout=0.1)
except subprocess.TimeoutExpired:
    while True:
        line = proc.stdout.readline()
        print(f'{line}', end="")
        if '</netconf-config-change>' in line:
            print(f'{proc.stdout.readline()}')
            break
proc.kill()

print(f'\n{HEADER}##### Waiting for the YANG push notification for the config'
      f' change{ENDC}')
while True:
    line = yp_proc.stdout.readline()
    print(f'{line}', end="")
    if '</push-update>' in line:
        print(f'{yp_proc.stdout.readline()}')
        break
yp_proc.kill()

print(f'{OKGREEN}##### Receive notifications for the'
      f' "hardware state" stream from the "c0" device that implements'
      f' the ietf-hardware.yang model\n{ENDC}')
print(f'{HEADER}##### List the streams the device implements{ENDC}')
subprocess.run(['netconf-console',
                '--get',
                '-x',
                '/devices/device[name="c0"]/notifications/stream'],
               check=True,
               encoding='utf-8')

print(f'\n{HEADER}##### Subscribe to hardware_state stream notifications'
      f' sent from the device to NSO{ENDC}')
proc = subprocess.Popen('exec netconf-console --edit-config=-',
                        shell=True,
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True)
cmd = '''<devices xmlns="http://tail-f.com/ns/ncs">
  <device>
    <name>c0</name>
    <notifications>
      <subscription>
        <name>hw-state-changes</name>
        <stream>hardware_state</stream>
        <local-user>admin</local-user>
      </subscription>
    </notifications>
  </device>
</devices>'''
reply, errs = proc.communicate(input=cmd)
if '<rpc-error>' in reply or errs != "":
    print(f'{reply}\n{errs}')
proc.kill()

print(f'\n{HEADER}##### Insert a new card into the device chassis subrack slot'
      f' to trigger a hardware_state stream notification from the device to'
      f' NSO{ENDC}')
wd = os.path.dirname(os.path.realpath(__file__)) + "/netsim/c/c0"
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
proc = subprocess.Popen(['python3',
                         'card.py',
                         '-p', '5010',
                         '-c', 'clone',
                         '-a', '87',
                         '-n', '9985',
                         '-m', 'tail-f',
                         '-r', '1',
                         '-u', '4',
                         '-l', '2',
                         '-o', '2'],
                        cwd=wd)

print(f'\n{HEADER}##### Waiting for NSO to forward the device hardware_state'
      f' stream notification to the NSO device-notifications stream'
      f'{ENDC}')
proc = subprocess.Popen('exec netconf-console --create-subscription='
                        f'device-notifications --start-time={dt_string}',
                        shell=True,
                        stdout=subprocess.PIPE,
                        text=True)
do_print = False
while True:
    line = proc.stdout.readline()
    if '<notification' in line:
        do_print = True
    if do_print:
        print(f'{line}', end="")
    if '</notification>' in line:
        break
proc.kill()

print(f'\n{OKGREEN}##### Start a yang-push on-change subscription for device'
      f' configuration changes\n{ENDC}')
yp_proc = subprocess.Popen('exec netconf-console -i -v 1.1',
                           shell=True,
                           stdout=subprocess.PIPE,
                           stderr=subprocess.STDOUT,
                           stdin=subprocess.PIPE,
                           text=True)
cmd = '''<establish-subscription
  xmlns="urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications"
  xmlns:yp="urn:ietf:params:xml:ns:yang:ietf-yang-push">
  <yp:datastore
    xmlns:ds="urn:ietf:params:xml:ns:yang:ietf-datastores">
    ds:running
  </yp:datastore>
  <yp:datastore-xpath-filter
    xmlns:ncs="http://tail-f.com/ns/ncs">
    /ncs:devices
  </yp:datastore-xpath-filter>
  <yp:on-change>
    <yp:dampening-period>0</yp:dampening-period>
    <yp:sync-on-start>false</yp:sync-on-start>
  </yp:on-change>
</establish-subscription>

'''
try:
    yp_proc.communicate(input=cmd, timeout=0.1)
except subprocess.TimeoutExpired:
    pass

print(f'{OKGREEN}##### Configure the new card and receive the resulting'
      f' notifications on the NETCONF and device-notifications streams'
      f' and yang-push\n{ENDC}')
dt_string = datetime.now(timezone.utc).replace(tzinfo=None).isoformat()
proc = subprocess.Popen('exec netconf-console --edit-config=-',
                        shell=True,
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE,
                        text=True)
cmd = '''<devices xmlns="http://tail-f.com/ns/ncs">
  <device>
    <name>c0</name>
    <config>
      <hardware xmlns="urn:ietf:params:xml:ns:yang:ietf-hardware">
        <component>
          <name>hydrogen</name>
          <class xmlns:ih="urn:ietf:params:xml:ns:yang:iana-hardware">
            ih:module
          </class>
          <parent>slot-1-4-2</parent>
          <parent-rel-pos>1040200</parent-rel-pos>
          <alias>clone</alias>
          <asset-id>clone</asset-id>
          <uri>urn:clone</uri>
        </component>
      </hardware>
    </config>
  </device>
</devices>'''
reply, errs = proc.communicate(input=cmd)
if '<rpc-error>' in reply or errs != "":
    print(f'{reply}\n{errs}')
proc.kill()

print(f'{HEADER}##### Waiting for the NETCONF stream notification {ENDC}')
proc = subprocess.Popen('exec netconf-console --establish-subscription=NETCONF'
                        f' --replay-start-time={dt_string} --subtree-filter=-',
                        shell=True,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT,
                        stdin=subprocess.PIPE,
                        text=True
                        )
cmd = '''<netconf-config-change
 xmlns="urn:ietf:params:xml:ns:yang:ietf-netconf-notifications"/>'''
try:
    proc.communicate(input=cmd, timeout=0.1)
except subprocess.TimeoutExpired:
    while True:
        line = proc.stdout.readline()
        print(f'{line}', end="")
        if '</netconf-config-change>' in line:
            print(f'{proc.stdout.readline()}')
            break
proc.kill()

print(f'{HEADER}##### Waiting for NSO to forward the device hardware_state'
      f' stream notification to the NSO device-notifications stream'
      f'{ENDC}')
proc = subprocess.Popen('exec netconf-console --create-subscription='
                        f'device-notifications --start-time={dt_string}',
                        shell=True,
                        stdout=subprocess.PIPE,
                        text=True)
do_print = False
while True:
    line = proc.stdout.readline()
    if '<notification' in line:
        do_print = True
    if do_print:
        print(f'{line}', end="")
    if '</notification>' in line:
        break
proc.kill()

print(f'\n{HEADER}##### Waiting for the YANG push notification for the config'
      f' change{ENDC}')
while True:
    line = yp_proc.stdout.readline()
    print(f'{line}', end="")
    if '</push-change-update>' in line:
        print(f'{yp_proc.stdout.readline()}')
        break
yp_proc.kill()

print(f'{OKGREEN}##### See logs/netconf.trace for NETCONF details{ENDC}')
print(f'\n{OKGREEN}##### Done!\n{ENDC}')
