package com.example.maapi;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfException;
import com.tailf.examples.router.namespaces.router;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuList;
import com.tailf.ncs.ns.Ncs;

/**
 * This example illustrates how entries in a list can be moved
 * if attribute ordered by user is set.
 *
 * USAGE of Dns Example
 *
 * ant dns -Darg="DEVICE ls"
 * ant dns -Darg="DEVICE [add | rm] DNS..."
 * ant dns -Darg="DEVICE mv DNS [first | last]"
 * ant dns -Darg="DEVICE mv DNS [before | after] DNS"
 *
 * DEVICE the name of one of the created devices ex0, ex1 or ex2
 * DNS    is an ip address to a dns server
 *
 * Example:
 * ant dns -Darg="ex2 ls"
 * ant dns -Darg="ex0 add 10.2.3.5 10.2.3.6"
 * ant dns -Darg="ex0 mv 10.2.3.5 first"
 * ant dns -Darg="ex0 mv 10.2.3.5 after 10.2.3.6"
 *
 */


public class Dns {
    public static void main(String[] argv)
            throws UnknownHostException, IOException, ConfException{

        String device = argv[0];
        String op = argv[1];
        String dns[] = null;
        String mv_op = null;
        String source = null;
        String dest = null;

        System.out.println("device: "+device);
        System.out.println("deviceop: "+op);

        SocketAddress address;
        String path = System.getenv("NCS_IPC_PATH");
        if (path == null) {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        } else {
            address = UnixDomainSocketAddress.of(path);
        }

        if (op.equals("ls")) {
            do_ls(address, device);

        } else if (op.equals("add")) {
            dns = getDns(argv ,2);
            do_add(address, device, dns);

        } else if (op.equals("rm")) {
            dns = getDns(argv ,2);
            do_rm(address, device, dns);

        } else if (op.equals("mv")) {
            if (argv.length < 4) {
                usage();
            }

            source = getOneDns(argv , 2);
            mv_op = argv[3];
            if (mv_op.equals("first") || mv_op.equals("last")) {
                do_mv(address, device, source, mv_op, null);

            } else if (mv_op.equals("before") || mv_op.equals("after")) {
                if (argv.length < 5) {
                    usage();
                }
                dest = getOneDns(argv , 4);
                do_mv(address, device, source, mv_op, dest);

            } else {
                usage();
            }
        } else {
            usage();
        }
    }

    static void usage(){
        String usage =
            "USAGE of Dns Example \n"+
            "\n"+
            "ant dns -Darg=\"DEVICE ls\"\n"+
            "ant dns -Darg=\"DEVICE [add | rm] DNS...\"\n"+
            "ant dns -Darg=\"DEVICE mv DNS [first | last]\"\n"+
            "ant dns -Darg=\"DEVICE mv DNS [before | after] DNS\"\n"+
            "\n"+
            "DEVICE the name of one of the created devices ex0, ex1 or ex2\n"+
            "DNS    is an ip address to a dns server\n"+
            "\n"+
            "Example:\n"+
            "ant dns -Darg=\"ex2 ls\"\n"+
            "ant dns -Darg=\"ex0 add 10.2.3.5 10.2.3.6\"\n"+
            "ant dns -Darg=\"ex0 mv 10.2.3.5 first\"\n"+
            "ant dns -Darg=\"ex0 mv 10.2.3.5 after 10.2.3.6\"\n";
        System.out.println(usage);
        System.exit(1);
    }

    static void do_ls(SocketAddress address, String device)
        throws  IOException, IOException, ConfException {
        NavuContainer ncs_root = getNcs(address);
        NavuList dns_list = getDnsList(ncs_root, device);
        boolean isFirst = true;

        for (NavuContainer dns : dns_list.elements()) {
            String name = dns.leaf(router._address_)
                    .value()
                    .toString();

            if (isFirst) {
                System.out.print(name);
                isFirst = false;
            } else {
                System.out.print(" "+name);
            }
        }
        System.out.println("");
    }

    static void do_rm(SocketAddress address, String device, String[] dns)
            throws  IOException, IOException, ConfException {
        NavuContainer ncs_root = getNcs(address);
        NavuList dns_list = getDnsList(ncs_root, device);

        for (String d : dns) {
            dns_list.delete(d);
        }

        int tid = ncs_root.context().getMaapiHandle();
        Maapi maapi = ncs_root.context().getMaapi();
        maapi.applyTrans(tid, false);
        maapi.finishTrans(tid);
    }

    static void do_add(SocketAddress address, String device, String[] dns)
            throws  IOException, IOException, ConfException {
        NavuContainer ncs_root = getNcs(address);
        NavuList dns_list = getDnsList(ncs_root, device);

        for (String d : dns) {
            dns_list.create(d);
        }

        int tid = ncs_root.context().getMaapiHandle();
        Maapi maapi = ncs_root.context().getMaapi();
        maapi.applyTrans(tid, false);
        maapi.finishTrans(tid);
    }

    static void do_mv(SocketAddress address, String device, String source,
            String mv_op, String dest)
            throws  IOException, IOException, ConfException {
        NavuContainer ncs_root = getNcs(address);
        NavuList dns_list = getDnsList(ncs_root, device);

        if (mv_op.equals("first")) {
            dns_list.move(source, NavuList.WhereTo.FIRST, null);

        } else if(mv_op.equals("last")) {
            dns_list.move(source, NavuList.WhereTo.LAST, null);

        } else {

            if (mv_op.equals("before")) {
                dns_list.move(source, NavuList.WhereTo.BEFORE, dest);

            } else if(mv_op.equals("after")) {
                dns_list.move(source, NavuList.WhereTo.AFTER, dest);
            }
        }

        int tid = ncs_root.context().getMaapiHandle();
        Maapi maapi = ncs_root.context().getMaapi();
        maapi.applyTrans(tid, false);
        maapi.finishTrans(tid);
    }

    static String[] getDns(String[] argv, int start) {
        int length = argv.length-start;
        if (length == 0) {
            return null;
        }
        String[] dns = new String[length];

        for (int i = 0; i < length; ++i) {
            dns[i] = argv[start+i];
        }
        return dns;
    }

    static String getOneDns(String[] argv, int start) {
        int length = argv.length-start;
        if (length == 0) {
            return null;
        }
        String dns = argv[start];

        return dns;
    }

    // return the root container of the NCS YANG model
    static NavuContainer getNcs(SocketAddress address)
                throws UnknownHostException, IOException, ConfException {
        Maapi maapi = new Maapi(address);
        maapi.startUserSession("admin", "maapi", new String[] { "admin" });

        // Start a read transaction towards maapi
        int th   = maapi.startTrans(Conf.DB_RUNNING,
                                    Conf.MODE_READ_WRITE);

        // Create a container to serve as a place holder for all
        // loaded modules. This container has no representation
        // in the YANG model
        NavuContainer modules =
            new NavuContainer(new NavuContext(maapi,th));

        // Get desired top level module, either from hash or uri
        return modules.container(Ncs.uri);
    }

    static NavuList getDnsList(NavuContainer ncs_root, String device)
        throws  NavuException{
        NavuList dns_list = ncs_root
            .container(Ncs._devices_)
            .list(Ncs._device_)
            .elem(device)
            .container(Ncs._config_)
            .container(router._sys_)
            .container(router._dns_)
            .list(router._server_);
        return dns_list;
    }
}
