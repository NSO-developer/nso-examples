package com.example.maapi;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.maapi.ResultTypeKeyPathValue;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfObject;
import com.tailf.maapi.QueryResult;


import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.Arrays;
import java.util.List;
import com.tailf.conf.Conf;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 *
 * The example will show how to use the query API to start query
 * against the running db, run the ant query target as follow:
 *
 *
 */


public class SimpleQuery1{

    private static Logger log =
        LogManager.getLogger(Query.class);

    private int th = -1;
    private Maapi maapi;

    public static void main(String[] args) {
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }
        new SimpleQuery1(address).printQueryResult();
    }

    public SimpleQuery1(SocketAddress address) {
        try{
            maapi = new Maapi(address);

            maapi.startUserSession("jb", "maapi", new String[] {"oper"});

            th =  maapi.startTrans(Conf.DB_RUNNING,
                                   Conf.MODE_READ_WRITE);
        }catch(Exception e){
            log.error("Error:", e);
            System.exit(1);
        }
    }

    public void printQueryResult() {
        QueryResult<ResultTypeKeyPathValue> resultSet = null;
        //get all the enabled interfaces on all routers
        String xPathQuery =
            "/ncs:devices/device/config" +
            "/r:sys/interfaces/interface[enabled='true']";

        try{

            List<String> retrivedLeafs =
                Arrays.asList("name","description");// ,"description","speed",
                              // "duplex","mtu", "mac",
                              // "speed");

            Class<ResultTypeKeyPathValue> resultType =
                ResultTypeKeyPathValue.class;

            resultSet = maapi.<ResultTypeKeyPathValue>queryStart(th,
                                                                 xPathQuery,
                                                                 null,
                                                                 3,
                                                                 1,
                                                                 retrivedLeafs,
                                                                 resultType);


            //for each entry in the resultset from our query
            for(QueryResult.Entry<ResultTypeKeyPathValue> entry : resultSet){
                List<ResultTypeKeyPathValue> entryValues = entry.value();
                //for each leafs that we wanted.
                log.info("Entry:");
                for(ResultTypeKeyPathValue entryLeaf: entryValues){
                    ConfObject[] keyPath = entryLeaf.keyPath();
                    ConfValue value = entryLeaf.confValue();
                    log.info("\t" + new ConfPath(keyPath).toString() +
                             " --> "  +
                             value);
                }
            }


        }catch(Exception e){
            log.error("Exception encountered", e);
            System.exit(1);
        }finally{
            try{
                resultSet.stop();
                maapi.close();
            }catch(Exception ee){
                log.fatal("Exception when closing socket",ee); }
        }

    }

}
