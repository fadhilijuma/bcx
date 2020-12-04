import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.*;

import java.time.Instant;

public class Deposit extends AbstractVerticle {

    @Override
    public void start() {
        System.out.println("Deposit started....");
        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("deposit", message -> {
            JsonObject json = message.body();
            json.put("Topic", json.getString("IMEI").concat("/deposit"));
            System.out.printf("deposit request  |  %s\n", json.encodePrettily());
            // Pool options
            MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                    .setPort(3306)
                    .setHost(config().getString("AGENT_DB_HOST"))
                    .setDatabase("Uchumi")
                    .setUser("root");
            PoolOptions poolOptions = new PoolOptions()
                    .setMaxSize(5);
// Create the client pool
            MySQLPool pool = MySQLPool.pool(vertx, connectOptions, poolOptions);
// Get a connection from the pool
            pool.getConnection(ar1 -> {

                if (ar1.succeeded()) {

                    System.out.println("Connected");

                    // Obtain our connection
                    SqlConnection conn = ar1.result();
//SELECT Commission FROM tb_DepositCommission where ? BETWEEN FromAmount AND ToAmount
                    // All operations execute on the same connection
                    conn
                            .preparedQuery("SELECT Commission FROM tb_DepositCommission where ? BETWEEN FromAmount AND ToAmount")
                            .execute(Tuple.of(json.getString("Amount")), ar -> {
                                if (ar.succeeded()) {
                                    RowSet<Row> rows = ar.result();
                                    if (rows.size() == 0) {
                                        System.out.printf("Commission not set for the amount %s |\n", json.getString("Amount"));
                                        eventBus.publish("mqtt", new JsonObject()
                                                .put("status", "01")
                                                .put("Topic", json.getString("IMEI").concat("/deposit"))
                                                .put("ProcessCode", "02")
                                                .put("Message", "Weka kiasi cha Tshs. 5,000 na zaidi."));
                                    } else {
                                        System.out.println("Got " + rows.size() + " rows ");
                                        for (Row row : rows) {
                                            System.out.printf("Amount: %s | Commission: %d\n", json.getString("Amount"), row.getInteger(0));
                                            String stamp = Long.toString(Instant.now().getEpochSecond());
                                            json.put("ChargeAmount", 0);
                                            json.put("ProcessCode", "02");
                                            json.put("Topic", json.getString("IMEI").concat("/deposit"));
                                            json.put("AgentCommission", row.getInteger(0));
                                            json.put("UcbCommission", 0);
                                            json.put("Stan", json.getString("AccountNumber").concat(stamp.substring(stamp.length() - 4)));

                                            eventBus.publish("withdraw_deposit_exec", json);
                                        }
                                    }

                                } else {
                                    eventBus.publish("mqtt", new JsonObject()
                                            .put("status", "01")
                                            .put("ProcessCode", "02")
                                            .put("Topic", json.getString("IMEI").concat("/deposit"))
                                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                                    System.out.println("Connection failure: " + ar.cause().getMessage());
                                }
                            });

                } else {
                    eventBus.publish("mqtt", new JsonObject()
                            .put("status", "01")
                            .put("Topic", json.getString("IMEI").concat("/deposit"))
                            .put("ProcessCode", "02")
                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                    System.out.println("Could not connect: " + ar1.cause().getMessage());
                }
            });
        });

    }

}
