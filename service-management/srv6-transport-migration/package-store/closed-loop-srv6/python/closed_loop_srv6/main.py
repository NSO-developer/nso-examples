# -*- mode: python; python-indent: 4 -*-
from datetime import datetime, timezone

import ncs
from ncs.dp import Action


class TelemetryDiffIterator:
    def __init__(self, entries):
        self.entries = entries

    def __call__(self, keypath, op, oldv, newv):
        self.entries.append(f'{keypath} op={op} old={oldv} new={newv}')
        return ncs.ITER_RECURSE


def compact_summary(entries):
    if not entries:
        return 'telemetry notification received with no datastore diff'
    return '; '.join(entries[:8])[:900]


def locator_path(device, locator_name):
    return (
        f'/ncs:devices/device{{{device}}}/config/'
        f'cisco-ios-xr:segment-routing/srv6/locators/'
        f'locator{{{locator_name}}}'
    )


def locator_state_in_running(device, locator_name, expected_prefix):
    with ncs.maapi.single_read_trans('admin', 'closed-loop-srv6') as t:
        path = locator_path(device, locator_name)
        if not t.exists(path):
            return False, 'missing'

        prefix_path = f'{path}/prefix'
        try:
            current_prefix = str(ncs.maagic.get_node(t, prefix_path))
        except Exception:
            current_prefix = 'unknown'

        return current_prefix == expected_prefix, current_prefix


def prefix_from_diff(entries, device, locator_name):
    marker = (
        f'/ncs:devices/device{{{device}}}/config/'
        f'cisco-ios-xr:segment-routing/srv6/locators/'
        f'locator{{{locator_name}}}/prefix'
    )
    for entry in reversed(entries):
        if marker in entry and ' new=' in entry:
            return entry.rsplit(' new=', 1)[1]
    return None


def service_snapshot(kp):
    with ncs.maapi.single_read_trans('admin', 'closed-loop-srv6') as t:
        root = ncs.maagic.get_root(t)
        service = ncs.maagic.cd(root, kp)
        return {
            'name': str(service.name),
            'device': str(service.device),
            'locator_name': str(service.locator_name),
            'srv6_prefix': str(service.srv6_prefix),
            'auto_repair': str(service.auto_repair).lower() == 'true',
        }


def oper_snapshot(kp):
    with ncs.maapi.single_read_trans(
        'admin', 'closed-loop-srv6', db=ncs.OPERATIONAL
    ) as t:
        root = ncs.maagic.get_root(t)
        service = ncs.maagic.cd(root, kp)
        try:
            repair_status = str(service.repair_status)
        except Exception:
            repair_status = 'idle'
        try:
            repair_count = int(str(service.repair_count))
        except (TypeError, ValueError):
            repair_count = 0
        return {
            'repair_status': repair_status,
            'repair_count': repair_count,
        }


def timestamp():
    return datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')


def set_oper_state(kp, **updates):
    with ncs.maapi.single_write_trans(
        'admin', 'closed-loop-srv6', db=ncs.OPERATIONAL
    ) as write_trans:
        root = ncs.maagic.get_root(write_trans)
        oper_service = ncs.maagic.cd(root, kp)

        if updates.pop('increment_telemetry_events', False):
            try:
                oper_service.telemetry_events = (
                    int(str(oper_service.telemetry_events)) + 1
                )
            except (TypeError, ValueError):
                oper_service.telemetry_events = 1
        if updates.pop('increment_repair_count', False):
            try:
                repair_count = int(str(oper_service.repair_count))
                oper_service.repair_count = repair_count + 1
            except (TypeError, ValueError):
                oper_service.repair_count = 1

        for leaf, value in updates.items():
            if value is not None:
                setattr(oper_service, leaf, value)

        write_trans.apply()


def redeploy_service_with_oob_policy(kp):
    with ncs.maapi.single_read_trans('admin', 'closed-loop-srv6') as trans:
        root = ncs.maagic.get_root(trans)
        service = ncs.maagic.cd(root, kp)
        action_input = service.re_deploy.get_input()
        action_input['confirm-network-state'].create()
        service.re_deploy(action_input)
        return 'confirm-network-state re-deploy completed'


