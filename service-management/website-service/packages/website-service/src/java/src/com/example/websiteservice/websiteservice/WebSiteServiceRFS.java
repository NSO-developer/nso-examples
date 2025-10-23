package com.example.websiteservice.websiteservice;

import java.util.ArrayList;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.example.websiteservice.lb.namespaces.lb;
import com.example.websiteservice.webserver.namespaces.interfaces;
import com.example.websiteservice.webserver.namespaces.webserver;
import com.example.websiteservice.websiteservice.namespaces.webSite;
import com.example.websiteservice.websiteservice.namespaces.webSiteProps;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt16;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.DiffIterateResultFlag;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiDiffIterate;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;
//generated symbols from the device models

/**
 * This class implement the create logic for the Web SIte Service.
 *
 */
public class WebSiteServiceRFS {
    private static Logger LOGGER  =
        LogManager.getLogger(WebSiteServiceRFS.class);

    private final NcsMain main;

    public WebSiteServiceRFS(NcsMain main){
        this.main = main;
    }

    @ServiceCallback(servicePoint="websiteservice",
                     callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode root,
                             Properties opaque)
                             throws DpCallbackException {

        Properties opaqueProps;

        opaqueProps = opaque;
        LOGGER.info("opaqueProps Alias:" + opaqueProps.getProperty("alias")+
            ",sub:" + opaqueProps.getProperty("subnet"));

        try {

            /*
             * If this is an update, then decode the values stored away to be
             * used at update.
             * These values are needed in orde to make sure that the same
             * alias and subnet values are used through
             */

            NavuNode myService = service;


            NavuContainer myProfile = root.
                container(Ncs._services_).
                container(Ncs._properties_).
                namespace(webSiteProps.id).
                container(webSite._web_site_).
                list(webSiteProps._profile_).
                elem(myService.leaf(webSite._lb_profile_).toKey());

            if (myProfile == null)
                throw new ConfException(
                    "No such profile: " +
                    myService.leaf(webSite._lb_profile_).value());

            /*
             * Check if a corresponding listener exists on the load balancer.
             */

            ArrayList<Integer> nums = new ArrayList<Integer>();


            NavuContainer myConfig = root.
                container(Ncs._devices_).
                list(Ncs._device_).
                elem(myProfile.leaf(webSiteProps._lb_).toKey()).
                container(Ncs._config_);
            NavuContainer myLbConfig = myConfig.
                    namespace(lb.id).
                    container(lb._lbConfig_);

            /*
             * Create listener on load balancer
             */


            ConfKey myKey = new ConfKey(
                new ConfObject[]{myService.leaf(webSite._ip_).value(),
                                 myService.leaf(webSite._port_).value()});

            if (!myLbConfig.list(lb._listener_).containsNode(myKey)) {

                myLbConfig.list(lb._listener_).sharedCreate(myKey);

                if (opaque == null ||
                    opaqueProps.getProperty("subnet") == null){

                    /*
                     * Derive subnet.
                     */

                    for (NavuNode elem: myLbConfig.select(
                             lb._listener_+"/.*/"+lb._service_+"/.*/" +
                             lb._number_)) {
                        Integer num = ((ConfInt32) ((NavuLeaf)elem).value()).
                            intValue();
                        nums.add(num);
                    }



                    for (Integer i = 1;i<32;i++){
                        if (!nums.contains(i)){
                            opaqueProps.
                                setProperty("subnet",
                                            Integer.valueOf(i).toString());
                            break;
                        }
                    }
                }
            }

            /*
             * Create service on the listener.
             */

            NavuContainer ms = myLbConfig.list(lb._listener_).
                elem(myKey).
                list(lb._service_).
                sharedCreate(service.leaf(Ncs._name_).toKey());

            ms.leaf(lb._number_).
                sharedSet(opaqueProps.getProperty("subnet", "0"));
            ms.leaf(lb._URL_pattern_).
                sharedSet( myService.leaf(webSite._url_).value());
            ms.container(lb._session_).leaf(lb._type_).sharedSet("IP");


            /*
             * Update www devices.
             */


            /*
             * CIDR IP 192.168.0.(0-31<<5 + 0-7)
             *
             */

            String baseIp = "192.168.0.";
            Integer server = 1;

            /*
             * Update the load balancer with the existing www devices
             */

            int subnet = Integer.valueOf(opaqueProps.getProperty("subnet","0"));

            for (NavuContainer elem: myProfile.list(webSiteProps._backend_).
                     elements()) {

                NavuLeaf elemName = elem.leaf(Ncs._name_);
                NavuContainer md = root.container(Ncs._devices_).
                    list(Ncs._device_).elem(elemName.toKey());

                String ipv4Str = baseIp + ((subnet<<3) + server);
                String ipv6Str = "::ff:ff:" + ipv4Str;
                String ipStr = ipv4Str;
                String nedIdStr =
                    context.getNedIdByDeviceName(elemName.valueAsString());
                if ("webserver-nc-1.0:webserver-nc-1.0".equals(nedIdStr)) {
                    ipStr = ipv4Str;
                } else if ("webserver-nc-1.2:webserver-nc-1.2"
                           .equals(nedIdStr)) {
                    ipStr = ipv6Str;
                }

                md.container(Ncs._config_).namespace(nedIdStr).
                    container(webserver._wsConfig_).
                    list(webserver._listener_).
                    sharedCreate(new String[] {ipStr, ""+8008});

                ms.list(lb._backend_).sharedCreate(
                    new String[]{baseIp + ((subnet<<3) + server++),
                                 ""+8008});


                if (opaque==null ||
                    opaqueProps.getProperty("alias") == null) {

                    /*
                     * Then, this is a real CREATE. No need to rememeber
                     * anything.
                     */
                    ArrayList<Long> aliases = new ArrayList<Long>();
                    try {
                        for (NavuContainer ifs: md.container(Ncs._config_).
                                namespace(nedIdStr).
                             list(interfaces._interface_).
                             elem("{eth0}").
                             list(interfaces._alias_).elements()) {
                            Long num = ((ConfUInt16)ifs.
                                        leaf(interfaces._number_).
                                        value()).longValue();
                            aliases.add(num);
                        }
                    }
                    catch (NullPointerException e) {
                        throw new ConfException(
                            "No data on device, did you sync from network");
                    }

                    for (Integer i = 0;i<32000;i++){
                        if (!aliases.contains(i.longValue())){
                            opaqueProps.
                                setProperty("alias",
                                            Integer.valueOf(i).toString());
                            break;
                        }
                    }
                }

                md.container(Ncs._config_).namespace(nedIdStr).
                    list(interfaces._interface_).
                    elem("{eth0}").
                    list(interfaces._alias_).
                    sharedCreate(new String[] {
                            opaqueProps.getProperty("alias", "0")}
                        ).
                    leaf(interfaces._ipv4_address_).
                    sharedSet( ""+baseIp + (((subnet<<3) + server-1) & 0xff));
            }

            return opaqueProps;
        } catch (Exception e) {
            throw new DpCallbackException("Service create failed", e);
        }

    }


