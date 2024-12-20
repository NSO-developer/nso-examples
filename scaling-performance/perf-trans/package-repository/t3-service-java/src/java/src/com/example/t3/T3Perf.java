package com.example.t3;

import com.example.t3.namespaces.t3;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfUInt64;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfXMLParam;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.DpTrans;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.annotations.TransValidateCallback;
import com.tailf.dp.annotations.ValidateCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.proto.TransValidateCBType;
import com.tailf.dp.proto.ValidateCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.NcsMain;
import com.tailf.ncs.ns.Ncs;
import java.io.IOException;
import java.lang.Number;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NSO transaction performance example. Implements service and validation
 * callbacks and an action to calibrate CPU load.
 */
public class T3Perf {
  private static Logger LOGGER = LogManager.getLogger(T3Perf.class);

  /* CPU hogger */
  private long factorial(long n) {
    long num = 1;
    while (n >= 1) {
      num = num * n;
      n = n - 1;
    }
    return num;
  }

  /*
   * Do some CPU hogging to simulate work
   * Note that, while rarely feasible, we can here sometimes divide the
   * workload from a large transaction into multiple processes to utilize
   * all CPU cores. Likely, we rather want to rely on large workloads being
   * divided up into several transactions that will be handled in parallel
   * by NSO, and do the work in a single process here.
   */
  private void simwork(long nf, long nw) {
    long i;
    for (i = 0; i < nw; i++) {
      factorial(nf);
    }
  }

  /**
   * Service setting simulated device work.
   */
  @ServiceCallback(servicePoint = "devs-servicepoint",
                   callType = ServiceCBType.CREATE)
  public Properties createDset(ServiceContext context,
      NavuNode service,
      NavuNode ncsRoot,
      Properties opaque) throws DpCallbackException {
    LOGGER.info("Service create" + service);
    try {
      NavuList devList = ncsRoot.container("devices").list("device");
      for (NavuNode device : devList) {
        device.container("config").container("sys").leaf("trans-delay")
            .set(service.leaf("dev-delay").value());
      }
    } catch (Exception e) {
      throw new DpCallbackException("Device settings service failed", e);
    }
    return opaque;
  }

  /**
   * Service callback simulating service handling work and configuring devices.
   */
  @ServiceCallback(servicePoint = "t3-servicepoint",
                   callType = ServiceCBType.CREATE)
  public Properties createT3(ServiceContext context,
      NavuNode service,
      NavuNode ncsRoot,
      Properties opaque) throws DpCallbackException {
    LOGGER.info("Service create" + service);
    try {
      // Get settings from CDB
      long nf = ((ConfUInt32) ncsRoot.getParent().container(t3.uri)//
          .container("t3s")//
          .container("t3-settings")//
          .leaf("nfactorial").value())//
          .longValue();
      long nw = ((ConfUInt32) ncsRoot.getParent().container(t3.uri)//
          .container("t3s").container("t3-settings")//
          .leaf("nwork").value()).longValue();
      int ndtrans = (int) ((ConfUInt32) ncsRoot.getParent().container(t3.uri)//
          .container("t3s").container("t3-settings")//
          .leaf("ndtrans").value()).longValue();

      /*
       * Do some CPU hogging to simulate work
       * nw = number of "work items" - work item calibrated by the action
       * nf = number of factorials - same as used when calibrating
       */
      LocalDateTime startTime = LocalDateTime.now();
      simwork(nf, nw);

      if (ndtrans > 0) {
        // Config devices
        NavuList devList = ncsRoot.container("devices").list("device");
        int devN = devList.size();
        int startId = (int) ((ConfUInt32) service.leaf("id").value())
            .longValue();
        for (int i = 0; i < ndtrans; i++) {
          int pos = (startId * ndtrans + i) % devN;
          int th = ncsRoot.context().getMaapiHandle();
          int j = 0;
          for (NavuNode device : devList) {
            if (j == pos) {
              device.container("config").container("sys")
                  .container("interfaces").list("interface")
                  .create("I" + i + "@" + th);
              break;
            }
            j++;
          }
        }
      }

      LOGGER.info("Wall clock time service: "
                  + ChronoUnit.SECONDS.between(startTime,
                                               LocalDateTime.now())
                  + " for " + service);
    } catch (Exception e) {
      throw new DpCallbackException("T3 service failed", e);
    }
    return opaque;
  }

