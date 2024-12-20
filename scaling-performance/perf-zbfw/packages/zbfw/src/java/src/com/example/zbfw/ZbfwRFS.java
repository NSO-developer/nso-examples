package com.example.zbfw;

import com.example.zbfw.namespaces.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

public class ZbfwRFS {
  private static Logger log = LogManager.getLogger(ZbfwRFS.class);

  public static final String VDEVICE = "VDEVICE";

  public static final String ROUTER_ZBFW_POLICY = "router-zbfw-policy";
  public static final String ROUTER_ZBFW_POLICY_OBJECT_GROUP_CONFIG =
                                      "router-zbfw-policy-object-group-config";
  public static final String ROUTER_ZBFW_POLICY_SERVICE_OBJECT_GROUP_CONFIG =
                              "router-zbfw-policy-service-object-group-config";

  public static final String ACL_REQUIRED = "ACL_REQUIRED";
  public static final String SRC_IP_PREFIX_ENABLED = "SRC_IP_PREFIX_ENABLED";
  public static final String DEST_IP_PREFIX_ENABLED = "DEST_IP_PREFIX_ENABLED";
  public static final String SRC_IP_PREFIX_LIST_ENABLED =
                                                  "SRC_IP_PREFIX_LIST_ENABLED";
  public static final String DEST_IP_PREFIX_LIST_ENABLED =
                                                 "DEST_IP_PREFIX_LIST_ENABLED";
  public static final String SVC_OG_REQUIRED = "SVC_OG_REQUIRED";
  public static final String ACTION_VALUE = "ACTION_VALUE";
  public static final String ORDER = "ORDER";
  public static final String SEQUENCE_NAME = "SEQUENCE_NAME";
  public static final String RULESET_NAME = "RULESET_NAME";
  public static final String RULE_NAME = "RULE_NAME";
  public static final String ANY_ANY_DATA_PREFIX = "0.0.0.0/0";
  public static final String ACL_NAME = "ACL_NAME";
  public static final String MODE = "MODE";
  public static final String METHOD = "METHOD";

  public static boolean empty(final String s) {
    return s == null || s.trim().isEmpty();
  }

  public static boolean equals(final String s1, final String s2) {
    return s1 == s2 || (s1 == null && s2 == null) || (s1 != null &&
        s1.equals(s2));
  }

  /**
   * Create callback method.
   * This method is called when a service instance committed due to a create
   * or update event.
   *
   * This method returns a opaque as a Properties object that can be null.
   * If not null it is stored persistently by Ncs.
   * This object is then delivered as argument to new calls of the create
   * method for this service (fastmap algorithm).
   * This way the user can store and later modify persistent data outside
   * the service model that might be needed.
   *
   * @param context - The current ServiceContext object
   * @param service - The NavuNode references the service node.
   * @param ncsRoot - This NavuNode references the ncs root.
   * @param opaque  - Parameter contains a Properties object.
   *                This object may be used to transfer
   *                additional information between consecutive
   *                calls to the create callback. It is always
   *                null in the first call. I.e. when the service
   *                is first created.
   * @return Properties the returning opaque instance
   * @throws ConfException
   */

  @ServiceCallback(servicePoint = "zbfw-servicepoint",
                   callType = ServiceCBType.CREATE)
  public Properties create(ServiceContext context,
      NavuNode service,
      NavuNode ncsRoot,
      Properties opaque)
      throws ConfException {

    log.debug("router policy RFS: context " + context + " service " + service);
    NavuContainer policy = (NavuContainer) service;
    try {
      context.setTimeout(400);
      String templDevName = service.leaf("template-name").valueAsString();
      String[] parts = templDevName.split("-");
      String templateName = parts[0];
      String deviceName = parts[1];

      if (policy.list("zone").size() > 0) {
        log.debug("Applying router ZBFW policy RFS...");
        Template zbfwPolicyTemplate = new Template(context,
            ROUTER_ZBFW_POLICY);
        TemplateVariables zbfwPolicyVars = new TemplateVariables();
        zbfwPolicyVars.putQuoted(VDEVICE, deviceName);
        zbfwPolicyTemplate.apply(policy, zbfwPolicyVars);
      }
      if (policy.list("zone-based-policy").size() > 0) {
        processZoneBasedPolicy(context, policy, deviceName);
      }
    } catch (NavuException e) {
      log.error("exception " + e.getMessage(), e);
      throw new DpCallbackException(e.getMessage(), e);
    } catch (Exception e) {
      log.error("exception " + e.getMessage(), e);
      throw new DpCallbackException(e.getMessage(), e);
    }

    return opaque;
  }