    @ActionCallback(callPoint="websiteselftest", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    @ActionCallback(callPoint="websiteselftest", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {

        ConfNamespace ws = new webSite();
        String str = null;
        if (kp[0] instanceof ConfKey) {
            str = ((ConfKey)kp[0]).toString();
        } else {
            str = ((ConfTag)kp[0]).toString();
        }

        LOGGER.info("In selftest");

        return new ConfXMLParam[]
            {new ConfXMLParamValue(ws, "success", new ConfBool(true)),
             new ConfXMLParamValue(ws, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("selftest failed", e);
        }

    }



    @ActionCallback(callPoint="diffcheck", callType=ActionCBType.ACTION)
    public ConfXMLParam[] diffcheck(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try (Maapi maapi3 = new Maapi(main.getAddress())) {
            System.out.println("-------------------");
            System.out.println(params[0]);
            System.out.println(params[1]);
            System.out.println(params[2]);

            ConfUInt32 val = (ConfUInt32) params[2].getValue();
            int tid = (int)val.longValue();

            maapi3.attach(tid, -1);

            maapi3.diffIterate(tid, new MaapiDiffIterate() {
                // Override the Default iterate function in the TestCase class
                public DiffIterateResultFlag iterate(ConfObject[] kp,
                                                     DiffIterateOperFlag op,
                                                     ConfObject oldValue,
                                                     ConfObject newValue,
                                                     Object initstate) {
                    System.out.println("path = " + new ConfPath(kp));
                    System.out.println("op = " + op);
                    System.out.println("newValue = " + newValue);
                    return DiffIterateResultFlag.ITER_RECURSE;

                }

            });

            maapi3.detach(tid);

            return new ConfXMLParam[]{};
        } catch (Exception e) {
            throw new DpCallbackException("diffcheck failed", e);
        }

    }

}
