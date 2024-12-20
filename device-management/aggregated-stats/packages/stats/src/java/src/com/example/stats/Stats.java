package com.example.stats;


import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.example.stats.namespaces.aggregate;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfValue;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.annotations.TransCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.dp.proto.TransCBType;
import com.tailf.maapi.Maapi;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuLeafList;
import com.tailf.navu.NavuList;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;

public class Stats  {


    private static Maapi m = null;
    private long staleCounter;
    private final NcsMain main;

    public Stats(NcsMain main) {
        this.staleCounter = 0;
        this.main = main;
    }

    private void checkStale(DpTrans trans) {
        try {
            long now = System.currentTimeMillis();
            if ((now - this.staleCounter) > 5000) {
                // If more than 5 seconds have passed, we want to throw the
                // entire cached navu tree away, and go to the devices again and
                // get the live data
                this.staleCounter = now;
                NavuContainer mroot =
                    new NavuContainer(new NavuContext(Stats.m,
                                                      trans.getTransaction()));
                ConfNamespace ncsNs = new Ncs();
                NavuContainer ncs = mroot.container(ncsNs.hash());
                trans.setTransactionUserOpaque(ncs);
            }
        }
        catch (Exception e) {
            ;
        }
    }


    @DataCallback(callPoint="aggregate-stats",
                  callType=DataCBType.ITERATOR)
    public Iterator<? extends Object> iterator(DpTrans trans,
                                     ConfObject[] keyPath)
        throws DpCallbackException {
        checkStale(trans);
        try {
            NavuContainer ncs = (NavuContainer)trans.getTransactionUserOpaque();
            NavuList devGroups = ncs.container("devices").list("device-group");
            return devGroups.iterator();
        }
        catch (Exception e) {
            throw new DpCallbackException("", e);
        }
    }


    @DataCallback(callPoint="aggregate-stats",
                  callType=DataCBType.GET_NEXT)
    public ConfKey getKey(DpTrans trans, ConfObject[] keyPath,
                          Object obj)
        throws DpCallbackException {
        try {
            NavuContainer g = (NavuContainer)obj;
            ConfBuf b = (ConfBuf) g.leaf("name").value();
            return new ConfKey( new ConfObject[] { b });
        }
        catch (Exception e) {
            throw new DpCallbackException("", e);
        }
    }


    private void addMembers(NavuContainer ncs,
                            Set<ConfObject> members, ConfBuf group)
        throws NavuException {


        NavuContainer  g = ncs.container("devices").
            list("device-group").
            elem(new ConfKey(new ConfBuf[] {group}));
        NavuLeafList devName = g.leafList("device-name");
        NavuLeafList devGroup = g.leafList("device-group");
        for (ConfValue item : devName) {
            members.add(item);
        }
        for (ConfValue item : devGroup) {
            addMembers(ncs, members, (ConfBuf)item);
        }
    }



    @DataCallback(callPoint="aggregate-stats",
                  callType=DataCBType.GET_ELEM)
    public ConfValue getElem(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {

        checkStale(trans);
        ConfBuf group = (ConfBuf) ((ConfKey) keyPath[1]).elementAt(0);

        // First, figure out which devices are member of this
        // group
        Set<ConfObject> members = new HashSet<ConfObject>();
        try {
            NavuContainer ncs = (NavuContainer)trans.getTransactionUserOpaque();
            addMembers(ncs, members, group);

            int res = 0;
            for (ConfObject g : members.toArray(new ConfObject[0])) {
                NavuList ifs = ncs.container("devices").
                    list("device").
                    elem(new ConfKey(new ConfBuf[] {(ConfBuf)g})).
                    container("live-status").
                    namespace("r").container("sys").
                    container("interfaces").
                    list("interface");
                for (NavuContainer iface : ifs.elements()) {
                    // switch on xml elem hash tag
                    ConfTag leaf = (ConfTag) keyPath[0];
                    switch (leaf.getTagHash()) {
                    case aggregate._packet_errors:
                        ConfUInt32 ui = (ConfUInt32) iface.container("status").
                            container("receive").
                            leaf("errors").value();
                    res += ui.longValue();
                    break;
                    case aggregate._packet_dropped:
                        ConfUInt32 ui2 = (ConfUInt32) iface.container("status").
                            container("receive").
                            leaf("dropped").value();
                        res += ui2.longValue();
                        break;
                    default:
                        System.out.println("Unknow path " +
                                           new ConfPath(keyPath));
                        break;
                    }

                }
            }
            return (ConfValue)new ConfUInt32(res);

        }
        catch (Exception e) {
            throw new DpCallbackException("xx", e);
        }
    }


    @DataCallback(callPoint="aggregate-stats",
                  callType=DataCBType.NUM_INSTANCES)
    public int numInstances(DpTrans trans, ConfObject[] keyPath)
        throws DpCallbackException {

        checkStale(trans);
        try {
            NavuContainer ncs = (NavuContainer)trans.getTransactionUserOpaque();
            NavuList devGroups = ncs.container("devices").list("device-group");
            return devGroups.size();
        }
        catch (Exception e) {
            throw new DpCallbackException("", e);
        }
    }


    @TransCallback(callType=TransCBType.INIT)
    public void init(DpTrans trans) throws DpCallbackException {
        try {
            if (Stats.m == null) {
                // Need a Maapi socket so that we can attach
                Stats.m = new Maapi(main.getAddress());
            }

            Stats.m.attach(trans.getTransaction(),
                           0,
                           trans.getUserInfo().getUserId());

            return;
        }
        catch (Exception e) {
            throw new DpCallbackException("Failed to attach", e);
        }
    }


    @TransCallback(callType=TransCBType.FINISH)
    public void finish(DpTrans trans) throws DpCallbackException {
        try {
            m.detach(trans.getTransaction());
        }
        catch (Exception e) {
            ;
        }
    }
}
