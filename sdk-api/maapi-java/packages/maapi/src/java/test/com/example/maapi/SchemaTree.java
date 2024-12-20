package com.example.maapi;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import com.tailf.maapi.MaapiSchemas;
import com.tailf.maapi.MaapiSchemas.CSEnum;
import com.tailf.maapi.MaapiSchemas.CSNode;
import com.tailf.maapi.MaapiSchemas.CSChoice;
import com.tailf.maapi.MaapiSchemas.CSType;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;

import com.tailf.navu.NavuNode;
import com.tailf.navu.NavuListEntry;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuLeaf;

import com.tailf.conf.ConfTypeDescriptor;
import com.tailf.conf.Conf;
import com.tailf.conf.ConfTag;

public class SchemaTree{

    public static void main(String[] args){
        SocketAddress address;
        if (args.length == 1) {
            address = UnixDomainSocketAddress.of(args[0]);
        } else if (args.length == 2) {
            address = new InetSocketAddress(args[0], Integer.valueOf(args[1]));
        } else {
            address = new InetSocketAddress("localhost", Conf.NCS_PORT);
        }
        System.out.println("ARGS " + address);

        try (Maapi maapi = new Maapi(address)){
            maapi.startUserSession("admin", "maapi", new String[] { "admin" });

            // Start a read transaction towards maapi
            int th   = maapi.startTrans(Conf.DB_RUNNING,
                                        Conf.MODE_READ);

            // Create a container to serve as a place holder for all
            // loaded modules. This container has no representation
            // in the YANG model
            NavuContainer modules =
                new NavuContainer(new NavuContext(maapi,th));

            // Get desired top level module, either from hash or uri
            NavuContainer ncs_module =
                modules.container("http://tail-f.com/ns/ncs");

            NavuContainer config_node = ncs_module
                    .container("devices")
                    .list("device")
                    .elem("ex0")
                    .container("config");
            printChildren(0, config_node);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    public static String stroffset(int offset){
        char[] chars = new char[offset];
        for (int i = 0; i < offset; i++) {
            chars[i] = ' ';
        }
        return new String(chars);
    }


    /**
     * Recursive printout of a schema tree with values
     * from and including given node
     *
     * @param offset
     *            indentation offset for printout, 0 at start.
     * @param node
     *            start node for printout
     */

    static void printChildren(int offset, NavuNode node) throws Exception{

        Collection<NavuNode> children = node.children();
        Iterator<NavuNode>  it = children.iterator();
        offset += 2;
        System.out.println();

        if(it.hasNext()){
            for(NavuNode child: node.children()){
                CSNode c = child.getInfo().getCsNode();
                System.out.print(stroffset(offset));

                String xtra = "" ;
                if(child.getInfo().isListEntry()) {
                    //extra information for a listEntry print
                    //its key
                    xtra = ((NavuListEntry)child).getKey().toString();
                }
                System.out.print("*<" + c.getTag() + xtra + ">*");
                System.out.print("(" + nodeInfoAsString(c)  + ")");


                //print Type information and
                //default value
                if(c.getType() != null){
                    CSType cstype = c.getType();
                    extractParentType(cstype,offset);

                    if (c.getDefval() != null) {
                        System.out.print(",Default:'" +
                                         c.getDefval() + "'");
                    } else if (c.getType().getDefval() != null) {
                        System.out.print(",Default:'" +
                                         c.getType()
                                         .getDefval() + "'");
                    } else
                        System.out.print(",No Default");
                }

                //Show the corresponding leaf value
                if(child.getInfo().isLeaf()){
                    if (child.getInfo().isEmptyLeaf()) {
                        if (((NavuLeaf)child).exists()) {
                            System.out.println();
                            System.out.print(stroffset(offset) + " EXISTS " +
                                             child.getKeyPath());
                        }
                    } else {
                        if (((NavuLeaf)child).exists()) {
                            System.out.println();
                            System.out.print(stroffset(offset) + " VALUE " +
                                child.getKeyPath() + " --> " +
                                ((NavuLeaf)child).value());
                        }
                    }
                }
                printCurrentCase(offset,child);
                printChildren(offset,child);
            }
        } else {
            //No more data has been found recurse the
            //raw MaapiSchemas Tree
            printChildren(offset,node.getInfo().getCsNode());
        }
    }

    public static void printCurrentCase(int offset,NavuNode node) throws
        Exception{
        if(!node.getInfo().isList()){
            NavuContext ctx = node.context();
            Maapi maapi = ctx.getMaapi();
            ConfTag tag = null;

            CSNode csNode = node.getInfo().getCsNode();
            List<CSChoice> choices = null;
            if((choices = csNode.getChoices()) != null){


                for(CSChoice choice: choices){
                    if((tag = maapi.getCase(ctx.getMaapiHandle(),
                                            choice.getTag(),
                                            node.getKeyPath())) != null){
                        System.out.println();
                        System.out.print(stroffset(offset) +
                                         "+CURRENT CASE " + tag);
                    }
                }
            }
        }
    }

    public static void printChildren(int offset, CSNode n)
        throws Exception{

        offset += 2;
        List<MaapiSchemas.CSNode> chlist = n.getChildren();
        if (chlist == null)
            return;

        Iterator<MaapiSchemas.CSNode> iter = n.getChildren().iterator();
        while (iter.hasNext()) {
            MaapiSchemas.CSNode c = iter.next();
            System.out.print(stroffset(offset));
            System.out.print("*<" + c.getTag() + ">*");
            System.out.print("(" + nodeInfoAsString(c)  + ")");

            if(c.getType() != null){
                CSType cstype = c.getType();
                extractParentType(cstype,offset);

                if (c.getDefval() != null) {
                    System.out.print(",Default:'" +
                                     c.getDefval() + "'");
                } else if (c.getType().getDefval() != null) {
                    System.out.print(",Default:'" + c.getType()
                                     .getDefval() + "'");
                } else
                    System.out.print(",No Default");

            }

            System.out.println();
            printNodeInfo(offset,c);
            printChildren(offset, c);

        }//while ........

    }

    static void extractParentType(CSType tt,int offset){
        if(tt.getNativeType() != 0){
            System.out.print(" TYPE:"+
                             new ConfTypeDescriptor(tt.getNativeType()) + " ");

        }else{
            if(tt.getParentType() != null)
                extractParentType(tt.getParentType(),offset);
            if( tt.getOpaque() != null)
                extractOpaque(tt.getOpaque(),offset);
        }

    }

    static void extractOpaque(Object opaque,int offset){
        if(opaque instanceof CSType[]){
            for(CSType type : (CSType[])opaque)
                extractParentType(type,offset);
        }else if(opaque instanceof CSEnum[]){
            CSEnum[] csenum = (CSEnum[])opaque;

            System.out.println();
            System.out.print(stroffset(offset) + "[ENUM:");
            boolean newline = false;
            for(int k = 0; k < csenum.length; k++){
                CSEnum e = csenum[k];
                if(k !=0 && k % 10 == 0){
                    System.out.println();
                    newline = true;
                }
                System.out.print((newline)? stroffset(offset):"");
                System.out.print(e.getName() + ":" +e.getValue()  +
                                 ((k == csenum.length-1)?"":","));
                newline = false;
            }
            System.out.print("]");
        }else
            System.out.print("opaque:" + opaque);
    }




    /**
     * Node info printout for a given node
     *
     * @param offset
     *            indentation offset for printout, 0 at start.
     * @param n
     *            node to printout
     */
    static void printNodeInfo(int offset, MaapiSchemas.CSNode n) {

        List<MaapiSchemas.CSChoice> colist = n.getChoices();
        if (colist != null) {
            System.out.println();
            System.out.print(stroffset(offset));
            System.out.print(stroffset(offset) + " +-[Choice : ");
            Iterator<MaapiSchemas.CSChoice> iter = colist.iterator();
            while (iter.hasNext()) {
                MaapiSchemas.CSChoice c = iter.next();
                System.out.print(c.getTag());
                List<MaapiSchemas.CSCase> calist = c.getCases();
                if (calist != null) {
                    Iterator<MaapiSchemas.CSCase> iter2 = calist.iterator();
                    System.out.print("{");
                    while (iter2.hasNext()) {
                        MaapiSchemas.CSCase ca = iter2.next();
                        System.out.print(ca.getTag());
                        List<MaapiSchemas.CSNode> nlist = ca.getNodes();
                        if (nlist != null) {
                            Iterator<MaapiSchemas.CSNode> iter3 =
                                nlist.iterator();
                            System.out.print("(");
                            while (iter3.hasNext()) {
                                MaapiSchemas.CSNode no = iter3.next();
                                System.out.print(no.getTag());
                                if (iter3.hasNext()) {
                                    System.out.print(",");
                                }
                            }
                            System.out.print(")");
                        }
                        if (iter2.hasNext()) {
                            System.out.print(",");
                        }
                    }
                    System.out.print("}");
                }
                if (iter.hasNext()) {
                    System.out.print(",");
                }
            }
            System.out.println("]");
        }

    }

    static  String nodeInfoAsString(MaapiSchemas.CSNode node) {
        String str ="NodeInfo";

        if (node != null){
            str += "["+node.getTag()+","+
                nodeTypeToString(node)+
                ","+
                MaapiSchemas.CSShallowType.toEnum(
                node.getNodeInfo().getShallowType()) +"]";
        } else {
            str += "[null]";
        }
        return str;
    }

    static  List<String> nodeTypeToString(MaapiSchemas.CSNode node){

        List<String> strInfo = new ArrayList<String>();
        if(node.isAction())
            strInfo.add("Action");
        if(node.isCase())
            strInfo.add("Case");
        if(node.isContainer())
            strInfo.add("Container");
        if(node.isEmptyLeaf())
            strInfo.add("EmptyLeaf");
        if(node.isLeaf())
            strInfo.add("Leaf");
        if(node.isLeafList())
            strInfo.add("LeafList");
        if(node.isList())
            strInfo.add("List");
        if(node.isActionParam())
            strInfo.add("ActionParam");
        if(node.isActionResult())
            strInfo.add("ActionResult");
        if(node.isWritable())
            strInfo.add("Writable");
        if(node.isNotif())
            strInfo.add("Notif");

        return strInfo;
    }
}