  private void processZoneBasedPolicy(ServiceContext context,
      NavuContainer policy,
      String deviceName) throws ConfException, UnknownHostException {
    NavuList zoneBasedPolicyList = policy.list("zone-based-policy");
    String aclRequired = "true";
    String svcOGReqd = "true";

    Set<String> processedDataPrefixList = new HashSet<String>();

    for (NavuNode zoneBased : zoneBasedPolicyList) {
      String name = zoneBased.leaf("name").valueAsString();
      String acl_name = name;
      char firstChar = acl_name.charAt(0);

      firstChar = Character.toLowerCase(firstChar);
      if (firstChar < 'a' || firstChar > 'z') {
        acl_name = "acl-" + name;
      }
      NavuList sequenceList = zoneBased.list("sequence");
      for (NavuNode sequenceNode : sequenceList) {
        aclRequired = "true";
        svcOGReqd = "true";
        NavuContainer match = sequenceNode.container("match");
        String seqValue = sequenceNode.leaf("seq-value")//
            .valueAsString();
        String seqName = sequenceNode.leaf("seq-name").valueAsString();
        if (seqName == null || seqName.isEmpty()) {
          seqName = seqValue;
        }

        String method = "OLD";
        String sourceIp = match.leaf("source-ip").valueAsString();
        int sourceIpSize = (sourceIp != null) ? sourceIp//
            .split(" ")//
                .length
            : 0;
        String sourceDataPrefixList = match//
            .leaf("source-data-prefix-list")//
            .valueAsString();
        int sourceDataPrefixListSize =
                        (sourceDataPrefixList != null) ? sourceDataPrefixList//
                                                         .split(" ")//
                                                         .length : 0;

        String sourcePort = match.leaf("source-port").valueAsString();
        int sourcePortSize = (sourcePort != null) ? sourcePort//
                                                    .split(" ")//
                                                    .length : 0;

        String destinationIp = match.leaf("destination-ip")//
                                    .valueAsString();
        int destinationIpSize = (destinationIp != null) ? destinationIp//
                                                          .split(" ")//
                                                          .length : 0;
        String destinationDataPrefixList = match.leaf(
            "destination-data-prefix-list").valueAsString();
        int destinationDataPrefixListSize =
            (destinationDataPrefixList != null) ? destinationDataPrefixList//
                                                  .split(" ")//
                                                  .length : 0;
        String destinationPort = match.leaf("destination-port")//
                                      .valueAsString();
        int destinationPortSize =
            (destinationPort != null) ? destinationPort.split(" ")//
                                                       .length : 0;
        String protocolName = match.leaf("protocol-name").valueAsString();
        int protocolNameSize = (protocolName != null) ? protocolName//
                                                        .split(" ")//
                                                        .length : 0;
        String protocol = match.leaf("protocol").valueAsString();
        int protocolSize = (protocol != null) ? protocol//
            .split(" ").length : 0;
        if (empty(sourcePort) &&
            empty(destinationPort) &&
            empty(protocolName) && empty(protocol)) {
          svcOGReqd = "false";
        }

        processZbfwMatchSequence(context, policy, deviceName,
                aclRequired, svcOGReqd, name, acl_name,
                match, seqValue, seqName, "", "permit", sourceIp,
                sourceDataPrefixList, sourcePort, destinationIp,
                destinationDataPrefixList, destinationPort, protocol,
                protocolName, method, processedDataPrefixList);
      }
    }
    processedDataPrefixList.clear();
  }

