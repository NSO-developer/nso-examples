#!/usr/bin/env python3
"""
Event notification subscriber example application
"""
import argparse
import datetime
import select
import socket
import sys
import ncs
from ncs.maapi import Maapi
import _ncs
import _ncs.events as events


def print_syslog(event_name, syslog_dict):
    logno = syslog_dict['logno']
    prio = syslog_dict['prio']
    msg = syslog_dict['msg']
    print(f'{event_name}: logno={logno},prio={prio},msg={msg}')


def get_datastore_name(ds_num):
    if ds_num == ncs.NO_DB:
        ds = "no_db"
    elif ds_num == ncs.RUNNING:
        ds = "running"
    elif ds_num == ncs.OPERATIONAL:
        ds = "operational"
    elif ds_num == ncs.TRANSACTION:
        ds = "trans_in_trans"
    elif ds_num == ncs.PRE_COMMIT_RUNNING:
        ds = "pre_commit_running"
    elif ds_num == ncs.INTENDED:
        ds = "intended"
    else:
        ds = f'UNKNOWN DATASTORE:{ds_num}'
    return ds


INDENT_SIZE = 2
INDENT_STR = " "


def get_stream_value(tv, indent, current_path, root_ns):
    """
    NSO Tag Value to XML string
    """
    text = ""
    val_type = tv.v.confd_type()
    ns = _ncs.hash2str(tv.ns)
    tag = str(tv)
    prefix = _ncs.ns2prefix(tv.ns)

    # start a container/list entry creation/modification
    if val_type == ncs.C_XMLBEGIN:
        text += "{}<{} xmlns=\"{}\">\n".format(INDENT_STR * indent, tag, ns)
        indent += INDENT_SIZE
        current_path += f'/{prefix}:{tag}'
    # exit from a processing of container/list entry creation/modification
    elif val_type == ncs.C_XMLEND:
        indent -= INDENT_SIZE
        text += "{}</{}>\n".format(INDENT_STR * indent, tag)
        if '/' in current_path:
            last_slash = current_path.rindex('/')
            current_path = current_path[:last_slash]
        else:
            current_path = ""
    # deletion of a leaf
    elif val_type == ncs.C_NOEXISTS:
        # we do netconf delete (not remove) operations below as remove will be
        # silently ignored if the data to remove does not exist. We want to
        # know if there are errors
        text += "{}<{} nc:operation=\"delete\">\n".format(INDENT_STR * indent,
                                                          tag)
    # deletion of a list entry / container
    elif val_type == ncs.C_XMLBEGINDEL:
        text += "{}<{} nc:operation=\"delete\">\n".format(INDENT_STR * indent,
                                                          tag)
        indent += INDENT_SIZE
        current_path += f'/{prefix}:{tag}'
    # type empty leaf creation
    elif val_type == ncs.C_XMLTAG:
        text += "{}<{}/>\n".format(INDENT_STR * indent, tag)
    # linked list creation/modification
    elif val_type == ncs.C_LIST:
        path = f'{current_path}/{prefix}:{tag}'
        ll_chars = tv.v.val2str((root_ns, path))
        val_strs = ''.join(ll_chars).split()
        for val_str in val_strs:
            text += "{}<{}>{}</{}>\n".format(INDENT_STR * indent, tag,
                                             val_str, tag)
    # regular leaf creation/modification
    else:
        path = f'{current_path}/{prefix}:{tag}'
        text += "{}<{}>{}</{}>\n".format(INDENT_STR * indent, tag,
                                         tv.v.val2str((root_ns, path)), tag)
    return text, indent, current_path


def dt_to_str(ts):
    """
    timestamp to datetime string
    """
    micro_s = '{}'.format(ts.micro/1000000.0)[2:]
    ts_str = '%.4i-%.2i-%.2iT%.2i:%.2i:%.2i.%s+00:00' % (
        ts.year, ts.month, ts.day, ts.hour, ts.min, ts.sec, micro_s)
    return datetime.datetime.fromisoformat(ts_str).astimezone()  # local tz


