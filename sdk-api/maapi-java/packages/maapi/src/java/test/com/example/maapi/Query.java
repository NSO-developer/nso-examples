package com.example.maapi;


import gnu.getopt.Getopt;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.maapi.ResultType;
import com.tailf.maapi.ResultTypeString;
import com.tailf.maapi.ResultTypeKeyPath;
import com.tailf.maapi.ResultTypeKeyPathValue;
import com.tailf.maapi.ResultTypeTag;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfPath;
import com.tailf.maapi.QueryResult;


import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.net.UnknownHostException;
import java.net.InetAddress;
import com.tailf.conf.Conf;
import com.tailf.maapi.MaapiException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 *
 * The example will show how to use the query API to start query
 * against the running db, run the ant query target as follow:
 *
 *  ant query -Darg="'<Query>' '<selects>' -n '<num>' -c '<chunksize>' -o
 *  '<inital-offset>' -x '<inital-context>' -r [t|v|p|s]
 *
 * Query - XPath Query
 *
 * selects - Nodes to select,could also be XPath expression,usually
 *           lists the nodes to fetch, analogous to SQL select query:
 *           Select name,address,port from ..
 *
 * num - Number o times to iterate over the query results,
 *       (show how to reset and refetch results)
 *
 * chunksize - Chunksize, how many results will the iterator fetch under
 *             iteration. The smaller value the bigger chunks.
 *
 * inital-offset - The iterate offset where do i start
 *
 * inital-context - The XPath query context default '/'
 *
  * [t|v|p|s] - Result format as:
 *  t -  ConfXMLParm
 *  v -  KeyPath and Value
 *  p -  KeyPath
 *  s -  String
 *
 * example:
 * ant query -Darg="'/ncs:devices/device' 'name,address,port' -n 1 -c 3 -o 1
 *  -x '/' -r t -d"
 *
 *
 */


public class Query<T extends ResultType> {

    private static Logger LOGGER = LogManager.getLogger(Query.class);
    int opt_chunk_size = 100;
    int opt_initial_offset = 1;
    int opt_number_of_times = 1;
    int opt_reset_after_first = 0;
    String opt_initial_ctxt;
    int port = Conf.NCS_PORT;
    int th = -1;
    InetAddress addr;
    Maapi maapi;
    QueryResult<? extends ResultType> rs;


    public Query(InetAddress addr,int chunksize, int offset, int numtimes,
                 String ctxt)
        {
            this.addr = addr;
            this.opt_chunk_size = chunksize;
            this.opt_initial_offset = offset;
            this.opt_number_of_times = numtimes;
            this.opt_initial_ctxt = ctxt;
        }

    public void maapiSock() {
        try {
            Socket s =
                new Socket(addr, port);
            maapi = new Maapi(s);

        } catch(IOException e) {
            LOGGER.error("",e);

        } catch(ConfException e){
            LOGGER.error("",e);
        }
    }

    public void reset() {
        try {
            rs.reset();
        } catch(ConfException e) {
            LOGGER.error("",e);
        } catch(IOException e) {
            LOGGER.error("",e);
        }
    }

    public void stop() {
        try{
            rs.stop();
        }catch(ConfException e){
            LOGGER.error("",e);
        }catch(IOException e){
            LOGGER.error("",e);
        }
    }

    public QueryResult<? extends ResultType> queryStart(String query,
                                  List<String> selects,
                                  Class<? extends ResultType> type)
    {
        try {
            rs =
                maapi.queryStart(th,
                                 query,
                                 this.opt_initial_ctxt,
                                 this.opt_chunk_size,
                                 this.opt_initial_offset,
                                 selects,
                                 type);
        } catch(IOException e) {
            LOGGER.error("",e);
        } catch(ConfException e) {
            LOGGER.error("",e);
        }
        return rs;
    }

    public void maapiStartTrans()
    {

        try {
            th = maapi.startTrans(Conf.DB_RUNNING,
                                  Conf.MODE_READ);
        } catch(MaapiException e) {
            LOGGER.error("Could not create a transaction" , e);
        } catch(ConfException e) {
            LOGGER.info("",e);
        } catch(IOException e) {
            LOGGER.info("",e);
        }
    }


    public void maapiUsess()
    {
        try {
            maapi.startUserSession("admin", "maapi", new String[] {"admin"});
        }catch(MaapiException e){
            LOGGER.error("Could not create a session",e);
        }catch(ConfException e){
            LOGGER.error("Could not create a session",e);
        }catch(UnknownHostException e){
            LOGGER.error("",e);
        }catch(IOException e){
            LOGGER.error("",e);
        }
    }

    public static void usage()
    {
        StringBuffer usage =new StringBuffer();
        usage.append("USAGE of Query Example \n");
        usage.append("\n");
        usage.append("ant query -Darg=\"'<Query>' '<selects>' -n '<num>' -c");
        usage.append("'<chunksize>' -o  '<inital-offset>' -x ");
        usage.append("'<inital-context>' -r [t|v|p|s]\"\n");
        usage.append("\n");
        usage.append("Query          - XPath Query \n");
        usage.append("selects        - Nodes to select,could also be ");
        usage.append("XPath expression,usually lists the nodes to");
        usage.append("fetch, (analogous to SQL select query:");
        usage.append("Select name,address,port from ..)");
        usage.append("\n");
        usage.append("num            - Number o times to iterate over ");
        usage.append("the query results,");
        usage.append("(show how to reset and refetch results)");
        usage.append("\n");
        usage.append("chunksize      - Chunksize, how many results will the ");
        usage.append("iterator fetch under iteration. The smaller value");
        usage.append("the bigger chunks.");
        usage.append("\n");
        usage.append("inital-offset  - The iterate offset where do i start");
        usage.append("\n");
        usage.append("inital-context - The XPath query context default '/'");
        usage.append("[t|v|p|s] - Result format as:\n");
        usage.append("\t\t t -  ConfXMLParm\n");
        usage.append("\t\t v -  KeyPath and Value\n");
        usage.append("\t\t p -  KeyPath \n");
        usage.append("\t\t s -  String \n");
        usage.append("\t\n");
        usage.append("Example:\n");
        usage.append("ant query -Darg=\"'/ncs:devices/device' ");
        usage.append("'name,address,port' -n 1 -c 3 -o 1 -x '/' -r t -d\" ");
        System.out.println(usage.toString());
        System.exit(1);
    }