  private void processZbfwMatchSequence(ServiceContext context,
            NavuContainer policy, String deviceName,
            String aclRequired, String svcOGReqd, String name, String acl_name,
            NavuContainer match, String seqValue, String seqName, String order,
            String action, String sourceIp, String sourceDataPrefixList,
            String sourcePort, String destinationIp,
            String destinationDataPrefixList, String destinationPort,
            String protocol, String protocolName, String method,
            Set<String> processedDataPrefixList) throws ConfException,
                                                        UnknownHostException {
    String orderStr = "";
    String mode = "";

    if (!empty(sourceDataPrefixList) || !empty(sourceIp)) {
      mode = "IP-ANY";

      if (!empty(destinationIp)) {
        mode = "IP-IP";
        processSourceOrDestinationIp(context, policy, deviceName,
            seqValue, seqName, destinationIp, "dstn",
            name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
            method, mode);
        processSourceOrDestinationIp(context, policy, deviceName,
            seqValue, seqName, sourceIp, "src",
            name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
            method, mode);
      } else if (!empty(destinationDataPrefixList)) {
        mode = "IP-IP";
        processSourceOrDestinationPrefixList(context, deviceName, policy,
            processedDataPrefixList, "", seqName,
            destinationDataPrefixList, "dstn", name,
            protocol, svcOGReqd, orderStr, action, acl_name, method, mode);
        processSourceOrDestinationIp(context, policy, deviceName,
            seqValue, seqName, sourceIp, "src",
            name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
            method, mode);
        processSourceOrDestinationIp(context, policy, deviceName,
            seqValue, seqName, destinationIp,
            "dstn", name, protocol, aclRequired, svcOGReqd, orderStr,
            action, acl_name, method, mode);
      }

      if (equals(mode, "IP-ANY")) {
        orderStr = processOrder(order, mode);
        processSourceOrDestinationIp(context, policy, deviceName,
            seqValue, seqName, sourceIp, "src",
            name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
            method, mode);
        processSourceOrDestinationIp(context, policy, deviceName,
            seqValue, seqName, destinationIp, "dstn",
            name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
            method, mode);
      }

      if (!empty(sourceDataPrefixList)) {
        processSourceOrDestinationPrefixList(context, deviceName, policy,
            processedDataPrefixList, "", seqName,
            sourceDataPrefixList, "src", name, protocol,
            svcOGReqd, orderStr, action, acl_name, method, mode);
      }
    }

    if ((!empty(destinationDataPrefixList) || !empty(destinationIp)) &&
        empty(sourceDataPrefixList) && empty(sourceIp)) {
      mode = "ANY-IP";
      processSourceOrDestinationIp(context, policy, deviceName,
          seqValue, seqName, destinationIp,
          "dstn",
          name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
          method, mode);

      if (!empty(destinationDataPrefixList)) {
        processSourceOrDestinationPrefixList(context, deviceName, policy,
            processedDataPrefixList, "", seqName,
            destinationDataPrefixList, "dstn", name,
            protocol, svcOGReqd, orderStr, action, acl_name, method, mode);
      }
    }

    if (empty(sourceDataPrefixList) && empty(sourceIp)
        && empty(destinationDataPrefixList) && empty(destinationIp)) {
      mode = "ANY-ANY";
      processSourceOrDestinationIp(context, policy, deviceName,
          seqValue, seqName, sourceIp, "src",
          name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
          method, mode);
      processSourceOrDestinationIp(context, policy, deviceName,
          seqValue, seqName, destinationIp, "dstn",
          name, protocol, aclRequired, svcOGReqd, orderStr, action, acl_name,
          method, mode);
    }

    processSourceAndDestinationPort(context, policy, deviceName, sourcePort,
        destinationPort, name, seqValue, seqName, protocol,
        protocolName, svcOGReqd, orderStr, action, acl_name, method, mode);

    if (empty(sourcePort) && empty(destinationPort)) {
      processProtocol(context, policy, deviceName, protocol, protocolName,
                      name, seqValue, seqName, svcOGReqd,
                      orderStr, action, acl_name, method, mode);
    }
  }

