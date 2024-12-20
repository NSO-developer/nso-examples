package com.example.maapi;

import java.net.InetAddress;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

import java.util.EnumSet;

import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiInputStream;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.maapi.MaapiConfigFlag;

import com.tailf.conf.Conf;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import gnu.getopt.Getopt;

public class SaveConfig {

    private static Logger log =
        LogManager.getLogger(SaveConfig.class);

    private int th;


    public static void
        main( String[] arg )
    {
        Maapi maapi = null;
        FileWriter fileWriter = null;
        InputStream inputStream = null;
        int port = 4565;
        InetAddress addr = null;
        try {
            int opt = -1;
            EnumSet<MaapiConfigFlag> flags = null;
            String configPath = null;
            String file = null;

            Getopt getOpt = new Getopt("SaveConfig",arg, "c:f:p:o:x");
            while ( ( opt = getOpt.getopt()) != -1 ) {
                switch ( opt ) {
                case 'f':{
                    String format =  getOpt.getOptarg();
                    if (format.equalsIgnoreCase("XML")) {
                        flags = EnumSet.of(MaapiConfigFlag.XML_PRETTY);
                    } else if (format.equalsIgnoreCase("J")) {
                        flags = EnumSet
                            .of(MaapiConfigFlag.JUNIPER_CLI_FORMAT);
                    } else if (format.equalsIgnoreCase("IOS") ) {
                        flags = EnumSet
                            .of(MaapiConfigFlag.CISCO_IOS_FORMAT);
                    } else if (format.equalsIgnoreCase("XR") ) {
                        flags = EnumSet
                            .of(MaapiConfigFlag.CISCO_IOS_FORMAT);
                    }
                    break;
                }
                case 'x': {
                    flags.add(MaapiConfigFlag.XPATH);
                    break;
                }
                case 'p': {
                    configPath = getOpt.getOptarg();
                    break;
                }
                case 'o': {
                    file = getOpt.getOptarg();
                    break;
                }
                case 'c': {
                    String[] parts =  getOpt.getOptarg().split("/");

                    if ( parts.length > 1 ) {
                        byte[] b = new byte[4];
                        int i = 0;
                        for (String strByte :parts[0].split(".") )
                            b[i++] = Byte.parseByte( strByte );

                        addr = InetAddress.getByAddress(b);
                    }
                    if (parts.length == 2)
                        port = Integer.parseInt(parts[1]);

                    break;
                }
                default: {
                    System.err.printf("Unknow option %s", opt );
                    System.exit(1);
                }
                }
            }

            maapi = new Maapi(new Socket("localhost", 4565));

            maapi.startUserSession("admin", "maapi", new String[] {"admin"});

            int th = maapi.startTrans(Conf.DB_RUNNING,
                                      Conf.MODE_READ_WRITE);

            inputStream =
                maapi.saveConfig(th, flags, configPath );

            fileWriter = new FileWriter ( file );
            InputStreamReader is =
                new InputStreamReader ( inputStream ) ;

            char [] buf = new char [128];
            int bytesRead = -1;
            int byteswrote = 0;
            while ((bytesRead = is.read (buf, 0, buf.length)) != -1 ) {
                fileWriter.write(buf, 0, bytesRead );
                byteswrote += bytesRead;
            }

            log.info(" Wrote " + byteswrote + " bytes to file " + file);

        } catch( Exception e ) {
            log.error("",e);
            System.exit ( 1 );
        } finally {
            try {
                fileWriter.close();
                inputStream.close();
                maapi.getSocket().close();
            } catch ( Exception ee ) { log.error("",ee ); }
        }
    }
}
