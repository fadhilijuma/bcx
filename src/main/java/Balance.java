import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import util.PinVerify;

import java.time.Instant;

public class Balance extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {
        EventBus eventBus = this.vertx.eventBus();
        System.out.println("Balance started....");
        eventBus.<JsonObject>consumer("balance", message -> {
            JsonObject jsonObject = message.body();
            jsonObject.put("Topic", jsonObject.getString("IMEI").concat("/balance"));
            jsonObject.put("Key", MainLauncher.KEY);
            jsonObject.put("ProcessCode", "03");
            jsonObject.put("JDBC_URL", config().getString("JDBC_URL"));
            jsonObject.put("USER", config().getString("USER"));
            jsonObject.put("PASSWORD", config().getString("PASSWORD"));
            jsonObject.put("MAX_POOL_SIZE", 30);
            System.out.println("balance request: " + jsonObject.encodePrettily());
            JsonObject json = PinVerify.Validate(jsonObject, MainLauncher.channelManager,vertx);

            System.out.println("===========================================");
            System.out.printf("===========Response from Pin Verify || %s",json.encodePrettily());
            if (json.getString("Status").equals("00")) {
                String stamp=Long.toString(Instant.now().getEpochSecond());

                json.put("ChargeAmount",config().getString("BAL_CHARGE"));
                json.put("AgentCom",config().getString("COMMISSION_TO_AGENT_ON_BALANCE"));
                json.put("UcbCom",config().getString("COMMISSION_TO_UCB_ON_BALANCE"));
                json.put("Stan",json.getString("AccountNumber").concat(stamp.substring(stamp.length()-4)));

                eventBus.publish("bal_mini", json);

            } else {
                eventBus.publish("mqtt", new JsonObject()
                        .put("Status", "01")
                        .put("ProcessCode", "03")
                        .put("Topic", jsonObject.getString("IMEI").concat("/balance"))
                        .put("Message", json.getString("Status")));
            }

        });
        promise.complete();
    }
}