  private void processSourceOrDestinationIp(ServiceContext context,
      NavuContainer policy, String deviceName,
      String seqValue, String seqName, String ipPrefixList,
      String sourceOrDestination, String name, String protocol,
      String aclRequired, String svcOGReqd, String order, String actionValue,
      String acl_name, String method, String mode)
      throws ConfException, UnknownHostException {
    Template policyTemplate = new Template(context,
                                  ROUTER_ZBFW_POLICY_OBJECT_GROUP_CONFIG);
    TemplateVariables templateVariablesForObjectGroup = new TemplateVariables();
    if (!empty(ipPrefixList)) {
      String[] ipPrefixes = ipPrefixList.split(" ");
      long totalTime = 0;
      for (String ip : ipPrefixes) {
        templateVariablesForObjectGroup =
            getTemplateVariablesForObjectGroup(deviceName, sourceOrDestination,
                                               ip, seqValue, seqName, name,
                                               acl_name);
        templateVariablesForObjectGroup.putQuoted(ACL_REQUIRED, aclRequired);
        templateVariablesForObjectGroup.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
        templateVariablesForObjectGroup.putQuoted(ORDER, order);
        templateVariablesForObjectGroup.putQuoted(ACTION_VALUE, actionValue);
        templateVariablesForObjectGroup.putQuoted(METHOD, method);
        templateVariablesForObjectGroup.putQuoted(MODE, mode);

        policyTemplate.apply(policy, templateVariablesForObjectGroup);
      }
    }
  }

  private void processSourceOrDestinationPrefixList(ServiceContext context,
      String deviceName, NavuContainer policy,
      Set<String> processedDataPrefixList, String seqValue, String seqName,
      String dataPrefixLists, String sourceOrDestination, String name,
      String protocol, String svcOGReqd, String order, String actionValue,
      String acl_name, String method, String mode)
      throws ConfException, UnknownHostException {
    Template policyTemplate = new Template(context,
                                  ROUTER_ZBFW_POLICY_OBJECT_GROUP_CONFIG);
    String[] dataPrefixListNodes = dataPrefixLists.split(" ");
    for (String dataPrefixList : dataPrefixListNodes) {
      if (processedDataPrefixList.contains(dataPrefixList)) {
        continue;
      } else {
        processDataPrefixList(policy, deviceName, policyTemplate,
            dataPrefixList, sourceOrDestination, seqName, svcOGReqd, order,
            actionValue, acl_name, method, mode);
        processedDataPrefixList.add(dataPrefixList);
      }
    }
  }

  private String processOrder(String order, String mode) throws ConfException {
    if (empty(order)) {
      return order;
    }

    String orderStr = "";
    long orderNumber = (Long.parseLong(order)) - 1;
    if (equals(mode, "IP-IP") || equals(mode, "IP-ANY") ||
        equals(mode, "ANY-IP") || equals(mode, "ANY-ANY")) {
      orderStr = String.valueOf((orderNumber * 1000) + 25);
    } else {
      StringBuilder errStr = new StringBuilder();
      errStr.append(String.format("Invalid mode (%s) while processing" +
                                  "rule order", mode));
      log.error(errStr.toString());
      throw new DpCallbackException(errStr.toString());
    }
    return orderStr;
  }

