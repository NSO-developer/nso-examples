package com.example.basicfirewall;

import com.tailf.conf.ConfException;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;

public class NavuUtil {

    public static void checkLeafExistance(NavuLeaf leaf, String errorMessage)
        throws ConfException
    {
        if ( ! leaf.exists() ) {
            throw new ConfException(errorMessage);
        }
    }

    public static NavuContainer getInstanceIfExists(NavuList myList, String key,
                                          String errorMessage)
        throws ConfException
    {
        try {
            if (!myList.containsNode(key) ) {
                throw new ConfException(errorMessage);
            }
        }
        catch (Exception e) {
            throw new ConfException("Invalid list: "+ errorMessage);
        }
        return myList.elem(key);
    }
}