def reactive_redeploy_service(kp):
    with ncs.maapi.single_read_trans('admin', 'closed-loop-srv6') as trans:
        root = ncs.maagic.get_root(trans)
        service = ncs.maagic.cd(root, kp)
        service.reactive_re_deploy()


def operational_reason(service):
    return (
        f"SRv6 locator {service['locator_name']} on "
        f"{service['device']} has expected prefix "
        f"{service['srv6_prefix']}."
    )


def repair_drift(kp, service, reason, log):
    set_oper_state(
        kp,
        repair_status='reconciling',
        last_repair_update=timestamp(),
        last_repair_message=(
            f'Detected drift: {reason} Evaluating service out-of-band '
            'policy with confirm-network-state.'
        ),
    )

    set_oper_state(
        kp,
        repair_status='repairing',
        last_repair_update=timestamp(),
        last_repair_message=(
            'Re-deploying service intent with confirm-network-state so the '
            'service out-of-band policy can repair the device.'
        ),
    )
    redeploy_result = redeploy_service_with_oob_policy(kp)

    set_oper_state(
        kp,
        repair_status='verifying',
        last_repair_update=timestamp(),
        last_repair_message=(
            f'{redeploy_result}; verifying service-owned SRv6 locator state.'
        ),
    )
    locator_healthy, current_prefix = locator_state_in_running(
        service['device'], service['locator_name'], service['srv6_prefix']
    )

    if locator_healthy:
        message = (
            'Closed-loop repair completed through service out-of-band policy '
            f'and {redeploy_result}. Detected drift: {reason} '
            f'{operational_reason(service)}'
        )
        set_oper_state(
            kp,
            health_state='operational',
            health_reason=operational_reason(service),
            repair_status='repaired',
            last_repair_update=timestamp(),
            last_repair_message=message,
            increment_repair_count=True,
        )
        try:
            reactive_redeploy_service(kp)
        except Exception as exc:
            log.warning('Reactive re-deploy after repair failed: %s', exc)
        return True, message

    if current_prefix == 'missing':
        verify_reason = (
            f"SRv6 locator {service['locator_name']} is still missing on "
            f"{service['device']} after repair."
        )
    else:
        verify_reason = (
            f"SRv6 locator {service['locator_name']} on {service['device']} "
            f"still has prefix {current_prefix}, expected "
            f"{service['srv6_prefix']}."
        )
    set_oper_state(
        kp,
        health_state='degraded',
        health_reason=verify_reason,
        repair_status='failed',
        last_repair_update=timestamp(),
        last_repair_message=verify_reason,
    )
    return False, verify_reason


