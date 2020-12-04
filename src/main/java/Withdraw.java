import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import util.PinVerify;

public class Withdraw extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {
        EventBus eventBus = this.vertx.eventBus();
        System.out.println("Withdraw started....");
        eventBus.<JsonObject>consumer("withdraw", message -> {
            JsonObject jsonObject = message.body();
            jsonObject.put("Key", MainLauncher.KEY);
            jsonObject.put("Topic", jsonObject.getString("IMEI").concat("/withdraw"));
            System.out.println("PIN verification message: " + jsonObject.encodePrettily());
            JsonObject json = PinVerify.Validate(jsonObject, MainLauncher.channelManager,vertx);
            if (json.getString("Status").equals("00")) {
                eventBus.publish("withdraw_commission", json);
            } else {
                eventBus.publish("mqtt", new JsonObject()
                        .put("Status", "01")
                        .put("ProcessCode", "01")
                        .put("Topic", jsonObject.getString("IMEI").concat("/withdraw"))
                        .put("Message", json.getString("Status")));
            }

        });
        promise.complete();
    }
}
