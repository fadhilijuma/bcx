package channel;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISORequestListener;
import org.jpos.iso.ISOSource;

import java.util.HashMap;
import java.util.Map;

public class ServerRequestListener implements ISORequestListener {
    public boolean process(ISOSource isoSrc, ISOMsg isoMsg) {
        System.out.printf("serverrequest>>>>> [ %s ]\n",isoMsg.toString());
        ISOMsg reply = (ISOMsg)isoMsg.clone();
        try {
            String requestMTI = isoMsg.getMTI();
            System.out.printf("incoming>>>>> [ %s ]\n",requestMTI);
            System.out.printf("serverresponse>>>>> [ %s ]\n", (Object) isoMsg.pack());
            switch (requestMTI) {
                case "0800":
                    reply.setResponseMTI();
                    reply.set(39, "000");
                    System.out.printf("outgoing>>>>> [ %s ]\n", (Object) reply.pack());
                    isoSrc.send(reply);
                    return false;
                case "0810":
                    if (isoMsg.getString("70").equalsIgnoreCase("001")) {
                        System.out.println("SIGNED_ON = true");
                    } else if (isoMsg.getString("70").equalsIgnoreCase("301")) {
                        System.out.println("ECHO = true");
                    }
                    return false;
            }
            Map<String, String> response = new HashMap<>();
            for (int i = 1; i <= isoMsg.getMaxField(); i++) {
                if (isoMsg.hasField(i))
                    response.put(String.valueOf(i), isoMsg.getString(i));
            }
        } catch (ISOException|java.io.IOException e) {
            System.out.printf("Error at channel.ServerRequestListener: [ %s ]\n",e.getMessage());
        }
        return false;
    }
}
