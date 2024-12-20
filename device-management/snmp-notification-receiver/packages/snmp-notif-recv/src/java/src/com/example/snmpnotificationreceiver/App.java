/*    -*- Java -*-
 *
 *  $Id$
 *
 */
package com.example.snmpnotificationreceiver;

import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.snmp.snmp4j.NotificationReceiver;

/**
 * This class starts the Snmp-notification-receiver.
 */
public class App implements ApplicationComponent {

    private NcsMain main;
    private ExampleHandler handl = null;
    private NotificationReceiver notifRec = null;

    public App(NcsMain main) {
        this.main = main;
    }

    public void run() {
        try {
            notifRec.start();
            synchronized (notifRec) {
                notifRec.wait();
            }
        } catch (Exception e) {
            this.main.handlePackageException(this, e);
        }
    }

    public void finish() throws Exception {
        if (notifRec == null) {
            return;
        }
        synchronized (notifRec) {
            notifRec.notifyAll();
        }
        notifRec.destroy();
    }

    public void init() throws Exception {
        handl = new ExampleHandler();
        notifRec = NotificationReceiver.getNotificationReceiver(
            this.main.getAddress());
        // register example filter
        notifRec.register(handl, null);
    }
}