  private void processDataPrefixList(NavuContainer policy, String deviceName,
      Template policyTemplate, String dataPrefixListName,
      String sourceOrDestination, String seqName,
      String svcOGReqd, String order, String actionValue, String acl_name,
      String method, String mode) throws ConfException, UnknownHostException {
    NavuList dataPrefixList = policy.container("lists")//
                              .list("data-prefix-list");
    NavuContainer dataPrefixListNode = dataPrefixList.elem(dataPrefixListName);
    NavuList ipPrefixList = dataPrefixListNode.list("ip-prefix");
    if (!ipPrefixList.isEmpty()
        && ipPrefixList.containsNode(ANY_ANY_DATA_PREFIX)) {
      return;
    } else {
      Template policyObjectGroup = policyTemplate;
      TemplateVariables templateVariables = getTemplateVariablesForObjectGroup(
          deviceName, sourceOrDestination, "", "", seqName,
          dataPrefixListName, acl_name);

      templateVariables.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
      templateVariables.putQuoted(ORDER, order);
      templateVariables.putQuoted(ACTION_VALUE, actionValue);
      templateVariables.putQuoted(MODE, mode);
      templateVariables.putQuoted(METHOD, method);

      policyObjectGroup.apply(policy, templateVariables);
    }
  }

  private void processSourceAndDestinationPort(ServiceContext context,
      NavuContainer policy, String deviceName, String sourcePort,
      String destinationPort, String name,
      String seqValue, String seqName, String protocol, String protocolName,
      String svcOGReqd, String order, String actionValue, String acl_name,
      String method, String mode)
      throws ConfException, UnknownHostException {
    String[] sports = {};
    String[] dports = {};
    Template policyTemplate = new Template(context,
                         ROUTER_ZBFW_POLICY_SERVICE_OBJECT_GROUP_CONFIG);

    if (empty(sourcePort) && empty(destinationPort) && empty(protocol)) {
      TemplateVariables templateVariablesForObjectGroup =
          getTemplateVariablesForObjectGroup(deviceName, "", "",
          seqValue, seqName, name, acl_name);
      templateVariablesForObjectGroup.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
      templateVariablesForObjectGroup.putQuoted(ACTION_VALUE, actionValue);
      templateVariablesForObjectGroup.putQuoted(ORDER, order);
      templateVariablesForObjectGroup.putQuoted(MODE, mode);
      templateVariablesForObjectGroup.putQuoted(METHOD, method);

      processProtocolListVarsAndApply(policy, protocol,
                                      templateVariablesForObjectGroup,
                                      policyTemplate);
      return;
    }

    if (!empty(protocolName)) {
      return;
    }

    if (!empty(sourcePort)) {
      sports = sourcePort.split(" ");
    }

    if (!empty(destinationPort)) {
      dports = destinationPort.split(" ");
    }

    for (String sport : sports) {
      for (String dport : dports) {
        TemplateVariables templateVariablesForObjectGroup =
            getTemplateVariablesForObjectGroup(deviceName, "", "",
            seqValue, seqName, name, acl_name);
        templateVariablesForObjectGroup.putQuoted("SPORT", sport);
        templateVariablesForObjectGroup.putQuoted("DPORT", dport);
        templateVariablesForObjectGroup.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
        templateVariablesForObjectGroup.putQuoted(ACTION_VALUE, actionValue);
        templateVariablesForObjectGroup.putQuoted(ORDER, order);
        templateVariablesForObjectGroup.putQuoted(MODE, mode);
        templateVariablesForObjectGroup.putQuoted(METHOD, method);
        processProtocolListVarsAndApply(policy, protocol,
                                        templateVariablesForObjectGroup,
                                        policyTemplate);
      }
    }

    if (empty(sourcePort)) {
      for (String dport : dports) {
        TemplateVariables templateVariablesForObjectGroup =
            getTemplateVariablesForObjectGroup(deviceName, "", "",
            seqValue, seqName, name, acl_name);
        templateVariablesForObjectGroup.putQuoted("DPORT", dport);
        templateVariablesForObjectGroup.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
        templateVariablesForObjectGroup.putQuoted(ACTION_VALUE, actionValue);
        templateVariablesForObjectGroup.putQuoted(ORDER, order);
        templateVariablesForObjectGroup.putQuoted(MODE, mode);
        templateVariablesForObjectGroup.putQuoted(METHOD, method);
        processProtocolListVarsAndApply(policy, protocol,
                                        templateVariablesForObjectGroup,
                                        policyTemplate);
      }
    }

    if (empty(destinationPort)) {
      for (String sport : sports) {
        TemplateVariables templateVariablesForObjectGroup =
            getTemplateVariablesForObjectGroup(deviceName, "", "",
            seqValue, seqName, name, acl_name);
        templateVariablesForObjectGroup.putQuoted("SPORT", sport);
        templateVariablesForObjectGroup.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
        templateVariablesForObjectGroup.putQuoted(ACTION_VALUE, actionValue);
        templateVariablesForObjectGroup.putQuoted(ORDER, order);
        templateVariablesForObjectGroup.putQuoted(MODE, mode);
        templateVariablesForObjectGroup.putQuoted(METHOD, method);
        processProtocolListVarsAndApply(policy, protocol,
                                        templateVariablesForObjectGroup,
                                        policyTemplate);
      }
    }
  }