def process_event(port, event_sock, mask, confirm_sync=False):
    """
    Print event notifications and sync if needed.
    """
    try:
        event_dict = events.read_notification(event_sock)
        event_type = event_dict['type']
        if event_type & events.NOTIF_AUDIT:
            audit_dict = event_dict['audit']
            logno = audit_dict['logno']
            user = audit_dict['user']
            usid = audit_dict['usid']
            msg = audit_dict['msg']
            print(f'audit: logno={logno},user={user},usid={usid}'
                  f',msg={msg}')
            if mask & events.NOTIF_AUDIT_SYNC:
                if confirm_sync:
                    input("audit_sync: press ENTER to sync")
                events.sync_audit_notification(event_sock, audit_dict['usid'])
        elif event_type & events.NOTIF_DAEMON:
            print_syslog("daemon", event_dict['syslog'])
        elif event_type & events.NOTIF_NETCONF:
            print_syslog("netconf", event_dict['syslog'])
        elif event_type & events.NOTIF_DEVEL:
            print_syslog("devel", event_dict['syslog'])
        elif event_type & events.NOTIF_JSONRPC:
            print_syslog("jsonrpc", event_dict['syslog'])
        elif event_type & events.NOTIF_WEBUI:
            print_syslog("webui", event_dict['syslog'])
        elif event_type & events.NOTIF_WEBUI:
            print_syslog("takeover_syslog:", event_dict['syslog'])
        elif event_type & events.NOTIF_SNMPA:
            snmpa_dict = event_dict['snmpa']
            pdu_type = snmpa_dict['pdu_type']
            request_id = snmpa_dict['request_id']
            af = snmpa_dict['af']
            if af == socket.AF_INET:
                ipaddr = snmpa_dict['ip4']
            else:
                ipaddr = snmpa_dict['ip6']
            port = snmpa_dict['port']
            print(f'snmpa: pdu_type={pdu_type},request_id={request_id}'
                  f',addr={ipaddr}:{port}')
        elif event_type & events.NOTIF_USER_SESSION:
            user_sess_dict = event_dict['user_sess']
            stype = user_sess_dict['type']
            uinfo = user_sess_dict['uinfo']
            user = uinfo.username
            usid = uinfo.usid
            db_num = user_sess_dict['database']
            db = get_datastore_name(db_num)
            print(f'user_sess: type={stype},user={user},usid={usid},db={db}')
        elif event_type & events.NOTIF_COMMIT_DIFF:
            print("commit_diff:")
            commit_diff_dict = event_dict['commit_diff']
            tctx = commit_diff_dict['tctx']
            if 'comment' in commit_diff_dict:
                print(f'comment: {commit_diff_dict["comment"]}')
            if 'label' in commit_diff_dict:
                print(f'label: {commit_diff_dict["label"]}')

            def myiter(kp, op, oldv, newv):
                print(f'    ITER kp={kp},op={op},oldv={oldv},newv={newv}')

            m = ncs.maapi.Maapi(port=port)
            t = m.attach(tctx)
            t.diff_iterate(myiter, 0)

            events.diff_notification_done(event_sock, tctx)
        elif event_type & events.NOTIF_COMMIT_FAILED:
            cfail_dict = event_dict['cfail']
            provider = cfail_dict['provider']
            db_num = cfail_dict['dbname']
            db = get_datastore_name(db_num)
            if provider == events.DP_CDB:
                print(f'commit_failed: provider=CDB,db={db}')
            elif provider == events.DP_NETCONF:
                af = cfail_dict['af']
                if af == socket.AF_INET:
                    ipaddr = cfail_dict['ip4']
                else:
                    ipaddr = cfail_dict['ip6']
                port = cfail_dict['port']
                print(f'commit_failed: provider=NETCONF,db={db}'
                      f' addr={ipaddr}:{port}')
            elif provider == events.DP_EXTERNAL:
                dn = cfail_dict['daemon_name']
                print(f'commit_failed: provider=EXTERNAL,db={db}'
                      f' daemon_name={dn}')
            elif provider == events.DP_SNMPGW:
                print(f'commit_failed: provider=SNMPGW,db={db}')
            elif provider == events.DP_JAVASCRIPT:
                print(f'commit_failed: provider=JAVASCRIPT,db={db}')
            else:
                print(f'commit_failed: UNKNOWN PROVIDER:{provider},db={db}')
        elif (event_type & events.NOTIF_COMMIT_PROGRESS or
              event_type & events.NOTIF_PROGRESS):
            prog_dict = event_dict['progress']
            ptype_num = prog_dict['type']
            if ptype_num == _ncs.PROGRESS_START:
                ptype = "start"
            elif ptype_num == ncs.PROGRESS_STOP:
                ptype = "stop"
            elif ptype_num == _ncs.PROGRESS_INFO:
                ptype = "info"
            else:
                ptype = f'UNKNOWN TYPE:{ptype}'
            timestamp = prog_dict['timestamp']
            dt = datetime.datetime.fromtimestamp(timestamp/1000000)
            dur = ""
            if ptype == ncs.PROGRESS_STOP:
                tmp = prog_dict['duration']
                dur = f',duration={tmp}'
            trid = ""
            if "trace_id" in prog_dict:
                tmp = prog_dict['trace_id']
                trid = f',trace_id={tmp}'
            sid = prog_dict['span_id']
            psid = ""
            if "parent_span_id" in prog_dict:
                tmp = prog_dict['parent_span_id']
                psid = f',parent_span_id={tmp}'
            usid = prog_dict['usid']
            tid = prog_dict['tid']
            ds_num = prog_dict['datastore']
            ds = get_datastore_name(ds_num)
            ctx = ""
            if "context" in prog_dict:
                tmp = prog_dict['context']
                ctx = f',context={tmp}'
            subsys = ""
            if "subsystem" in prog_dict:
                tmp = prog_dict['subsystem']
                subsys = f',subsystem={tmp}'
            msg = ""
            if "msg" in prog_dict:
                tmp = prog_dict['msg']
                msg = f',msg={tmp}'
            annot = ""
            if "annotation" in prog_dict:
                tmp = prog_dict['annotation']
                annot = f',annotation={tmp}'
            attrs = ""
            if "num_attributes" in prog_dict:
                nattrs = prog_dict['num_attributes']
                for x in range(nattrs):
                    astr = None
                    anum = None
                    aname = prog_dict['attributes'][x]['name']
                    atype = prog_dict['attributes'][x]['type']
                    if atype == events.PROGRESS_ATTRIBUTE_STRING:
                        astr = prog_dict['attributes'][x]['string']
                        attr = f'{aname}:"{astr}"'
                    else:
                        anum = prog_dict['attributes'][x]['number']
                        attr = f'{aname}:{anum}'
                    attrs += f',attr[{x}]={attr}'
            links = ""
            if "num_links" in prog_dict:
                nlinks = prog_dict['num_links']
                for x in range(nlinks):
                    atid = prog_dict['links'][x]['trace_id']
                    asid = prog_dict['links'][x]['span_id']
                    links += f',link[x]={atid}:{asid}'
            print(f'progress: event_type={ptype},timestamp={dt}{dur}{trid}'
                  f',span_id={sid}{psid},session_id={usid}'
                  f',transaction_id={tid},datastore={ds}{ctx}{subsys}'
                  f'{msg}{annot}{attrs}{links}')
        elif event_type & events.NOTIF_HA_INFO:
            ha_info_dict = event_dict['hnot']
            htype = ha_info_dict['type']
            if htype == events.HA_INFO_NOPRIMARY:
                noprimary = ha_info_dict['noprimary']
                print(f'ha_info: noprimary={noprimary}')
                if noprimary == ncs.ERR_HA_NOTICK:
                    print('ha_info: noprimary=notick')
                elif noprimary == ncs.ERR_HA_CLOSED:
                    print('ha_info: noprimary=closed')
                else:
                    print(f'ha_info: UNKNOWN NOPRIMARY REASON:{noprimary}')
            elif htype == events.HA_INFO_SECONDARY_DIED:
                dst_dict = ha_info_dict['secondary_died']
                nodeid = dst_dict['nodeid']
                print(f'ha_info: secondary {nodeid} died')
            elif htype == events.HA_INFO_SECONDARY_ARRIVED:
                dst_dict = ha_info_dict['secondary_arrived']
                nodeid = dst_dict['nodeid']
                print(f'ha_info: secondary {nodeid} arrived')
            elif htype == events.HA_INFO_SECONDARY_INITIALIZED:
                cibc = ha_info_dict['cdb_initialized_by_copy']
                print(f'ha_info: secondary is initialized (cdbcopy={cibc})')
            elif htype == events.HA_INFO_IS_PRIMARY:
                print('ha_info: this node is now primary')
            elif htype == events.HA_INFO_IS_NONE:
                print('ha_info: this node is now "none"')
            elif htype == events.HA_INFO_BESECONDARY_RESULT:
                bres = ha_info_dict['besecondary_result']
                if bres == 0:
                    print('ha_info: async besecondary() succeeded')
                else:
                    print(f'ha_info: sync besecondary() failed'
                          f', ncs_errno={bres}')
            else:
                print(f'ha_info: UNKNOWN TYPE:{htype}')
            if mask & events.NOTIF_HA_INFO_SYNC:
                if confirm_sync:
                    input("ha_info_sync: press ENTER to sync")
                events.sync_ha_notification(event_sock)
                print("ha_info_sync: synched")
        elif event_type & events.NOTIF_UPGRADE_EVENT:
            upgrade_dict = event_dict['upgrade']
            event = upgrade_dict['event']
            if event == events.UPGRADE_INIT_STARTED:
                print("upgrade: init started")
            elif event == events.UPGRADE_INIT_SUCCEEDED:
                print("upgrade: init succeeded")
            elif event == events.UPGRADE_PERFORMED:
                print("upgrade: performed")
            elif event == events.UPGRADE_COMMITED:
                print("upgrade: committed")
            elif event == events.UPGRADE_ABORTED:
                print("upgrade: aborted")
            else:
                print(f'upgrade: UNKNOWN EVENT:{event_type}')
        elif event_type & events.NCS_NOTIF_PACKAGE_RELOAD:
            print("package_reload: completed")
        elif event_type & events.NCS_NOTIF_CQ_PROGRESS:
            cq_progress_dict = event_dict['cq_progress']
            cqp_type = cq_progress_dict['type']
            if cqp_type == events.NCS_CQ_ITEM_WAITING:
                type_name = "waiting"
            elif cqp_type == events.NCS_CQ_ITEM_EXECUTING:
                type_name = "executing"
            elif cqp_type == events.NCS_CQ_ITEM_LOCKED:
                type_name = "locked"
            elif cqp_type == events.NCS_CQ_ITEM_COMPLETED:
                type_name = "completed"
            elif cqp_type == events.NCS_CQ_ITEM_FAILED:
                type_name = "failed"
            elif cqp_type == events.NCS_CQ_ITEM_DELETED:
                type_name = "deleted"
            else:
                print(f'cq_progress: UNKNOWN TYPE:{cqp_type}')
                type_name = "unknown"
            ts = cq_progress_dict['timestamp']
            dt_str = dt_to_str(ts)
            cq_id = cq_progress_dict['cq_id']
            cq_tag = ""
            if "cq_tag" in cq_progress_dict:
                ct = cq_progress_dict['cq_tag']
                cq_tag = f',cq_tag={ct}'
            cd_msg = ""
            if "completed_devices" in cq_progress_dict:
                cd = cq_progress_dict['completed_devices']
                cd_msg = f',completed_devices={cd}'
            td_msg = ""
            tr_msg = ""
            if "transient_devices" in cq_progress_dict:
                td = cq_progress_dict['transient_devices']
                tr = cq_progress_dict['transient_reasons']
                td_msg = f',transient_devices={td}'
                tr_msg = f',transient_reasons={tr}'
            fd_msg = ""
            fr_msg = ""
            if "failed_devices" in cq_progress_dict:
                fd = cq_progress_dict['failed_devices']
                fr = cq_progress_dict['failed_reasons']
                fd_msg = f',failed_devices={fd}'
                fr_msg = f',failed_reasons={fr}'
            cs_msg = ""
            cscd_msg = ""
            if "completed_services" in cq_progress_dict:
                cs = cq_progress_dict['completed_services']
                cscd = cq_progress_dict['completed_services_completed_devices']
                cs_msg = f',completed_services={cs}'
                cscd_msg = f',completed_services_completed_devices={cscd}'
            fs_msg = ""
            fscd_msg = ""
            fsfd_msg = ""
            if "failed_services" in cq_progress_dict:
                fs = cq_progress_dict['failed_services']
                fscd = cq_progress_dict['failed_services_completed_devices']
                fsfd = cq_progress_dict['failed_services_failed_devices']
                fs_msg = f',failed_services={fs}'
                fscd_msg = f',failed_services_completed_devices={fscd}'
                fsfd_msg = f',failed_services_failed_devices={fsfd}'
            print(f'cq_progress: type={type_name},timestamp={dt_str}'
                  f',cq_it={cq_id}{cq_tag}{cd_msg}{td_msg}{tr_msg}'
                  f'{fd_msg}{fr_msg}{cs_msg}{cscd_msg}{fs_msg}{fscd_msg}'
                  f'{fsfd_msg}')
        elif event_type & events.NOTIF_REOPEN_LOGS:
            print("reopen_logs: completed")
        elif event_type & events.NCS_NOTIF_CALL_HOME_INFO:
            call_home_dict = event_dict['call_home']
            chtype = call_home_dict['type']
            dev_msg = ""
            if chtype == events.CALL_HOME_DEVICE_CONNECTED:
                ch_msg = "device connected"
                dev = call_home_dict['device']
                dev_msg = f',device={dev}'
            elif chtype == events.CALL_HOME_UNKNOWN_DEVICE:
                ch_msg = "unknown device"
            elif chtype == events.CALL_HOME_DEVICE_DISCONNECTED:
                ch_msg = "device disconnected"
                dev = call_home_dict['device']
                dev_msg = f',device={dev}'
            else:
                ch_msg = f"UNKNOWN TYPE:{chtype}"
            af_msg = ""
            ipaddr_msg = ""
            port_msg = ""
            shk_msg = ""
            ska_msg = ""
            if "af" in call_home_dict:
                af = call_home_dict['af']
                af_msg = f',af={af}'
                if af == socket.AF_INET:
                    ipaddr = call_home_dict['ip4']
                else:
                    ipaddr = call_home_dict['ip6']
                ipaddr_msg = f',ip_address={ipaddr}'
                port = call_home_dict['port']
                port_msg = f',port={port}'
                shk = call_home_dict['ssh_host_key']
                shk_msg = f',ssh_host_key={shk}'
                ska = call_home_dict['ssh_key_alg']
                ska_msg = f',ssh_key_alg={ska}'
            print(f'call_home: type={ch_msg}{dev_msg}{af_msg}{ipaddr_msg}'
                  f'{port_msg}{shk_msg}{ska_msg}')
        elif event_type & events.NCS_NOTIF_AUDIT_NETWORK:
            audit_net_dict = event_dict['audit_network']
            usid = audit_net_dict['usid']
            tid = audit_net_dict['tid']
            usr = audit_net_dict['user']
            dev = audit_net_dict['device']
            tid = audit_net_dict['trace_id']
            cfg = audit_net_dict['config']
            print(f'audit_network: usid={usid},tid={tid},user={usr}'
                  f',device={dev},trace_id={tid},config={cfg}')
            if mask & events.NCS_NOTIF_AUDIT_NETWORK_SYNC:
                if confirm_sync:
                    input("audit_network__sync: press ENTER to sync")
                events.sync_audit_network_notification(event_sock,
                                                       audit_net_dict['usid'])
                print("audit_network_sync: synched")
        elif event_type & events.NOTIF_COMPACTION:
            compact_dict = event_dict['compaction']
            dbfile_num = compact_dict['dbfile']
            if dbfile_num == events.COMPACTION_A_CDB:
                dbfile = 'A.cdb'
            elif dbfile_num == events.COMPACTION_O_CDB:
                dbfile = 'O.cdb'
            elif dbfile_num == events.COMPACTION_S_CDB:
                dbfile = 'S.cdb'
            else:
                dbfile = f'UNKNOWN DBFILE:{dbfile}'
            ctype_num = compact_dict['type']
            if ctype_num == events.COMPACTION_AUTOMATIC:
                ctype = "automatic"
            elif ctype_num == events.COMPACTION_MANUAL:
                ctype = "manual"
            else:
                ctype = f'UNKNOWN TYPE:{ctype_num}'
            fss = compact_dict['fsize_start']
            fse = compact_dict['fsize_end']
            fsl = compact_dict['fsize_last']
            ts = compact_dict['time_start']
            dur = compact_dict['duration']
            nt = compact_dict['ntrans']
            print(f'compaction: dbfile={dbfile},ctype={ctype}'
                  f',fsize_start={fss},fsize_end={fse},fsize_last={fsl}'
                  f',time_start={ts},duration={dur},ntrans={nt}')
        elif event_type & events.NOTIF_HEARTBEAT:
            print('tick heartbeat')
        elif event_type & events.NOTIF_HEALTH_CHECK:
            print('tick health check')
        elif event_type & events.NOTIF_COMMIT_SIMPLE:
            commit_dict = event_dict['commit']
            db_num = commit_dict['database']
            db = get_datastore_name(db_num)
            da = commit_dict['diff_available']
            uinfo = commit_dict['uinfo']
            user = uinfo.username
            usid = uinfo.usid
            # flags always 0 for NSO
            print(f'commit_simple: database={db},diff_available={da}'
                  f',user={user},usid={usid}')
        elif event_type & events.NOTIF_STREAM_EVENT:
            stream_dict = event_dict['stream']
            stype_num = stream_dict['type']
            et_msg = ""
            xml_msg = ""
            re_msg = ""
            if stype_num == events.STREAM_NOTIFICATION_EVENT:
                stype = "notification event"
                event_time = stream_dict['event_time']
                et_str = dt_to_str(event_time)
                et_msg = f',event-time={et_str}'
                tvs = stream_dict['values']
                root_ns = _ncs.hash2str(tvs[0].ns)
                xml_msg = ",values=\n"
                indent = 0
                current_path = ""
                for tag_val in tvs:
                    (text, indent, current_path) = get_stream_value(
                        tag_val, indent, current_path, root_ns)
                    xml_msg += text
            elif stype_num == events.STREAM_NOTIFICATION_COMPLETE:
                stype = "notification complete"
            elif stype_num == events.STREAM_REPLAY_COMPLETE:
                stype = "replay complete"
            elif stype_num == events.STREAM_REPLAY_FAILED:
                stype = "replay failed"
                re = stream_dict['replay_error']
                re_msg = f'replay_error={re}'
            else:
                stype = f'UNKNOWN STREAM EVENT TYPE:{stype_num}'
            print(f'stream_event: type={stype}{et_msg}{xml_msg}{re_msg}')
        else:
            print(f'UNKNOWN EVENT TYPE:{event_type}')
    except (_ncs.error.Error) as external_e:
        if external_e.ncs_errno is ncs.ERR_EXTERNAL:
            print("csocket> " + str(external_e))
        else:
            raise external_e


