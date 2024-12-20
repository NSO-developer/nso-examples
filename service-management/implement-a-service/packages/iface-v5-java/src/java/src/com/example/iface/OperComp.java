package com.example.iface;

import com.example.iface.namespaces.*;
import java.util.List;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ApplicationComponent;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;
import com.tailf.maapi.*;
import java.net.*;
import java.util.Random;

public class OperComp implements ApplicationComponent {

    private final NcsMain main;

    public OperComp(NcsMain main) {
        this.main = main;
    }

    public void init() throws Exception {
        // Ensure socket gets closed on errors, also ending any ongoing
        // session and transaction
        try (Maapi maapi = new Maapi(main.getAddress())) {
            maapi.startUserSession("admin", "system");

            NavuContext context = new NavuContext(maapi);
            context.startOperationalTrans(Conf.MODE_READ_WRITE);

            NavuContainer root =
                new NavuContainer(context).container(iface.hash);

            Random rnd = new Random();
            for (NavuContainer service : root.list("iface")) {
                NavuLeaf statusLeaf = service.leaf("last-test-status");
                if (statusLeaf.value() == null) {
                    statusLeaf.set(rnd.nextInt(2) == 0 ? "up" : "down");
                }
            }

            context.applyClearTrans();
        }
    }

    public void run() {
    }

    public void finish() {
    }
}
