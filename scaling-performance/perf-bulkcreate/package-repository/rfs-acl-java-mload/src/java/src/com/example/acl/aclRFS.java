package com.example.acl;

import com.example.acl.namespaces.*;
import java.util.EnumSet;
import java.util.Properties;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.maapi.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class aclRFS {
    private static Logger log = LogManager.getLogger(aclRFS.class);

    private static String getNextIPV4Address(String ip) {
        String[] nums = ip.split("\\.");
        int i = (Integer.parseInt(nums[0]) << 24
                | Integer.parseInt(nums[2]) << 8
                | Integer.parseInt(nums[1]) << 16
                | Integer.parseInt(nums[3])) + 1;

        return String.format("%d.%d.%d.%d", i >> 24 & 0xFF, i >> 16 & 0xFF,
                                            i >>  8 & 0xFF, i >>  0 & 0xFF);
    }

    private static String genConfigXml(int numVals) {
        String ip = "1.0.0.2";
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<config xmlns=\"http://tail-f.com/ns/ncs\">\n");
        for (int i = 0; i < numVals; i++) {
            sb.append("  <route xmlns=\"http://cisco.com/ned/asa\">\n");
            sb.append("    <id>ics</id>\n");
            sb.append("    <net>" + ip + "</net>\n");
            sb.append("    <net-mask>255.255.255.255</net-mask>\n");
            sb.append("    <gw>1.0.0.1</gw>\n");
            sb.append("    <metric>1</metric>\n");
            sb.append("  </route>\n");
            ip = getNextIPV4Address(ip);
        }
        ip = "1.0.0.1";
        sb.append("  <access-list xmlns=\"http://cisco.com/ned/asa\">\n");
        sb.append("    <access-list-id>\n");
        sb.append("      <id>tailf_42</id>\n");
        for (int i = 0; i < numVals; i++) {
            sb.append("      <rule>\n");
            sb.append("        <id>extended permit tcp host " + ip + " host " +
                                                      ip + " eq https</id>\n");
            sb.append("        <log/>\n");
            sb.append("      </rule>\n");
            ip = getNextIPV4Address(ip);
        }
        sb.append("      <rule>\n");
        sb.append("        <id>extended demy ip any4 any4</id>\n");
        sb.append("        <log/>\n");
        sb.append("      </rule>\n");
        sb.append("      <rule>\n");
        sb.append("        <id>extended demy ip any6 any6</id>\n");
        sb.append("        <log/>\n");
        sb.append("      </rule>\n");
        sb.append("    </access-list-id>\n");
        sb.append("  </access-list>\n");
        sb.append("  </config>\n");
        return sb.toString();
    }

    @ServiceCallback(servicePoint="rfs-acl-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws DpCallbackException {
        String servicePath = service.getKeyPath();
        try {
            int numRoutesRules = (int) ((ConfUInt32) service.leaf("num-routes")
                                                         .value()).longValue();
            String devStr = service.leaf("device").valueAsString();
            String rulesXML = genConfigXml(numRoutesRules);
            Maapi maapi = service.context().getMaapi();
            int tid = service.context().getMaapiHandle();
            maapi.loadConfigCmds(
                tid, EnumSet.of(MaapiConfigFlag.MERGE_CONFIGURATIONS,
                                MaapiConfigFlag.XML_FORMAT),
                rulesXML,
                "/devices/device{" + devStr + "}");
        } catch (Exception e) {
            log.error("exception " + e.getMessage(), e);
            throw new DpCallbackException("Cannot create service " +
                                          servicePath, e);
        }
        return opaque;
    }
}