def loop(port, event_sock, mask, non_interactive=False, confirm_sync=False):
    """
    Waiting for events
    """
    if non_interactive:
        while True:
            (readables, _, _) = select.select([event_sock], [], [])
            for readable in readables:
                if readable == event_sock:
                    process_event(port, event_sock, mask)
    else:
        while True:
            (readables, _, _) = select.select([event_sock, sys.stdin], [], [])
            for readable in readables:
                if readable == event_sock:
                    process_event(port, event_sock, mask, confirm_sync)
                if readable == sys.stdin:
                    user_input = sys.stdin.readline().rstrip()
                    if user_input == "exit":
                        print("Bye!")
                        return False


def run(args):
    """
    Parse arguments and register for notifications
    """
    mask = 0

    if args.daemon:
        mask |= events.NOTIF_DAEMON
    if args.devel:
        mask |= events.NOTIF_DEVEL
    if args.audit:
        mask |= events.NOTIF_AUDIT
    if args.netconf:
        mask |= events.NOTIF_NETCONF
    if args.jsonrpc:
        mask |= events.NOTIF_JSONRPC
    if args.webui:
        mask |= events.NOTIF_WEBUI
    if args.takeover_syslog:
        mask |= events.NOTIF_TAKEOVER_SYSLOG
    if args.snmpa:
        mask |= events.NOTIF_SNMPA
    if args.user_session:
        mask |= events.NOTIF_USER_SESSION
    if args.commit_diff:
        mask |= events.NOTIF_COMMIT_DIFF
    if args.commit_failed:
        mask |= events.NOTIF_COMMIT_FAILED
    if args.commit_progress:
        mask |= events.NOTIF_COMMIT_PROGRESS
    if args.progress:
        mask |= events.NOTIF_PROGRESS
    if args.ha_info:
        mask |= events.NOTIF_HA_INFO
    if args.upgrade:
        mask |= events.NOTIF_UPGRADE_EVENT
    if args.package_reload:
        mask |= events.NCS_NOTIF_PACKAGE_RELOAD
    if args.cq_progress:
        mask |= events.NCS_NOTIF_CQ_PROGRESS
    if args.reopen_logs:
        mask |= events.NOTIF_REOPEN_LOGS
    if args.call_home:
        mask |= events.NCS_NOTIF_CALL_HOME_INFO
    if args.audit_network:
        mask |= events.NCS_NOTIF_AUDIT_NETWORK
    if args.compaction:
        mask |= events.NOTIF_COMPACTION
    if args.all:
        mask = (events.NOTIF_DAEMON |
                events.NOTIF_DEVEL |
                events.NOTIF_AUDIT |
                events.NOTIF_NETCONF |
                events.NOTIF_JSONRPC |
                events.NOTIF_WEBUI |
                events.NOTIF_TAKEOVER_SYSLOG |
                events.NOTIF_SNMPA |
                events.NOTIF_USER_SESSION |
                events.NOTIF_COMMIT_DIFF |
                events.NOTIF_COMMIT_FAILED |
                events.NOTIF_COMMIT_PROGRESS |
                events.NOTIF_PROGRESS |
                events.NOTIF_HA_INFO |
                events.NOTIF_UPGRADE_EVENT |
                events.NCS_NOTIF_PACKAGE_RELOAD |
                events.NCS_NOTIF_CQ_PROGRESS |
                events.NOTIF_REOPEN_LOGS |
                events.NCS_NOTIF_CALL_HOME_INFO |
                events.NCS_NOTIF_AUDIT_NETWORK |
                events.NOTIF_COMPACTION)
    if args.heartbeat:
        mask |= events.NOTIF_HEARTBEAT
    if args.health_check:
        mask |= events.NOTIF_HEALTH_CHECK
    if args.commit_simple:
        mask |= events.NOTIF_COMMIT_SIMPLE
    if args.stream != "whatever":
        mask |= events.NOTIF_STREAM_EVENT
    if args.audit_sync:
        mask |= events.NOTIF_AUDIT_SYNC
    if args.audit_network_sync:
        mask |= events.NCS_NOTIF_AUDIT_NETWORK_SYNC
    if args.ha_info_sync:
        mask |= events.NOTIF_HA_INFO_SYNC

    if args.progress_verbosity == "verbose":
        progress_verbosity = _ncs.VERBOSITY_VERBOSE
    elif args.progress_verbosity == "very_verbose":
        progress_verbosity = _ncs.VERBOSITY_VERY_VERBOSE
    elif args.progress_verbosity == "debug":
        progress_verbosity = _ncs.VERBOSITY_DEBUG
    else:
        progress_verbosity = _ncs.VERBOSITY_NORMAL

    if args.start_time:
        start_time = _ncs.Value(init=args.start_time, type=ncs.C_DATETIME)
    else:
        start_time = _ncs.Value(init=1, type=ncs.C_NOEXISTS)
    if args.stop_time:
        stop_time = _ncs.Value(init=args.stop_time, type=ncs.C_DATETIME)
    else:
        stop_time = _ncs.Value(init=1, type=ncs.C_NOEXISTS)

    if args.user_id:
        data = events.NotificationsData(heartbeat_interval=args.interval,
                                        health_check_interval=args.interval,
                                        stream_name=args.stream,
                                        start_time=start_time,
                                        stop_time=stop_time,
                                        xpath_filter=args.xpath_filter,
                                        usid=args.user_id,
                                        verbosity=progress_verbosity)
    else:
        data = events.NotificationsData(heartbeat_interval=args.interval,
                                        health_check_interval=args.interval,
                                        stream_name=args.stream,
                                        start_time=start_time,
                                        stop_time=stop_time,
                                        xpath_filter=args.xpath_filter,
                                        verbosity=progress_verbosity)

    event_sock = socket.socket()
    events.notifications_connect2(sock=event_sock,
                                  mask=mask,
                                  ip=args.address,
                                  port=args.port,
                                  data=data)

    if args.non_interactive:
        loop(args.port, event_sock, mask, args.non_interactive)
    else:
        print('Connected. Type "exit" to exit')
        loop(args.port, event_sock, mask, confirm_sync=args.confirm_sync)
    event_sock.close()


