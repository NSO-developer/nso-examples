package com.example.xmlrpcdevice.controller;

import java.util.List;
import java.util.ArrayList;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileInputStream;

import java.net.InetAddress;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import com.example.xmlrpcdevice.model.*;


public class DeviceModelController{

    private static Logger LOGGER =
        LogManager.getLogger(DeviceModelController.class);

    private static DeviceModelController controller
        = new DeviceModelController();

    public static DeviceModelController getInstance(){
        return controller;
    }

    private String fileName;
    private boolean connected = false;
    private DeviceModel model;
    private String currev = null;


    private String transID(){
        return currev;
    }


    public void reload(int initversion){
        try{
            loadFromStartup();
        }catch(Exception e){
            LOGGER.warn("Could not found serialized model");
            LOGGER.warn("Reseting to initial state");

            try{
                init(initversion);
                writeToFile(fileName + ".ser",model);
                loadFromStartup();
            }catch(ClassNotFoundException ce){
                LOGGER.error("ERROR:", ce);
                System.exit(1);
            }catch(IOException io){
                LOGGER.error("ERROR:", io);
                System.exit(1);

            }
        }
    }

    /**
     *  Set the name of the file to use
     *  when storing to disk. The file will be named
     *  as fileName.ser. The file will be simulated as
     *  the startup database.
     *
     *  @param fileName The name of the file to write to
     **/
    public void setFileName(String fileName){
        this.fileName = fileName;
    }

    /**
     *  Load the device model from file. Simulate
     *  load the startup to running.
     *
     *  @param fileName The name of the file to write to
     **/
    public void loadFromStartup() throws ClassNotFoundException,
                                         IOException {
        LOGGER.warn("DEVICE Controller loadFromStartup -->");
        DeviceModel startupmodel = (DeviceModel)load(fileName);

        this.model = startupmodel;
        LOGGER.warn("DEVICE Controller loadFromStartup -->" + model);
    }
    /**
     *  Retrieve to model from Running.
     *
     *  @return DeviceModel from Running.
     **/
    public DeviceModel getModel() {
        return model;
    }

    private void newTrans(){
        restore();
        long dd = System.currentTimeMillis();
        this.currev = "r" + dd;

    }

    public boolean connect(){
        this.connected = true;
        return true;
    }

    public boolean close(){
        this.connected = false;
        return true;
    }

    /**
     *  Start a transaction.
     *
     *  @return A unique transaction id.
     **/

    public String startTrans(){
        LOGGER.info("DEVICE Controler startTrans -->");
        newTrans();
        LOGGER.info("DEVICE Controler startTrans transID:" + transID());
        return transID();
    }

    /**
     *  Revert the current transaction before it has been commited
     *
     **/
    public void revert(){
        this.currev = null;
    }

    /**
     *  Abort the current trasaction before it has been persisted.
     *
     **/
    public void abort(){
        restore();
    }


    /**
     * Commit the current transaction
     * Save the Running to a temporary file.
     *
     * @param rev - Transaction id
     **/

    public boolean commit(String rev) throws IOException{
        LOGGER.info("DEVICE Controller commit rev " + rev + " -->");
        if(rev.equals(transID())){
            boolean status = save(model);
            LOGGER.info("DEVICE Controller commit rev " + rev + " -->"
                        + status);
            return status;
        }else{
            LOGGER.info("DEVICE Controller commit rev " + rev + " -->"
                        + false);

            return false;
        }
    }


    /**
     * Simulate copy running to startup
     * Load tempory file that was saved in commit() and
     * save it to Startup.
     **/
    public  boolean persist(String revision){
        LOGGER.info("DEVICE Controller persist (" + revision +")-->");
        boolean ret = false;

        if(revision == null)
            return ret;
        //DeviceModel state  = load(revision);

        try{
            writeToStartup(revision);
            deleteFile(revision);
            this.currev = null;
            ret = true;
        }catch(ClassNotFoundException e){
            LOGGER.error("WriteToStartup() Failure",e);
        }catch(IOException e){
            LOGGER.error("WriteToStartup() Failure",e);
        }
        LOGGER.info("DEVICE Controller persist (" + revision +")--> " + ret);
        return ret;
    }





    private boolean deleteFile(String rev){
        File file = new File(rev + ".ser");
        return file.delete();
    }

    /**
     * Save the model to file.
     *
     * @return  -  Unique string that represent a changeset which could be
     *  used when loading a revision from file load(String revision).
     *
     **/
    private boolean save(DeviceModel model){
        //long dd = System.currentTimeMillis();
        LOGGER.info("Controller saving model " + currev);
        boolean status = false;
        try{
            writeToFile(currev + ".ser",model);
            status = true;
        }catch(IOException e){
            LOGGER.error("Could not save the model",e);
        }
        return status;
    }


