package channel;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.MUX;
import org.jpos.q2.QBeanSupport;
import org.jpos.util.NameRegistrar;

public class ChannelManager extends QBeanSupport {
    private long MAX_TIME_OUT;
    private MUX mux;

    public ChannelManager() {
    }

    protected void initService() {
        this.log.info("initializing ChannelManager Service");

        try {
            this.mux = (MUX)NameRegistrar.get("mux." + this.cfg.get("mux"));
            this.MAX_TIME_OUT = this.cfg.getLong("timeout");
            NameRegistrar.register("manager", this);
        } catch (NameRegistrar.NotFoundException var2) {
            this.log.error("Error in initializing service :" + var2.getMessage());
        }

    }

    public ISOMsg sendMsg(ISOMsg m) throws Exception {
        return this.sendMsg(m, this.mux, this.MAX_TIME_OUT);
    }

    private ISOMsg sendMsg(ISOMsg msg, MUX mux, long time) throws Exception {
        if (mux != null) {
            long start = System.currentTimeMillis();
            ISOMsg respMsg = mux.request(msg, time);
            long duration = System.currentTimeMillis() - start;
            this.log.info("Response time (ms):" + duration);
            return respMsg;
        } else {
            return null;
        }
    }
}


