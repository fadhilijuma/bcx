import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import util.PinVerify;

public class Mini extends AbstractVerticle {
    @Override
    public void start(Promise<Void> promise) {
        EventBus eventBus = this.vertx.eventBus();
        System.out.println("Mini started....");
        eventBus.<JsonObject>consumer("mini", message -> {
            JsonObject jsonObject = message.body();
            jsonObject.put("Topic", jsonObject.getString("IMEI").concat("/mini"));
            jsonObject.put("Key", MainLauncher.KEY);
            jsonObject.put("ProcessCode", "04");
            jsonObject.put("JDBC_URL", config().getString("JDBC_URL"));
            jsonObject.put("USER", config().getString("USER"));
            jsonObject.put("PASSWORD", config().getString("PASSWORD"));
            jsonObject.put("MAX_POOL_SIZE", 30);

            System.out.println("Mini request: " + jsonObject.encodePrettily());
            JsonObject json = PinVerify.Validate(jsonObject, MainLauncher.channelManager,vertx);
            if (json.getString("Status").equals("00")) {
                eventBus.publish("bal_mini", json);
            } else {
                eventBus.publish("mqtt", new JsonObject()
                        .put("Status", "01")
                        .put("ProcessCode", "04")
                        .put("Topic", jsonObject.getString("IMEI").concat("/mini"))
                        .put("Message", json.getString("Status")));
            }

        });
        promise.complete();
    }
}