class RecordHealth(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        service = service_snapshot(kp)
        oper = oper_snapshot(kp)
        expected_subscription = f"closed-loop-srv6-{service['name']}"
        diff_entries = []

        with ncs.maapi.Maapi() as maapi:
            telemetry_trans = maapi.attach(input.tid)
            try:
                telemetry_trans.diff_iterate(
                    TelemetryDiffIterator(diff_entries),
                    ncs.ITER_WANT_ATTR
                )
            finally:
                maapi.detach(input.tid)

        matching_subscription = (
            str(input.subscription) == expected_subscription
        )
        matching_device = str(input.device) == service['device']
        if not (matching_subscription and matching_device):
            output.success = False
            output.health_state = 'unknown'
            output.message = (
                'Ignored telemetry event: expected '
                f"{service['device']}/{expected_subscription}, received "
                f'{input.device}/{input.subscription}.'
            )
            return

        current_prefix = prefix_from_diff(
            diff_entries, service['device'], service['locator_name']
        )
        if current_prefix is None:
            locator_healthy, current_prefix = locator_state_in_running(
                service['device'], service['locator_name'],
                service['srv6_prefix']
            )
        else:
            locator_healthy = current_prefix == service['srv6_prefix']
        health_state = 'operational' if locator_healthy else 'degraded'
        if locator_healthy:
            reason = operational_reason(service)
        elif current_prefix == 'missing':
            reason = (
                f"SRv6 locator {service['locator_name']} is missing on "
                f"{service['device']}."
            )
        else:
            reason = (
                f"SRv6 locator {service['locator_name']} on "
                f"{service['device']} has prefix {current_prefix}, expected "
                f"{service['srv6_prefix']}."
            )

        summary = compact_summary(diff_entries)
        set_oper_state(
            kp,
            health_state=health_state,
            health_reason=reason,
            last_telemetry_update=timestamp(),
            last_diff_summary=summary,
            increment_telemetry_events=True,
        )

        self.log.info(
            f"Closed-loop SRv6 health update: service={service['name']} "
            f"device={input.device} subscription={input.subscription} "
            f'health={health_state} diff-count={len(diff_entries)}'
        )
        if locator_healthy:
            try:
                reactive_redeploy_service(kp)
            except Exception as exc:
                self.log.warning(
                    'Reactive re-deploy after health update failed: %s',
                    exc,
                )
            output.success = True
            output.health_state = health_state
            output.message = reason
            return

        active_repair = oper['repair_status'] in (
            'detected', 'reconciling', 'repairing', 'verifying'
        )
        if service['auto_repair'] and not active_repair:
            set_oper_state(
                kp,
                repair_status='detected',
                last_repair_update=timestamp(),
                last_repair_message=f'Detected drift from telemetry: {reason}',
            )
            repair_success, repair_message = repair_drift(kp, service, reason,
                                                          self.log)
            output.success = repair_success
            output.health_state = (
                'operational' if repair_success else 'degraded'
            )
            output.message = repair_message
        else:
            output.success = True
            output.health_state = health_state
            output.message = reason


class VerifyHealth(Action):
    @Action.action
    def cb_action(self, uinfo, name, kp, input, output, trans):
        service = service_snapshot(kp)
        oper = oper_snapshot(kp)
        locator_healthy, current_prefix = locator_state_in_running(
            service['device'], service['locator_name'],
            service['srv6_prefix']
        )

        if locator_healthy:
            reason = operational_reason(service)
            set_oper_state(
                kp,
                health_state='operational',
                health_reason=reason,
                last_telemetry_update=timestamp(),
                last_diff_summary=(
                    'Explicit lab verification confirmed service-owned '
                    'SRv6 locator state in NSO device configuration.'
                ),
            )
            try:
                reactive_redeploy_service(kp)
            except Exception as exc:
                self.log.warning(
                    'Reactive re-deploy after health verification failed: %s',
                    exc,
                )
            output.success = True
            output.health_state = 'operational'
            output.message = reason
            return

        if current_prefix == 'missing':
            reason = (
                f"SRv6 locator {service['locator_name']} is missing on "
                f"{service['device']}."
            )
        else:
            reason = (
                f"SRv6 locator {service['locator_name']} on "
                f"{service['device']} has prefix {current_prefix}, expected "
                f"{service['srv6_prefix']}."
            )

        active_repair = oper['repair_status'] in (
            'detected', 'reconciling', 'repairing', 'verifying'
        )
        if service['auto_repair'] and not active_repair:
            repair_reason = f'Detected drift from verification: {reason}'
            set_oper_state(
                kp,
                health_state='degraded',
                health_reason=reason,
                repair_status='detected',
                last_repair_update=timestamp(),
                last_repair_message=repair_reason,
            )
            repair_success, repair_message = repair_drift(
                kp, service, reason, self.log
            )
            output.success = repair_success
            output.health_state = (
                'operational' if repair_success else 'degraded'
            )
            output.message = repair_message
            return

        set_oper_state(
            kp,
            health_state='degraded',
            health_reason=reason,
            last_telemetry_update=timestamp(),
            last_diff_summary=(
                'Explicit lab verification found service-owned SRv6 '
                'locator drift in NSO device configuration.'
            ),
        )
        output.success = True
        output.health_state = 'degraded'
        output.message = reason


class Main(ncs.application.Application):
    def setup(self):
        self.log.info('closed-loop-srv6 Main RUNNING')
        self.register_action('closed-loop-srv6-record-health', RecordHealth)
        self.register_action('closed-loop-srv6-verify-health', VerifyHealth)

    def teardown(self):
        self.log.info('closed-loop-srv6 Main FINISHED')
