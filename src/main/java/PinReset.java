import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import util.PinVerify;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PinReset extends AbstractVerticle {

    @Override
    public void start() {
        System.out.println("Pin Reset Started...");
        WorkerExecutor executor = vertx.createSharedWorkerExecutor("pin-worker-pool");
        WebClient webClient = WebClient.create(vertx);
        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("pinReset", message -> {
            System.out.printf("PinReset message received: | %s\n", message.body());
            JsonObject jsonObject = message.body();
            jsonObject.put("Topic", jsonObject.getString("ShortCode").concat("/pin_reset_response"));
            executor.executeBlocking((future) -> {
                JsonObject customer = PinVerify.getCustomerDetails(jsonObject.getString("AccountNumber"), config().getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL"));
                if (customer.getString("Status").equals("00")) {
                    String builder = jsonObject.getString("pan")
                            .concat("|")
                            .concat(jsonObject.getString("AccountNumber"))
                            .concat("|")
                            .concat("10")
                            .concat("|")
                            .concat(customer.getString("ClientID"))
                            .concat("|")
                            .concat(customer.getString("Name"))
                            .concat("|").concat("1");
                    System.out.printf("Proceeding to post to BCX..: | %s\n", builder);
                    executeWebClient(builder, jsonObject, eventBus, webClient);

                } else {
                    eventBus.publish("mqtt", new JsonObject()
                            .put("Status", "01")
                            .put("Message","Invalid Account Details")
                            .put("Topic", jsonObject.getString("Topic")));
                }
            }, (res) -> System.out.printf("Final Result | %s\n", res.result()));
        });
    }
    private void executeWebClient(String builder, JsonObject jsonObject, EventBus eventBus, WebClient webClient) {
        LocalDateTime localDateTime = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("MMddHHmmss");
        webClient.post(8784, "172.25.27.80", "/UmojaMobileTransactions/services/UmojaMobileService/instantCardIssuing/")
                .as(BodyCodec.jsonObject())
                .sendJsonObject(new JsonObject()
                        .put("MTI", "0600")
                        .put("f2", jsonObject.getString("pan"))
                        .put("f3", "910000")
                        .put("f7", localDateTime.format(format))
                        .put("f11", "102926")
                        .put("f37", "000407393861")
                        .put("f41", "UCB00101")
                        .put("f42", "UCB000000000001")
                        .put("f49", "834")
                        .put("f62", jsonObject.getString("phoneNumber"))
                        .put("f60", builder)
                        .put("f102", jsonObject.getString("AccountNumber")), ar -> {
                    if (ar.succeeded()) {
                        System.out.println("Connection to BCX Successful....");
                        HttpResponse<JsonObject> bufferHttpResponse = ar.result();
                        JsonObject body = bufferHttpResponse.body();
                        System.out.printf("Response from BCX | %s\n", body.encodePrettily());
                        if (body.getString("f39").equals("00")) {

                            eventBus.publish("mqtt", new JsonObject()
                                    .put("status", "00")
                                    .put("Topic", jsonObject.getString("Topic"))
                                    .put("Message", "Successful PIN Reset."));
                        } else {
                            eventBus.publish("mqtt", new JsonObject()
                                    .put("Status", "01")
                                    .put("Message", "Failed to to Reset PIN.")
                                    .put("Topic", jsonObject.getString("Topic")));
                        }
                    } else {
                        System.out.printf("Connection to BCX Failed....| %s", ar.cause());

                        eventBus.publish("mqtt", new JsonObject()
                                .put("Status", "01")
                                .put("Message", "Failed to to Reset PIN.")
                                .put("Topic", jsonObject.getString("Topic")));
                    }

                });
    }
}
