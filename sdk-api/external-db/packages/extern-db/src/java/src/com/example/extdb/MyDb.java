package com.example.extdb;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import com.tailf.dp.*;

public class MyDb {

    private static int nextKey = 0;

    /**
     * This is our simple database.
     * Methods for accessing the database are
     * synchronized to allow access from several threads.
     */
    private static ArrayList<Object> runningDb;
    private static boolean isLocked= false;


    /**
     * Locks the database
     */
    public synchronized static void lock() {
        isLocked = true;
    }


    /**
     * Unlocks the database
     */
    public synchronized static void unlock() {
        isLocked =false;
    }

    /**
     * Find work item in database and return it.
     */
    public synchronized static Item findItem(int key) {
        for (int i=0; i<runningDb.size(); i++) {
            Item x = (Item) runningDb.get(i);
            if (x.key == key) return x;
        }
        return null;
    }

    public static int getKey() {
        MyDb.nextKey++;
        return MyDb.nextKey;
    }

    /**
     * Gives iterator for our item table
     */
    public synchronized static Iterator<Object> iterator() {
        return runningDb.iterator();
    }


    /**
     * Remove Item from database
     */
    public synchronized static void removeItem(int k) {
        for (int i=0; i<runningDb.size(); i++) {
            Item x = (Item) runningDb.get(i);
            if (x.key == k) {
                runningDb.remove(i);
                return;
            }
        }
    }


    /**
     * Create new item in database
     */
    public synchronized static void newItem(Item s) {
        runningDb.add(s);
    }


    /**
     * number of items
     */
    public synchronized static int numItems() {
        return runningDb.size();
    }



    /**
     *
     */
    public synchronized static Item getItem(int key) {
        return (Item) runningDb.get(key);
    }


    /**
     * Restores DB from FILE
     */
    public synchronized static boolean restore(String file) {
        try {
            String filePath = new String( file );
            FileInputStream fis = new FileInputStream( filePath );
            ObjectInputStream inStream = new ObjectInputStream( fis );
            ArrayList<Object> tmp;
            tmp = ( ArrayList<Object> ) inStream.readObject();
            MyDb.nextKey = (Integer) inStream.readObject();
            inStream.close();
            runningDb = tmp;
            return true;
        }
        catch (Exception e) {
        }
        return false;
    }


    /**
     * Saves DB to FILE
     */
    public synchronized static void save(String filename) throws Exception {
        try {
            FileOutputStream fos = new FileOutputStream( filename );
            ObjectOutputStream outStream = new ObjectOutputStream( fos );

            //  Save each object.
            outStream.writeObject( runningDb );
            outStream.writeObject( MyDb.nextKey);
            outStream.flush();
            outStream.close();
        }
        catch (Exception e) {
            throw new DpCallbackException("Failed to save file "+e);
        }
    }


    /**
     * Deletes the DB file
     */
    public synchronized static void unlink(String filename) {
        File f = new File(filename);
        f.delete();
    }


    /**
     * Renames a DB file
     */
    public synchronized static void rename(String oldname, String newname)
        throws DpCallbackException {
        File file = new File(oldname);
        File file2 = new File(newname);
        boolean success = file.renameTo(file2);
        if (!success) {
            throw new DpCallbackException("failed to rename file");
        }
    }

    /**
     * init DB
     */
    public static void initDb()  {
        try {
            if ( restore("running.DB"))
                return;
            runningDb = new ArrayList<Object>();
            runningDb.add( new Item(MyDb.getKey(), "Finish the docs",
                                    "klacke", ""));
            runningDb.add( new Item(MyDb.getKey(), "Check ticket 22",
                                    "Steffe", ""));
            runningDb.add( new Item(MyDb.getKey(), "Work harder",
                                    "Guran",""));
            save("running.DB");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

}