    private void writeToFile(String fileName,DeviceModel model)
        throws IOException{
        LOGGER.info("Controller writing file " + fileName);
        FileOutputStream fos = new FileOutputStream(fileName);
        ObjectOutputStream oos = new ObjectOutputStream(fos);

        oos.writeObject(model);
        oos.close();
    }

    //Write permenently to fileName.ser
    //and reload the memory version
    private void writeToStartup(String revision)
        throws IOException,ClassNotFoundException{
        LOGGER.info("Controller write to startup revision " + revision);
        DeviceModel temp = load(revision);
        writeToFile(fileName + ".ser",temp);
        loadFromStartup();
        LOGGER.info("Controller write to startup revision " +
                    revision  + " ok");
    }



    /**
     *  Load a DeviceModel from revision.
     *  @parm revision string without file name extension.
     **/
    private DeviceModel load(String revision) throws ClassNotFoundException,
                                                    IOException{
        FileInputStream fis =
            new FileInputStream(revision + ".ser");

        ObjectInputStream ois = new ObjectInputStream(fis);
        DeviceModel tmp = (DeviceModel)ois.readObject();
        ois.close();


        return tmp;

    }

    private void restore(){
        if(transID() != null){
            boolean deleted = deleteFile(currev);

            if(!deleted)
                LOGGER.warn("Could not delete " + currev + ".ser");

            try{
                loadFromStartup();
            }catch(Exception e){
                LOGGER.info("Could Not load from startup");
            }
        }
    }



    private void init(int initversion){
        try{
            model = new DeviceModel();

            List<DeviceInterfaceAlias> aliases =
                new ArrayList<DeviceInterfaceAlias>();

            DeviceInterfaceAlias alias1 =
                new DeviceInterfaceAlias(0,
                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)192,(byte)168,(byte)1,
                                                  (byte)(129 + initversion)}),

                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)255,(byte)255,(byte)255,
                                                  (byte)(0)}));

            DeviceInterfaceAlias alias2 =
                new  DeviceInterfaceAlias(1,
                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)192,(byte)168,(byte)1,
                                                  (byte)(130 + initversion)}),
                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)255,(byte)255,(byte)255,
                                                  (byte)0}));

            aliases.add(alias1);
            aliases.add(alias2);

            String deviceMacAddress1 = "";
            switch(initversion) {
            case 1:
                deviceMacAddress1 = "84:2b:2b:9e:af:0a";
                break;
            case 2:
                deviceMacAddress1 = "00:1b:63:ff:fe:7a";
                break;
            case 3:
                deviceMacAddress1 = "00:1b:63:c7:2b:42";
                break;
            }


            DeviceInterface eth0 =
                new DeviceInterface("eth0",
                                    new DeviceMacAddress(deviceMacAddress1),
                                    InetAddress.getByAddress(new byte[]{
                                            (byte)192,(byte)168,(byte)1,
                                            (byte)(128 + initversion)}),

                                    InetAddress.getByAddress(new byte[]{
                                            (byte)255,(byte)255,(byte)255,
                                            (byte)0}),

                                    DeviceInterface.UP,
                                    1500,
                                    aliases,
                                    100,1000);

            model.addInterface(eth0);



            List<DeviceInterfaceAlias> aliases2 =
                new ArrayList<DeviceInterfaceAlias>();

            DeviceInterfaceAlias alias3 =
                new  DeviceInterfaceAlias(0,
                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)192,(byte)168,(byte)1,
                                                  (byte)(129 + initversion)}),

                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)255,(byte)255,(byte)255,
                                                  (byte)0}));

            DeviceInterfaceAlias alias4 =
                new  DeviceInterfaceAlias(1,
                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)192,(byte)168,(byte)1,
                                                  (byte)(130 + initversion)}),
                                          InetAddress.getByAddress(new byte[]{
                                                  (byte)255,(byte)255,(byte)255,
                                                  (byte)0}));

            aliases2.add(alias3);
            aliases2.add(alias4);


            String deviceMacAddress2 = "";
            switch(initversion) {
            case 1:
                deviceMacAddress2 = "84:3b:2b:7e:ab:0a";
                break;
            case 2:
                deviceMacAddress2 = "00:1b:63:fe:fa:7b:1c:ce";
                break;
            case 3:
                deviceMacAddress2 = "11:1c:63:c7:2b:22";
                break;
            }

            DeviceInterface lo =
                new DeviceInterface("lo",
                                    new DeviceMacAddress(deviceMacAddress2),
                                    InetAddress.getByAddress(new byte[]{
                                            (byte)127,(byte)0,(byte)0,
                                            (byte)(1+initversion)}),

                                    InetAddress.getByAddress(new byte[]{
                                            (byte)255,(byte)0,(byte)0,
                                            (byte)0 }),

                                    DeviceInterface.UP, 16436,aliases2,
                                    100,
                                    1000);


        }catch(Exception e){
            LOGGER.error("Error:" , e);
        }
    }

}