  private void processProtocol(ServiceContext context, NavuContainer policy,
      String deviceName, String protocol, String protocolName,
      String name, String seqValue, String seqName,
      String svcOGReqd, String order, String actionValue, String acl_name,
      String method, String mode)
      throws ConfException, UnknownHostException {
    if (!empty(protocol) || !empty(protocolName)) {
      Template policyTemplate = new Template(context,
                              ROUTER_ZBFW_POLICY_SERVICE_OBJECT_GROUP_CONFIG);
      TemplateVariables templateVariablesForObjectGroup =
          getTemplateVariablesForObjectGroup(deviceName,
          "", "", seqValue, seqName, name,
          acl_name);
      templateVariablesForObjectGroup.putQuoted(SVC_OG_REQUIRED, svcOGReqd);
      templateVariablesForObjectGroup.putQuoted(ACTION_VALUE, actionValue);
      templateVariablesForObjectGroup.putQuoted(ORDER, order);
      templateVariablesForObjectGroup.putQuoted(MODE, mode);
      processProtocolListVarsAndApply(policy, protocol,
                                      templateVariablesForObjectGroup,
                                      policyTemplate);
    }
  }

  private void processProtocolListVarsAndApply(NavuContainer policy,
      String protocol, TemplateVariables templateVariablesForObjectGroup,
      Template policyTemplate) throws ConfException {
    if (!empty(protocol)) {
      String[] protocolList = protocol.split(" ");
      long totalTime = 0;
      for (String protocols : protocolList) {
        int protocolNumber = Integer.parseInt(protocols);
        if (protocolNumber == 17) {
          templateVariablesForObjectGroup.putQuoted("PROTOCOL", "udp");
        } else if (protocolNumber == 6) {
          templateVariablesForObjectGroup.putQuoted("PROTOCOL", "tcp");
        } else if (protocolNumber == 1) {
          templateVariablesForObjectGroup.putQuoted("PROTOCOL", "icmp");
        } else {
          templateVariablesForObjectGroup.putQuoted("PROTOCOL",
                                          String.valueOf(protocolNumber));
        }
        policyTemplate.apply(policy, templateVariablesForObjectGroup);
      }
    } else {
      templateVariablesForObjectGroup.putQuoted("PROTOCOL", "");
      policyTemplate.apply(policy, templateVariablesForObjectGroup);
    }
  }

