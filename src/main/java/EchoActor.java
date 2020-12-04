import akka.actor.AbstractActor;
import akka.actor.Props;
import channel.ChannelManager;
import org.jpos.iso.ISODate;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.packager.ISO93APackager;

import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

public class EchoActor extends AbstractActor {
    private final ChannelManager channelManager;
    static ISO93APackager packager;
    public static Props props(ChannelManager channelManager) {
        return Props.create(EchoActor.class, channelManager);
    }

    public EchoActor(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    public Receive createReceive() {
        return this.receiveBuilder().matchEquals("Tick", (m) -> CompletableFuture.supplyAsync(this::sendECHO).thenApply(this::ProcessEcho)).matchEquals("Tock", (m) -> CompletableFuture.supplyAsync(this::sendSignOn).thenApply(this::ProcessSignOn)).matchEquals("Tack", (m) -> CompletableFuture.supplyAsync(this::sendKeyExchange).thenApply(this::ProcessKeyExchange)).match(Throwable.class, (t) -> {
            System.out.printf("Error at EchoActor [ %s ]\n",t.getMessage());
        }).build();
    }

    private ISOMsg sendSignOn() {
        ISOMsg response = null;

        try {
            SecureRandom rnd = new SecureRandom();
            ISOMsg signonmsg = new ISOMsg();
            signonmsg.setMTI("0800");
            signonmsg.set(7, ISODate.formatDate(new Date(), "MMddHHmmss"));
            signonmsg.set(11, 100000 + rnd.nextInt(900000) + "");
            signonmsg.set(12, ISODate.formatDate(new Date(), "HHmmss"));
            signonmsg.set(13, ISODate.formatDate(new Date(), "MMdd"));
            signonmsg.set(70, "001");
            response = this.channelManager.sendMsg(signonmsg);
        } catch (ISOException var4) {
            this.channelManager.getLog().error("ISOException :" + var4.getMessage());
        } catch (Exception var5) {
            this.channelManager.getLog().error("Exception :" + var5.getMessage());
        }

        return response;
    }

    private ISOMsg sendECHO() {
        ISOMsg response = null;

        try {
            SecureRandom rnd = new SecureRandom();
            ISOMsg signonmsg = new ISOMsg("0800");
            signonmsg.set(7, ISODate.formatDate(new Date(), "MMddHHmmss"));
            signonmsg.set(11, 100000 + rnd.nextInt(900000) + "");
            signonmsg.set(12, ISODate.formatDate(new Date(), "HHmmss"));
            signonmsg.set(13, ISODate.formatDate(new Date(), "MMdd"));
            signonmsg.set(70, "301");
            response = this.channelManager.sendMsg(signonmsg);
        } catch (ISOException var4) {
            this.channelManager.getLog().error("ISOException :" + var4.getMessage());
        } catch (Exception var5) {
            this.channelManager.getLog().error("Exception :" + var5.getMessage());
        }

        return response;
    }

    private ISOMsg sendKeyExchange() {
        ISOMsg response = null;

        try {
            SecureRandom rnd = new SecureRandom();
            ISOMsg signonmsg = new ISOMsg("0800");
            signonmsg.set(7, ISODate.formatDate(new Date(), "MMddHHmmss"));
            signonmsg.set(11, 100000 + rnd.nextInt(900000) + "");
            signonmsg.set(12, ISODate.formatDate(new Date(), "HHmmss"));
            signonmsg.set(13, ISODate.formatDate(new Date(), "MMdd"));
            signonmsg.set(70, "101");
            response = this.channelManager.sendMsg(signonmsg);
            this.logISOMsg(response);
        } catch (ISOException var4) {
            this.channelManager.getLog().error("ISOException :" + var4.getMessage());
        } catch (Exception var5) {
            this.channelManager.getLog().error("Exception :" + var5.getMessage());
        }

        return response;
    }

    private String ProcessEcho(ISOMsg response) {
        if (response != null) {
            if (response.getString("39").equals("00")) {
                MainLauncher.ECHO = true;
                System.out.println("ECHO: true");
            } else {
                MainLauncher.ECHO = false;
                MainLauncher.SIGNED_ON = false;
            }
        } else {
            MainLauncher.ECHO = false;
            MainLauncher.SIGNED_ON = false;
        }

        System.out.println("Sign ON: " + MainLauncher.SIGNED_ON + " ECHO: " + MainLauncher.ECHO);
        return "1";
    }

    private String ProcessSignOn(ISOMsg response) {
        if (response != null) {
            if (response.getString("39").equals("00")) {
                MainLauncher.SIGNED_ON = true;
                System.out.println("Sign ON: true");
            }
        } else {
            MainLauncher.SIGNED_ON = false;
        }

        System.out.println("Sign ON: " + MainLauncher.SIGNED_ON + " ECHO: " + MainLauncher.ECHO);
        return "1";
    }

    private String ProcessKeyExchange(ISOMsg response) {
        if (response != null) {
            if (response.getString("39").equalsIgnoreCase("00")) {
                String PWK = response.getString("125");
                System.out.printf(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>PWK from BCX : [ %s ]\n",PWK);
                MainLauncher.KEY=PWK;
            } else {
                System.out.printf(">>>>>>>>>>>>>>>>>>>>>>>>KEYEXCHANGE ERROR: [  %s ]\n",response.toString());
            }
        } else {
            System.out.println("NULL RESPONSE FROM BCX");
        }

        return "1";
    }

    private void logISOMsg(ISOMsg msg) {
        packager = new ISO93APackager();
        System.out.println("----ISO MESSAGE-----");
        try {
            System.out.printf("  MTI : %s\n",msg.getMTI());

            for(int i = 1; i <= msg.getMaxField(); ++i) {
                if (msg.hasField(i)) {
                    System.out.printf("Field-%s (%s): %s\n",i, packager.getFieldPackager(i).getDescription(), msg.getString(i));
                }
            }
        } catch (ISOException isoException) {
            System.out.printf("Error while logging ISOMsg [ %s ]\n",isoException.getMessage());
        } finally {
            System.out.println("--------------------");
        }

    }
}
