package com.example.serverconfig;

import com.example.serverconfig.namespaces.*;
import com.tailf.conf.*;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiException;
import com.tailf.maapi.MaapiRetryableOp;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.*;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import java.io.IOException;
import java.lang.StringBuilder;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class serverconfigRFS {
  private static Logger log = LogManager.getLogger(serverconfigRFS.class);
  private String deviceName;
  private String serverName;
  private Semaphore sem = new Semaphore(1);
  private final NcsMain main;

  public serverconfigRFS(NcsMain main) {
        this.main = main;
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
   *                  This object may be used to transfer
   *                  additional information between consecutive
   *                  calls to the create callback.  It is always
   *                  null in the first call. I.e. when the service
   *                  is first created.
   * @return Properties the returning opaque instance
   * @throws ConfException
   */

  @ServiceCallback(servicePoint="dns-config-servicepoint",
                   callType=ServiceCBType.CREATE)
  public Properties createdns(ServiceContext context,
                              NavuNode service,
                              NavuNode ncsRoot,
                              Properties opaque)
      throws ConfException {

    Template dnsTemplate = new Template(context, "dns-config-template");
    TemplateVariables dnsVars = new TemplateVariables();
    try {
      log.info("Service create(service="
               + service.getKeyPath().toString() + ")");
      String serverName = service.leaf("server").value().toString();
      String serverIp = ncsRoot.getParent()//
                               .container(serverConfig.uri)//
                               .container("servers").list("server")//
                               .elem(serverName).leaf("ip").value()//
                               .toString();
      /* Acquire a seemaphore which only purpose is to enable simulating
         a config update conflict before continuing */
      log.info("DNS service - try acquiring the semaphore");
      sem.acquire();
      dnsVars.putQuoted("DNS_IP", serverIp);
      // apply the template with the variable
      dnsTemplate.apply(service, dnsVars);
      log.info("Service create done(service="
               + service.getKeyPath().toString() + ")");
      sem.release();
    } catch (Exception e) {
      throw new DpCallbackException(e.getMessage(), e);
    }
    return opaque;
  }

  @ServiceCallback(servicePoint="ntp-config-servicepoint",
                   callType=ServiceCBType.CREATE)
  public Properties createntp(ServiceContext context,
                              NavuNode service,
                              NavuNode ncsRoot,
                              Properties opaque)
      throws ConfException {
    try (Maapi maapi = new Maapi(main.getAddress())) {
      log.info("Service create(service="
               + service.getKeyPath().toString() + ")");
      String serverName = service.leaf("server").value().toString();
      String serverIp = ncsRoot.getParent().container(serverConfig.uri)//
                               .container("servers").list("server")//
                               .elem(serverName).leaf("ip")//
                               .value().toString();

      /* The below transaction will cause a conflict and trigger an
         automatic retry */
      maapi.startUserSession("admin", "example", new String[] {"admin"});
      NavuContext ncontext = new NavuContext(maapi);
      ncontext.startRunningTrans(Conf.MODE_READ_WRITE);
      NavuContainer root = new NavuContainer(ncontext);
      NavuContainer scRoot = root.container(new serverConfig().hash());
      NavuLeaf ipLeaf = scRoot.container("servers").list("server")//
                              .elem(serverName).leaf("ip");
      int start = serverIp.lastIndexOf(".");
      StringBuilder builder = new StringBuilder();
      builder.append(serverIp.substring(0, start));
      builder.append(".121");
      serverIp = builder.toString();
      ipLeaf.set(serverIp);
      ncontext.applyClearTrans();

      TemplateVariables ntpVars = new TemplateVariables();
      ntpVars.putQuoted("NTP_IP", serverIp);
      // apply the template with the variable
      Template ntpTemplate = new Template(context,
                                          "ntp-config-template");
      ntpTemplate.apply(service, ntpVars);
      log.info("Service create done(service="
               + service.getKeyPath().toString() + ")");
    } catch (Exception e) {
      throw new DpCallbackException(e.getMessage(), e);
    }
    return opaque;
  }

  /**
   * Acquire semaphore action
   */
  @ActionCallback(callPoint="acquire-sem", callType=ActionCBType.ACTION)
  public ConfXMLParam[] acquiresem(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
      throws DpCallbackException {
    try {
      log.info("Acquire action");
      sem.acquire();
      return new ConfXMLParam[] {
        new ConfXMLParamValue("sc", "result", new ConfBool(true))};
    } catch (Exception e) {
      throw new DpCallbackException("Acquire semaphore action failed",
                                    e);
    }
  }

  /**
   * Release semaphore action
   */
  @ActionCallback(callPoint="release-sem", callType=ActionCBType.ACTION)
  public ConfXMLParam[] releasesem(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
      throws DpCallbackException {
    try {
      log.info("Release action");
      sem.release();
      return new ConfXMLParam[] {
        new ConfXMLParamValue("sc", "result", new ConfBool(true))};
    } catch (Exception e) {
      throw new DpCallbackException("Release semaphore action failed",
                                    e);
    }
  }

  /**
   * Update NTP action
   */
  @ActionCallback(callPoint="update-ntp", callType=ActionCBType.ACTION)
  public ConfXMLParam[] updatentp(DpActionTrans trans, ConfTag name,
                                  ConfObject[] kp, ConfXMLParam[] params)
      throws DpCallbackException {
    try (Maapi maapi = new Maapi(main.getAddress())) {
      String deviceName = params[0].getValue().toString();
      String serverName = params[1].getValue().toString();
      maapi.startUserSession("admin", "example", new String[] {"admin"});
      NavuContext context = new NavuContext(maapi);
      context.startRunningTrans(Conf.MODE_READ_WRITE);
      NavuContainer root = new NavuContainer(context);
      NavuContainer scRoot = root.container(new serverConfig().hash());
      String serverIp = scRoot.container("servers").list("server")//
                              .elem(serverName).leaf("ip").value().toString();

      log.info("Update NTP without retry action - try acquiring the"
               + " semaphore");
      sem.acquire();
      NavuContainer ncsRoot = root.container(new Ncs().hash());
      ncsRoot.container("devices").list("device").elem(deviceName)//
             .container("config").container("sys").container("ntp")//
             .list("server").create(serverIp);
      try {
        context.applyClearTrans();
      } catch (NavuException e) {
        log.error(e);
      }
      sem.release();
      log.info("Update NTP without retry action - done");
    } catch (Exception e) {
      throw new DpCallbackException("Update NTP action failed", e);
    }
    return new ConfXMLParam[] {
      new ConfXMLParamValue("sc", "result", new ConfBool(true))};
  }

  /**
   * Update NTP with retry action
   */
  @ActionCallback(callPoint="update-ntp-retry", callType=ActionCBType.ACTION)
  public ConfXMLParam[] updatentpretry(DpActionTrans trans, ConfTag name,
                                       ConfObject[] kp, ConfXMLParam[] params)
      throws DpCallbackException {
    try (Maapi maapi = new Maapi(main.getAddress())) {
      deviceName = params[0].getValue().toString();
      serverName = params[1].getValue().toString();
      maapi.startUserSession("admin", "example", new String[]{"admin"});
      log.info("Update NTP with retry action");
      maapi.ncsRunWithRetry(new MyProvisioningOp());
      log.info("Update NTP with retry action - finish");
      maapi.endUserSession();
      return new ConfXMLParam[] {
        new ConfXMLParamValue("sc", "result", new ConfBool(true))};
    } catch (Exception e) {
      throw new DpCallbackException("Update NTP action failed", e);
    }
  }

  class MyProvisioningOp implements MaapiRetryableOp {
    public boolean execute(Maapi maapi, int tid) throws IOException,
                           ConfException, MaapiException {
      NavuContext context = new NavuContext(maapi, tid);
      NavuContainer root = new NavuContainer(context);
      NavuContainer scRoot = root.container(new serverConfig().hash());
      String serverIp = scRoot.container("servers").list("server")//
                              .elem(serverName).leaf("ip").value().toString();

      log.info("Update NTP with retry action - try acquiring the"
               + " semaphore");
      try {
        sem.acquire();
        NavuContainer ncsRoot = root.container(new Ncs().hash());
        ncsRoot.container("devices").list("device").elem(deviceName)//
               .container("config").container("sys").container("ntp")//
               .list("server").create(serverIp);
        sem.release();
      } catch (InterruptedException e) {
        throw new ConfException("Retry NTP server config update failed",
                                e);
      }
      log.info("Update NTP with retry action - done");
      return true;
    }
  }
}
