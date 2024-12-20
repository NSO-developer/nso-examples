package com.example.qinqalarm;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfException;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ns.Ncs;

public class SessionWriterStatus {

    private static Logger LOGGER = LogManager.getLogger(
            SessionWriterStatus.class);


    public enum QinqServiceStatus {
        undefined,
        cleared,
        alarm
    }


    public void setQinqServiceStatus(SocketAddress address, String serviceName,
            QinqServiceStatus status) throws IOException, ConfException {

        try (Maapi maapi = new Maapi(address)) {
            maapi.startUserSession("system", "system");

            NavuContext ncontext = new NavuContext(maapi);
            int th = ncontext.startOperationalTrans(Conf.MODE_READ_WRITE);
            NavuContainer root = new NavuContainer(ncontext);

            NavuContainer ncsRoot = root.container(Ncs.hash);
            NavuContainer service =  ncsRoot.container(Ncs._services).
                                        list(Ncs._service).elem(serviceName);

            LOGGER.info("setQinqServerStatus : service=" + service);

            if (service != null) {
                NavuContainer type = service.container(Ncs._type);
                List<NavuNode> nns = type.getSelectCaseAsNavuNode(
                        Ncs._service_type_choice_);

                if ((nns != null) && (nns.size() == 1)) {
                    NavuNode nn = nns.get(0);
                    NavuLeaf stat = nn.leaf("status");

                    stat.set(status.toString());
                }
            }
            maapi.applyTrans(th, false);
            maapi.finishTrans(th);
            maapi.endUserSession();
        }
    }
}