  @TransValidateCallback(callType = TransValidateCBType.INIT)
  public void init(DpTrans trans) throws DpCallbackException {
    LOGGER.info("init validate(uinfo=" + trans.getUserInfo());
  }

  @TransValidateCallback(callType = TransValidateCBType.STOP)
  public void stop(DpTrans trans) throws DpCallbackException {
    LOGGER.info("stop validate(uinfo=" + trans.getUserInfo());
  }

  /**
   *  Validation callback simulating validation work.
   */
  @ValidateCallback(callPoint = "t3-valpoint",
                    callType = ValidateCBType.VALIDATE)
  public void validate(DpTrans trans, ConfObject[] kp,
      ConfValue newval) throws DpCallbackException {
    LOGGER.info("Validating " + new ConfPath(kp));
    try {
      // Get settings from CDB
      Maapi maapi = new Maapi(trans.getSocket().getRemoteSocketAddress());
      maapi.startUserSession("admin", "example", new String[] { "admin" });
      NavuContext context = new NavuContext(maapi);
      context.startOperationalTrans(Conf.MODE_READ);
      NavuContainer root = new NavuContainer(context);
      NavuContainer t3Root = root.container(new t3().hash());
      long nf = ((ConfUInt32) t3Root.container("t3s")//
          .container("t3-settings")//
          .leaf("nfactorial").value()).longValue();
      context.finishClearTrans();

      context.startRunningTrans(Conf.MODE_READ);
      root = new NavuContainer(context);
      t3Root = root.container(new t3().hash());
      long nw = ((ConfUInt32) t3Root.container("t3s")//
          .container("t3-settings")//
          .leaf("nwork").value()).longValue();
      context.finishClearTrans();

      /*
       * Do some CPU hogging to simulate work
       * nw = number of "work items" - work item calibrated by the action
       * nf = number of factorials - same as used when calibrating
       */
      LocalDateTime start = LocalDateTime.now();
      simwork(nf, nw);
      LOGGER.info("Wall clock time validating: " + ChronoUnit.SECONDS
          .between(start, LocalDateTime.now()) + " for " + new ConfPath(kp));
    } catch (Exception e) {
      throw new DpCallbackException("Validation failed", e);
    }
  }

  /**
   * Action calibrating the CPU load.
   */
  @ActionCallback(callPoint = "t3-cputime", callType = ActionCBType.ACTION)
  public ConfXMLParam[] action(DpActionTrans trans, ConfTag name,
      ConfObject[] kp, ConfXMLParam[] params)
      throws DpCallbackException {
    LOGGER.info("action(uinfo=" + trans.getUserInfo() + ", name=" + name + ")");
    try {
      long ival = 10;
      long val = ival;
      long ts;
      LocalDateTime start;

      /*
       * Calculate how many factorial are needed to keep a CPU core busy for
       * one second
       */
      while (true) {
        start = LocalDateTime.now();
        factorial(val);
        ts = ChronoUnit.MILLIS.between(start, LocalDateTime.now());
        if (ts > 1050) {
          val -= ival;
          ival = ival / 2;
        } else if (ts < 1) {
          ival = ival * 100;
        } else if (ts < 10) {
          ival = ival * 10;
        } else if (ts < 100) {
          ival = ival * 3;
        } else if (ts > 1000) {
          break;
        }
        val += ival;
      }

      /*
       * Store the result in CDB so that the service and validation
       * callbacks can use it to simulate CPU load
       */
      Maapi maapi = new Maapi(trans.getSocket().getRemoteSocketAddress());
      maapi.startUserSession("admin", "example", new String[] { "admin" });
      NavuContext context = new NavuContext(maapi);
      context.startOperationalTrans(Conf.MODE_READ_WRITE);
      NavuContainer root = new NavuContainer(context);
      NavuContainer t3Root = root.container(new t3().hash());
      t3Root.container("t3s").container("t3-settings")//
          .leaf("nfactorial").set(val + "");
      context.applyClearTrans();
      LOGGER.info("action(uinfo=" + trans.getUserInfo() + ", name=" + name
                  + ") ts=" + ts);
    } catch (Exception e) {
      throw new DpCallbackException("Calibrate CPU time action failed", e);
    }
    return null;
  }
}
