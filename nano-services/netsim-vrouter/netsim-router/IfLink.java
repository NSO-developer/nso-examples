/*********************************************************************
 * NSO Nano service example.
 *
 * Router interface initialization example.
 *
 * See the README file for more information
 *********************************************************************/

import com.tailf.cdb.*;
import com.tailf.conf.*;
import com.tailf.dp.Dp;
import com.tailf.dp.DpNotifStream;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiCursor;
import com.tailf.maapi.MaapiUserSessionFlag;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IfLink {
  private static final Logger log = LogManager.getLogger(IfLink.class);

  private static final String DAEMON_NAME = "if-link";
  private static final String CONFD_ADDR = "127.0.0.1";
  private static int confdPort;

  private static void sendLinkStatusNotif(String ifName, String linkState,
                                          DpNotifStream stream) {
    try {
      router r = new router();
      int upDown = r.r_up;
      if (linkState.equals("down")) {
        upDown = r.r_down;
      }
      ConfXMLParam[] vals = new ConfXMLParam[] {
        new ConfXMLParamStart(r.hash(), r.r_link_status),
        new ConfXMLParamValue(r.hash(), r.r_name,
            new ConfBuf(ifName)),
        new ConfXMLParamValue(r.hash(), r.r_status,
            new ConfEnumeration(upDown)),
        new ConfXMLParamStop(r.hash(), r.r_link_status)
      };
      stream.send(ConfDatetime.getConfDatetime(), vals);
      log.info("Sent link status NETCONF notification");
    } catch (ConfException | IOException e) {
      log.error("Semd notification exception:\n" + e);
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static class SubThread extends Thread {
    public void run() {
      Maapi maapi = null;
      try {
        Socket maapiSocket = new Socket(CONFD_ADDR, confdPort);
        maapi = new Maapi(maapiSocket);
        maapi.loadSchemas();
        String user = "admin";
        String context = "system";
        String[] groups = {user};
        maapi.startUserSession(user, context, groups);
      } catch (ConfException | IOException e) {
        log.error("Could not start MAAPI session!\n" + e);
        e.printStackTrace();
        System.exit(1);
      }

      Socket subsSocket = null;
      try {
        subsSocket = new Socket(CONFD_ADDR, confdPort);
      } catch (IOException e) {
        log.error("Could not open subscription socket to"
                  + " ConfD!\n" + e);
        e.printStackTrace();
        System.exit(1);
      }

      Cdb cdb = null;
      CdbSubscription cdbSubscriber = null;
      int subPoint = -1;
      try {
        cdb = new Cdb("if-admin", subsSocket);
        cdbSubscriber = cdb.newSubscription();
        EnumSet<CdbSubscrConfigFlag> aflag = EnumSet.of(
            CdbSubscrConfigFlag.CDB_SUB_WANT_ABORT_ON_ABORT);
        router r = new router();
        subPoint =
          cdbSubscriber.subscribe(
            CdbSubscriptionType.SUB_RUNNING_TWOPHASE, aflag, 3,
            r.hash, "/r:sys/interfaces/interface/");
        cdbSubscriber.subscribeDone();
      } catch (ConfException | IOException e) {
        log.error("Could not connect to CDB!\n" + e);
        e.printStackTrace();
        System.exit(1);
      }
      log.info("CDB prepare and commit phase subscriber initialization done");

      try {
        while (true) {
          String adminState = "unlocked";
          int tid = maapi.startTrans(Conf.DB_OPERATIONAL,
                                     Conf.MODE_READ_WRITE);
          try {
            int[] triggeredSubs = cdbSubscriber.read();
            EnumSet<CdbGetModificationFlag> flags = EnumSet.of(
                CdbGetModificationFlag.CDB_GET_MODS_INCLUDE_LISTS);
            if (triggeredSubs[0] != subPoint) {
              continue;
            }
            if (cdbSubscriber.getLatestNotificationType()
                == CdbNotificationType.SUB_ABORT) {
              // Get a list of all the reverse changes done in the
              // aborted commit
              flags.add(
                  CdbGetModificationFlag.CDB_GET_MODS_REVERSE);
            }
            List<ConfXMLParam> changes =
                cdbSubscriber.getModifications(subPoint, flags,
                                             "/r:sys/interfaces");
            // Print the configuration changes
            String output = ModificationWriter.writeValues(changes);
            log.info("Subscription type "
                + cdbSubscriber.getLatestNotificationType().toString()
                + " with flags "
                + cdbSubscriber.getFlags().toString()
                + " for transaction from user \""
                + maapi.getUserSession(
                       (int) cdbSubscriber.getUserSession()).getUser()
                + "\" over " + maapi.getUserSession(
                       (int) cdbSubscriber.getUserSession()).getContext()
                + ":\n" + output);
            String ifName = "";
            String tagStr = "";
            for (ConfXMLParam param : changes) {
              String prevTag = tagStr;
              tagStr = param.getTag();
              if (param instanceof ConfXMLParamValue
                  && tagStr.equals("name") && prevTag.equals("interface")) {
                ConfValue val = (ConfValue) param.getValue();
                if (cdbSubscriber.getLatestNotificationType()
                    == CdbNotificationType.SUB_PREPARE) {
                  // Check admin-state in the prepare phase of the transaction
                  // as we can still abort the transaction here.
                  ifName = val.toString();
                  String ifAdmin = "unlocked";
                  try {
                    val = maapi.getElem(tid,
                                        "/r:sys/state/admin-state");
                    ifAdmin =
                      ConfValue.getStringByValue("/r:sys/"
                                                 + "state/admin-state", val);
                  } catch (ConfException e) {}
                  if (ifAdmin.equals("locked")) {
                    adminState = "locked";
                    // Transaction will be aborted
                    // Configuration changes rejected as admin-state is locked
                  }
                  log.info("Prepare ifName: " + ifName
                           + " adminState: " + adminState);
                }
                if (cdbSubscriber.getLatestNotificationType()
                    == CdbNotificationType.SUB_COMMIT) {
                  // Configuration change committed
                  ifName = val.toString();
                  if(maapi.exists(tid, "/r:sys/interfaces/interface{"
                                  + ifName + "}")) {
                    maapi.setElem(tid, "up",
                                  "/r:sys/interfaces/interface{"
                                  + ifName + "}/status/link");
                  }
                  log.info("Commit ifName: " + ifName
                           + " adminState: " + adminState);
                }
              }
            }
          } catch (Exception e) {
            log.error("Subscriber thread exception:\n" + e);
            e.printStackTrace();
            System.exit(1);
          } finally {
            if (cdbSubscriber.getLatestNotificationType()
                == CdbNotificationType.SUB_ABORT) {
              cdbSubscriber.sync(
                  CdbSubscriptionSyncType.DONE_PRIORITY);
            } else if (cdbSubscriber.getLatestNotificationType()
                       == CdbNotificationType.SUB_COMMIT) {
              maapi.applyTrans(tid, false);
              log.info("Link status updated for all "
                       + "configured interfaces");
              cdbSubscriber.sync(
                  CdbSubscriptionSyncType.DONE_PRIORITY);
            } else if (cdbSubscriber.getLatestNotificationType()
                       == CdbNotificationType.SUB_PREPARE
                       && adminState.equals("unlocked")) {
              cdbSubscriber.sync(
                  CdbSubscriptionSyncType.DONE_PRIORITY);
            } else {
              log.info("Transaction aborted. System admin state"
                       + "locked.\n");
              cdbSubscriber.abortTransaction(
                new CdbExtendedException(
                    CdbExtendedException.ERRCODE_APPLICATION,
                    null, null,
                    new ConfException("System admin state locked. Transaction "
                                      + "aborted")));
            }
          }
          maapi.finishTrans(tid);
        }
      } catch (ConfException | IOException e) {
        log.error("Subscriber thread exception:\n" + e);
        e.printStackTrace();
        System.exit(1);
      }
    }
  }

  public static void main(String[] arg) {
    confdPort = Integer.parseInt(arg[0]);
    Socket subsSocket = null;
    try {
      subsSocket = new Socket(CONFD_ADDR, confdPort);
    } catch (IOException e) {
      log.error("Could not open subscription socket to "
                + "ConfD!\n" + e);
      e.printStackTrace();
      System.exit(1);
    }

    Cdb cdb = null;
    CdbSubscription cdbSubscriber = null;
    int subPoint = -1;
    try {
      cdb = new Cdb(DAEMON_NAME, subsSocket);
      cdbSubscriber = cdb.newSubscription();
      router r = new router();
      subPoint =
        cdbSubscriber.subscribe(CdbSubscriptionType.SUB_OPERATIONAL, 3,
                                r.hash,
                                "/r:sys/interfaces/interface/status/link");
      cdbSubscriber.subscribeDone();
    } catch (ConfException | IOException e) {
      log.error("Could not connect to CDB!\n" + e);
      e.printStackTrace();
      System.exit(1);
    }
    log.info("CDB subscriber initialization done");

    Socket ctrlSocket = null;
    try {
      ctrlSocket = new Socket(CONFD_ADDR, confdPort);
    } catch (IOException e) {
      log.error("Could not open control socket to ConfD!\n" + e);
      e.printStackTrace();
      System.exit(1);
    }

    DpNotifStream stream = null;
    try {
      Dp dp = new Dp("link_daemon", ctrlSocket);
      stream = dp.createNotifStream("iflink");
    } catch (ConfException | IOException e) {
      log.error("Could not connect to the DP API!\n" + e);
      e.printStackTrace();
      System.exit(1);
    }
    log.info("DP NETCONF notification initialization done");

    try {
      // Wait for interface config changes and set the link status
      // Need its own thread as the changes trigger the oper subscriber
      SubThread subThread = new SubThread();
      subThread.start();
      log.info("Config subscriber initialization done");

      while (true) {
        try {
          // Wait for link status changes
          int[] triggeredSubs = cdbSubscriber.read();

          if (triggeredSubs[0] != subPoint) {
            continue;
          }

          // Get the link status changes
          EnumSet<CdbGetModificationFlag> flags = EnumSet.of(
              CdbGetModificationFlag.CDB_GET_MODS_INCLUDE_LISTS);
          List<ConfXMLParam> changes = cdbSubscriber.getModifications(
              subPoint, flags, "/r:sys/interfaces");
          String ifName = "";
          String linkState = "";
          String tagStr = "";
          for (ConfXMLParam param : changes) {
            String prevTag = tagStr;
            tagStr = param.getTag();
            if (param instanceof ConfXMLParamValue) {
              ConfValue val = (ConfValue) param.getValue();
              if (tagStr.equals("name")
                  && prevTag.equals("interface")) {
                ifName = val.toString();
              }
              if (tagStr.equals("link")) {
                linkState =
                  ConfValue.getStringByValue("/r:sys/interfaces/interface{"
                                             + ifName + "}/status/link", val);
                // Send a NETCONF notification with link status change
                sendLinkStatusNotif(ifName, linkState, stream);
              }
            }
          }
          String output = ModificationWriter.writeValues(changes);
          log.info("Modifications read by subscriber:\n" + output);

          cdbSubscriber.sync(
              CdbSubscriptionSyncType.DONE_OPERATIONAL);
        } catch (ConfException e) {
          log.debug("Connection to ConfD closed. Exiting...");
          System.exit(0);
        }
      }
    } catch (Exception e) {
      log.error("Main thread exception:\n" + e);
      e.printStackTrace();
      System.exit(1);
    }
  }
}

// Helper that print the interface changes to the log file
class ModificationWriter {

  private static final String INDENT_STR = "";
  private static final int INDENT_SIZE = 4;

  private static class IndentedValue {
    public String value;
    public int indent;

    public IndentedValue(String value, int indent) {
      this.value = value;
      this.indent = indent;
    }
  }

  private static IndentedValue processValue(ConfXMLParam param, int indent) {
    String tagStr = param.getTag();

    String outStr = "";
    int outIndent = indent;
    int prefixLen = indent;

    if (param instanceof ConfXMLParamStart) {
      // start a container/list entry creation/modification
      outStr = String.format("<%s>", tagStr);
      outIndent += INDENT_SIZE;
    } else if (param instanceof ConfXMLParamStop) {
      // exit from a processing of container/list entry creation/modification
      outStr = String.format("</%s>", tagStr);
      outIndent -= INDENT_SIZE;
      prefixLen = outIndent;
    } else if (param instanceof ConfXMLParamStartDel) {
      // deletion of a list entry / container
      outStr = String.format("<%s operation=\"delete\">", tagStr);
      outIndent += INDENT_SIZE;
    } else if (param instanceof ConfXMLParamLeaf) {
      // type empty leaf creation
      outStr = String.format("<%s/>", tagStr);
    } else if (param instanceof ConfXMLParamValue) {
      // deletion of leaf
      ConfObject val = param.getValue();
      if (val instanceof ConfNoExists) {
        outStr = String.format("<%s operation=\"delete\">", tagStr);
      } else {
        // regular leaf creation/modification
        outStr = String.format("<%s>%s</%s>", tagStr, val, tagStr);
      }
    }

    String prefix = "";
    if (prefixLen > 0) {
      prefix = String.format("%" + prefixLen + "s", INDENT_STR);
    }
    String suffix = String.format("%n");

    return new IndentedValue(prefix + outStr + suffix, outIndent);
  }

  // Allocate a string containing printout of the tagged value array.
  // Output follows a NETCONF edit-config message format without any headers.
  public static String writeValues(List<ConfXMLParam> values) {
    StringBuilder builder = new StringBuilder();
    int indent = 0;
    for (ConfXMLParam param : values) {
      IndentedValue out = processValue(param, indent);
      builder.append(out.value);
      indent = out.indent;
    }
    return builder.toString();
  }
}