  private TemplateVariables getTemplateVariablesForObjectGroup(
      String deviceName, String sourceOrDestination, String ipPrefix,
      String seqValue, String seqName, String name, String acl_name)
      throws UnknownHostException {
    TemplateVariables templateVariables = new TemplateVariables();
    putEmptyVariables(templateVariables);
    String networkAddress = "";
    String netmask = "";
    if (!ipPrefix.isEmpty()) {
      String[] parts = ipPrefix.split("/");
      String ip = parts[0];
      int prefix;
      if (parts.length < 2) {
        prefix = 0;
      } else {
        prefix = Integer.parseInt(parts[1]);
      }
      int mask = 0xffffffff << (32 - prefix);
      byte[] bytes = new byte[] {
          (byte) (mask >>> 24), (byte) (mask >> 16 & 0xff),
          (byte) (mask >> 8 & 0xff), (byte) (mask & 0xff) };
      InetAddress netAddr = InetAddress.getByAddress(bytes);
      networkAddress = ip.toString();
      netmask = netAddr.getHostAddress();
    }
    templateVariables.putQuoted("IP", networkAddress);
    templateVariables.putQuoted("IP", networkAddress);
    templateVariables.putQuoted("MASK", netmask);
    templateVariables.putQuoted("SEQUENCE_NUMBER", seqValue);
    templateVariables.putQuoted(SEQUENCE_NAME, seqName);
    templateVariables.putQuoted("SRC_DEST", sourceOrDestination);
    templateVariables.putQuoted("NAME", name);
    templateVariables.putQuoted(ACL_REQUIRED, "true");
    templateVariables.putQuoted(SVC_OG_REQUIRED, "true");
    templateVariables.putQuoted(ACL_NAME, acl_name);
    templateVariables.putQuoted("FQDN_REQUIRED", "false");
    templateVariables.putQuoted("GEO_REQUIRED", "false");
    templateVariables.putQuoted("NETWORK", "true");
    templateVariables.putQuoted(VDEVICE, deviceName);
    templateVariables.putQuoted("NETWORK", "true");
    return templateVariables;
  }

  private void putEmptyVariables(TemplateVariables templateVariables) {
    templateVariables.putQuoted("PREFIX_LIST_NAME", "");
    templateVariables.putQuoted("SERVICE_LIST_NAME", "");
    templateVariables.putQuoted("IP", "");
    templateVariables.putQuoted("MASK", "");
    templateVariables.putQuoted("SEQUENCE_NUMBER", "");
    templateVariables.putQuoted(SEQUENCE_NAME, "");
    templateVariables.putQuoted(RULESET_NAME, "");
    templateVariables.putQuoted(RULE_NAME, "");
    templateVariables.putQuoted("SRC_DEST", "");
    templateVariables.putQuoted("ZBFW_POLICY_NAME", "");
    templateVariables.putQuoted("NAME", "");
    templateVariables.putQuoted("SOURCE", "");
    templateVariables.putQuoted("DESTINATION", "");
    templateVariables.putQuoted("PROTOCOL", "");
    templateVariables.putQuoted("SPORT", "");
    templateVariables.putQuoted("DPORT", "");
    templateVariables.putQuoted("PROTOCOL_NAME", "");
    templateVariables.putQuoted(ACL_REQUIRED, "");
    templateVariables.putQuoted(SRC_IP_PREFIX_ENABLED, "");
    templateVariables.putQuoted(DEST_IP_PREFIX_ENABLED, "");
    templateVariables.putQuoted(SRC_IP_PREFIX_LIST_ENABLED, "");
    templateVariables.putQuoted(DEST_IP_PREFIX_LIST_ENABLED, "");
    templateVariables.putQuoted(SVC_OG_REQUIRED, "");
    templateVariables.putQuoted(ACTION_VALUE, "");
    templateVariables.putQuoted("GEO_CONTINENT", "");
    templateVariables.putQuoted("GEO_COUNTRY", "");
    templateVariables.putQuoted("GEO_REQUIRED", "");
    templateVariables.putQuoted(ORDER, "");
    templateVariables.putQuoted("APP_LIST_NAME", "");
    templateVariables.putQuoted("APP_NAME", "");
    templateVariables.putQuoted("APP_FAMILY_NAME", "");
    templateVariables.putQuoted("CLASS_SUFFIX", "");
    templateVariables.putQuoted(ACL_NAME, "");
    templateVariables.putQuoted("FQDN_PATTERN", "");
    templateVariables.putQuoted("FQDN_REQUIRED", "");
    templateVariables.putQuoted("NETWORK", "");
    templateVariables.putQuoted(METHOD, "");
  }
}