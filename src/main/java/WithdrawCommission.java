import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.*;

import java.time.Instant;

public class WithdrawCommission extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("withdraw_commission", message -> {
            JsonObject json = message.body();
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
                            .preparedQuery("SELECT Charge,AgentCommission,UcbCommission FROM tb_WithdrawSlab where ? BETWEEN FromAmount AND ToAmount")
                            .execute(Tuple.of(json.getString("Amount")), ar -> {
                                if (ar.succeeded()) {
                                    RowSet<Row> rows = ar.result();
                                    if (rows.size() == 0) {
                                        System.out.printf("Commission not set for the amount %s |\n", json.getString("Amount"));
                                        eventBus.publish("mqtt", new JsonObject()
                                                .put("status", "01")
                                                .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                                    } else {
                                        System.out.println("Got " + rows.size() + " rows ");
                                        for (Row row : rows) {
                                            System.out.printf("Amount: %s | Commission: %d\n", json.getString("Amount"), row.getInteger(0));
                                            String stamp = Long.toString(Instant.now().getEpochSecond());
                                            json.put("ChargeAmount", row.getValue("Charge"));
                                            json.put("ProcessCode", "01");
                                            json.put("AgentCommission", row.getValue("AgentCommission"));
                                            json.put("UcbCommission", row.getValue("UcbCommission"));
                                            json.put("Stan", json.getString("AccountNumber").concat(stamp.substring(stamp.length() - 4)));
                                            eventBus.publish("withdraw_deposit_exec", json);
                                        }
                                    }

                                } else {
                                    eventBus.publish("mqtt", new JsonObject()
                                            .put("status", "01")
                                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                                    System.out.println("Failure: " + ar.cause().getMessage());
                                }
                            });

                } else {
                    eventBus.publish("mqtt", new JsonObject()
                            .put("status", "01")
                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                    System.out.println("Could not connect: " + ar1.cause().getMessage());
                }
            });
        });

    }
}
