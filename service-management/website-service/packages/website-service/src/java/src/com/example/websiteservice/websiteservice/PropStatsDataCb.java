package com.example.websiteservice.websiteservice;

import com.example.websiteservice.webserver.namespaces.interfaces;
import com.example.websiteservice.websiteservice.namespaces.webSiteProps;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfException;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.DataCallback;
import com.tailf.dp.proto.DataCBType;
import com.tailf.maapi.Maapi;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
import java.io.IOException;

/**
 * This class calculates a value based of the oper data on the
 * webserver devices.
 *
 */
public class PropStatsDataCb {

    @Resource(type=ResourceType.MAAPI,
              scope=Scope.CONTEXT,
              qualifier="TransMaapi")
    public Maapi m;

    /**
     * @param m a maapi socket.
     */
    public PropStatsDataCb() {
    }

    /**
     * Calculates a drop ratio according to the interface parameters.
     *
     * @param trans
     * @param kp
     * @return
     * @throws DpCallbackException
     */
    @DataCallback(callPoint=webSiteProps.callpoint_ws_stats,
                  callType=DataCBType.GET_ELEM)
        public ConfValue getElem(DpTrans trans, ConfObject[] kp)
        throws DpCallbackException, ConfException, IOException {
        if (trans.getDBName()==Conf.DB_RUNNING) {

            try {
                NavuContainer root = new
                    NavuContainer(new NavuContext(m, trans.getTransaction()))
                    .container(new Ncs().hash());

                ConfKey profileId =(ConfKey)kp[3];
                ConfKey webserverId =(ConfKey)kp[1];

                NavuLeaf backend =
                    root.container(Ncs._services_).
                    container(Ncs._properties_).
                    namespace(webSiteProps.id).
                    container(webSiteProps._web_site_).
                    list(webSiteProps._profile_).elem(profileId).
                    list(webSiteProps._backend_).elem(webserverId).
                    leaf(webSiteProps._name_);

                NavuLeaf packets = root.container(Ncs._devices_).
                    list(Ncs._device_).
                    elem(backend.toKey()).
                    container(Ncs._live_status_).
                    namespace(interfaces.id).
                    list(interfaces._interface_).
                    elem(new String[]{"eth0"}).
                    container(interfaces._stats_).
                    container(interfaces._receive_).leaf(interfaces._packets_);
                NavuLeaf dropped =
                    root.container(Ncs._devices_).
                    list(Ncs._device_).
                    elem(backend.toKey()).
                    container(Ncs._live_status_).
                    namespace(interfaces.id).
                    list(interfaces._interface_).
                    elem(new String[]{"eth0"}).
                    container(interfaces._stats_).
                    container(interfaces._receive_).
                    leaf(interfaces._dropped_);


                return new ConfUInt32(((ConfUInt32)dropped.value()).longValue()*
                           1000 /
                           ((ConfUInt32)packets.value()).longValue());
            }catch (NavuException e) {
                return null;
            }
        }
        return null;
    }
}
