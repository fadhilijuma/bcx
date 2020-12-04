import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import org.jpos.util.LogEvent;
import org.jpos.util.Logger;
import org.jpos.util.NameRegistrar;

import java.util.Date;
import java.util.Random;

public class SignOnEchoManager implements Runnable {
    public SignOnEchoManager() {
        try {
            MainLauncher.channelManager = NameRegistrar.get("manager");
        } catch (org.jpos.util.NameRegistrar.NotFoundException e) {
            LogEvent evt = MainLauncher.channelManager.getLog().createError();
            evt.addMessage(e);
            evt.addMessage(NameRegistrar.getInstance());
            Logger.log(evt);
        } catch (Throwable t) {
            MainLauncher.channelManager.getLog().error(t);
        }
    }

    public void run() {
        while (true) {
            try {
                if (!MainLauncher.ECHO) {
                    if (!MainLauncher.SIGNED_ON) {
                        sendSignOn();
                        continue;
                    }
                    sendECHO();
                    continue;
                }
                ISOUtil.sleep(120000L);
                sendECHO();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendSignOn() {
        try {
            Random rnd = new Random();
            ISOMsg signonmsg = new ISOMsg();
            signonmsg.setMTI("0800");
            signonmsg.set(7, ISODate.formatDate(new Date(), "MMddHHmmss"));
            signonmsg.set(11, (100000 + rnd.nextInt(900000)) + "");
            signonmsg.set(12, ISODate.formatDate(new Date(), "HHmmss"));
            signonmsg.set(13, ISODate.formatDate(new Date(), "MMdd"));
            signonmsg.set(70, "001");
            ISOMsg response = MainLauncher.channelManager.sendMsg(signonmsg);
            if (response != null) {
                if (response.getString("39").equalsIgnoreCase("00")) {
                    MainLauncher.SIGNED_ON = true;
                    System.out.println("Sign ON: " + MainLauncher.SIGNED_ON);
                }
            } else {
                MainLauncher.SIGNED_ON = false;
            }
            System.out.println("Sign ON: " + MainLauncher.SIGNED_ON + " ECHO: " + MainLauncher.ECHO);
        } catch (ISOException e1) {
            MainLauncher.channelManager.getLog().error("ISOException :" + e1.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            MainLauncher.channelManager.getLog().error("Exception :" + e.getMessage());
        }
    }

    private void sendECHO() {
        try {
            Random rnd = new Random();
            ISOMsg signonmsg = new ISOMsg("0800");
            signonmsg.set(7, ISODate.formatDate(new Date(), "MMddHHmmss"));
            signonmsg.set(11, (100000 + rnd.nextInt(900000)) + "");
            signonmsg.set(12, ISODate.formatDate(new Date(), "HHmmss"));
            signonmsg.set(13, ISODate.formatDate(new Date(), "MMdd"));
            signonmsg.set(70, "301");
            ISOMsg response = MainLauncher.channelManager.sendMsg(signonmsg);
            if (response != null) {
                if (response.getString("39").equalsIgnoreCase("00")) {
                    MainLauncher.ECHO = true;
                    System.out.println("ECHO: " + MainLauncher.ECHO);
                } else {
                    MainLauncher.ECHO = false;
                    MainLauncher.SIGNED_ON = false;
                }
            } else {
                MainLauncher.ECHO = false;
                MainLauncher.SIGNED_ON = false;
            }
            System.out.println("Sign ON: " + MainLauncher.SIGNED_ON + " ECHO: " + MainLauncher.ECHO);
        } catch (ISOException e1) {
            MainLauncher.channelManager.getLog().error("ISOException :" + e1.getMessage());
        } catch (Exception e) {
            MainLauncher.channelManager.getLog().error("Exception :" + e.getMessage());
        }
    }
}
