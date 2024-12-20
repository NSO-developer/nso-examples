package com.example.qinqalarm;

import java.util.Arrays;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfTag;
import com.tailf.maapi.Maapi;


public class ExtConfPath extends ConfPath {

  private static Logger LOGGER  = LogManager.getLogger(ExtConfPath.class);

    public ExtConfPath(Maapi maapi, int th, String path) throws ConfException
  {
      super(maapi, th, path);
  }
    public ExtConfPath(String path) throws ConfException
  {
      super(path);
  }
  public ExtConfPath(ConfObject [] kp){
    super(kp);
  }

  //Go up one level in the Path
  public ExtConfPath parent()
    throws ConfException {
    ConfObject[] kp = this.getKP();

    int i = 1;
    //Remove the first Tag. Make sure it's a Tag not only a Key
    for (ConfObject o : kp){
      if(o instanceof ConfTag){
        break;
      }
      i++;
    }

    if(kp.length - i <= 0) {
      throw new ConfException("Trying to shorten path results in 0 length");
    }

    ConfObject [] newKp = Arrays.copyOfRange(kp, i, kp.length);

    return new ExtConfPath(newKp);
  }

  //Returns the name of the top-level tag (not key)
  public String topTag() throws ConfException
  {
    ConfObject[] kp = this.getKP();
    for (int i =0; i<kp.length; i++) {
      if(kp[i] instanceof ConfTag){
        return ((ConfTag)kp[i]).getTag();
      }
    }
    throw new ConfException("KeyPath contains no tags: " + this.toString());
  }

}