    public static void main(String[] argv) throws UnknownHostException {
        //System.out.println("ARGV:" + Arrays.toString(argv));
        //System.out.println("argv.length:" + argv.length);
        if(argv.length != 13)
            usage();


        String query = argv[0];
        String[] sel = argv[1].split(",");
        List<String> selects = new ArrayList<String>();

        for(String select: sel)  selects.add(select);


        String[] argv2 = new String[argv.length-2];
        System.arraycopy(argv,2,argv2,0,argv2.length);

        InetAddress addr = InetAddress.getByName("127.0.0.1");
        Class<? extends ResultType> opt_res = ResultTypeString.class;
        int opt_chunk_size = 100;
        int opt_initial_offset = 1;
        int opt_number_of_times = 1;
        int debuglevel = 0;
        String opt_ctxt_node = null;
        System.out.println("arg.length:" + argv.length);

        int o;
        //xString arg;
        Getopt g = new Getopt("Query",argv2, "dp:r:c:o:x:n:t");
        while ((o = g.getopt()) != -1) {
            switch (o) {
            case 'd':
                debuglevel++;
                break;
            case 'p':
                addr = InetAddress.getByName(g.getOptarg());
                break;
            case 'r':
                String arg = g.getOptarg();
                char r = arg.charAt(0);
                switch (r) {
                case 't': opt_res = ResultTypeTag.class; break;
                case 'v': opt_res = ResultTypeKeyPathValue.class; break;
                case 'p': opt_res = ResultTypeKeyPath.class; break;
                case 's':
                default:
                    opt_res = ResultTypeString.class;
                }
                break;
            case 'c':
                opt_chunk_size = Integer.parseInt(g.getOptarg());
                break;
            case 'o':
                opt_initial_offset = Integer.parseInt(g.getOptarg());
                break;
            case 'x':
                opt_ctxt_node = g.getOptarg();
                break;
            case 'n':
                opt_number_of_times = Integer.parseInt(g.getOptarg());
                break;
            default:
                System.out.println("Unknown option:" + o);
                System.exit(1);
            }
        }
        Logger loggerMaapi = LogManager.getLogger("com.tailf.maapi");
        Logger loggerConf  = LogManager.getLogger("com.tailf.conf");

        switch(debuglevel){
        case 1: Configurator.setLevel(loggerMaapi.getName(), Level.INFO);/*loggerMaapi.setLevel(Level.INFO);*/   break;
        case 2: Configurator.setLevel(loggerMaapi.getName(), Level.DEBUG);/*loggerMaapi.setLevel(Level.DEBUG);*/ break;
        case 3:
            Configurator.setLevel(loggerMaapi.getName(), Level.DEBUG);/*loggerMaapi.setLevel(Level.DEBUG);*/
            Configurator.setLevel(loggerConf.getName(), Level.DEBUG);/*loggerConf.setLevel(Level.DEBUG);*/
            break;

        }
        Query<? extends ResultType> rq = new Query<ResultType>(addr,
                                                   opt_chunk_size,
                                                   opt_initial_offset,
                                                   opt_number_of_times,
                                                   opt_ctxt_node);
        rq.maapiSock();
        rq.maapiUsess();
        rq.maapiStartTrans();

        QueryResult<? extends ResultType> qR1 =
            rq.queryStart(query,selects,opt_res);

        for(int n = 0; n < opt_number_of_times; n++){
            if (n > 0) {
                rq.reset();
            }
            System.out.println("n:" + n);
            for(QueryResult.Entry<? extends ResultType> entry : qR1){
                List<? extends ResultType> rsValue = entry.value();

                for(ResultType typ: rsValue){
                    if(opt_res == ResultTypeTag.class){
                        ResultTypeTag rsTag =  (ResultTypeTag)typ;
                        System.out.println(" " + rsTag.tag());
                    }else if(opt_res == ResultTypeKeyPathValue.class){
                        ResultTypeKeyPathValue kpv =
                            (ResultTypeKeyPathValue)typ;
                        System.out.print(new ConfPath(kpv.keyPath())
                                         .toString());
                        System.out.println(" " + kpv.confValue());

                    }else if(opt_res == ResultTypeKeyPath.class){
                        ResultTypeKeyPath kpv =
                            (ResultTypeKeyPath)typ;
                        System.out.println(new ConfPath(kpv.keyPath())
                                           .toString());

                    }else if(opt_res == ResultTypeTag.class){
                        ResultTypeTag rst =
                        (ResultTypeTag)typ;
                        System.out.println(rst.tag());
                    }else if(opt_res == ResultTypeString.class){
                        ResultTypeString rss = (ResultTypeString)typ;
                        System.out.println(rss.stringValue());
                    }
                }
            }
        }
        rq.stop();
    }

}