if __name__ == "__main__":
    """
    Define arguments
    """
    noexists = _ncs.Value(init=1, type=_ncs.C_NOEXISTS)
    parser = argparse.ArgumentParser(
        description="A simple NSO event notification receiver",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument('-d', '--daemon', action='store_true',
                        help='Daemon log events')
    parser.add_argument('-D', '--devel', action='store_true',
                        help='Developer log events')
    parser.add_argument('-a', '--audit', action='store_true',
                        help='Audit log events')
    parser.add_argument('-N', '--netconf', action='store_true',
                        help='NETCONF log events')
    parser.add_argument('-J', '--jsonrpc', action='store_true',
                        help='JSON-RPC log events')
    parser.add_argument('-W', '--webui', action='store_true',
                        help='WebUI log events')
    parser.add_argument('-t', '--takeover-syslog', action='store_true',
                        help='Stop NSO syslogging')
    parser.add_argument('-S', '--snmpa', action='store_true',
                        help='SNMP agent audit log events')
    parser.add_argument('-u', '--user-session', action='store_true',
                        help='User session events')
    parser.add_argument('-i', '--commit-diff', action='store_true',
                        help='Config change events must be synced to continue')
    parser.add_argument('-L', '--commit-failed', action='store_true',
                        help='Data provider commit callback failure events')
    parser.add_argument('-P', '--commit-progress', action='store_true',
                        help='Commit progress events')
    parser.add_argument('-g', '--progress', action='store_true',
                        help='Commit and action progress events')
    parser.add_argument('-v', '--progress-verbosity',
                        default='normal',
                        choices=['normal', 'verbose', 'very_verbose', 'debug'],
                        help='Verbosity for progress events')
    parser.add_argument('-F', '--ha-info', action='store_true',
                        help='High availability information events')
    parser.add_argument('-U', '--upgrade', action='store_true',
                        help='Upgrade events')
    parser.add_argument('-R', '--package-reload', action='store_true',
                        help='Package reload complete events')
    parser.add_argument('-q', '--cq-progress', action='store_true',
                        help='Commit queue item events')
    parser.add_argument('-l', '--reopen-logs', action='store_true',
                        help='Close and reopen log files events')
    parser.add_argument('-O', '--call-home', action='store_true',
                        help='Call home connection events')
    parser.add_argument('-w', '--audit-network', action='store_true',
                        help='Audit network events')
    parser.add_argument('-C', '--compaction', action='store_true',
                        help='CDB compaction events')
    parser.add_argument('-A', '--all', action='store_true',
                        help='All events above')
    parser.add_argument('-H', '--heartbeat', action='store_true',
                        help='Heartbeat events')
    parser.add_argument('-e', '--health-check', action='store_true',
                        help='Health check events')
    parser.add_argument('-c', '--commit-simple', action='store_true',
                        help='Configuration change events')
    parser.add_argument('-s', '--stream', default='whatever',
                        help='Notification NAME stream events')
    parser.add_argument('-y', '--audit-sync', action='store_true',
                        help='Audit notifications must be synced to continue')
    parser.add_argument('-k', '--audit-network-sync', action='store_true',
                        help='Audit network notifications must be synced to'
                             ' continue')
    parser.add_argument('-Y', '--ha-info-sync', action='store_true',
                        help='HA changes must be synced to continue')
    parser.add_argument('-r', '--confirm-sync', action='store_true',
                        help='Confirm HA and Audit sync')
    parser.add_argument('-T', '--interval', type=int, default=1000,
                        help='heartbeat health check interval. Default 1000ms')
    parser.add_argument('-B', '--start-time', default=None,
                        help='Notification stream start time'
                             ' - yang:date-and-time format')
    parser.add_argument('-E', '--stop-time', default=None,
                        help='Notification stream stop time'
                             ' - yang:date-and-time format')
    parser.add_argument('-x', '--xpath-filter', default="/",
                        help='XPath filter')
    parser.add_argument('-z', '--user-id', type=int, help='User ID')
    parser.add_argument('-I', '--address', default='127.0.0.1',
                        help='Connect to NSO at ADDRESS. Default: 127.0.0.1')
    parser.add_argument('-p', '--port', type=int, default=4569,
                        help='Connect to NSO at PORT. Default: 4569')
    parser.add_argument('-n', '--non-interactive', action='store_true',
                        help='No actions or input required from user')
    args = parser.parse_args()
    with Maapi(port=args.port):  # Load schemas
        pass
    run(args)
