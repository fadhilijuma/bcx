import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Transaction;
import io.vertx.sqlclient.Tuple;

public class ComAndTransactionRecords extends AbstractVerticle {
    @Override
    public void start() {
        System.out.println("ComAndTransactionRecords started...");

        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("updateCommission", req -> {
            JsonObject json = req.body();
            System.out.printf("commission update request  |  %s\n", json.encodePrettily());
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

            pool.begin(res -> {
                if (res.succeeded()) {
                    // Get the transaction
                    Transaction tx = res.result();
                    // Various statements
                    tx.preparedQuery("INSERT INTO tb_CommissionDisbursement(ShortCode, AccountNumber,Amount,ProcessCode,Stan) VALUES(?,?,?,?,?)")
                            .execute(Tuple.of(json.getString("ShortCode"), json.getString("AccountNumber"), json.getString("AgentCommission"),
                                    json.getString("ProcessCode"), json.getString("Stan")), ar1 -> {
                                if (ar1.succeeded()) {
                                    tx.preparedQuery("INSERT INTO tb_transactions (ShortCode,AccountNumber,Amount,Message,Stan,TransactionDate,ProcessCode,AccountNames,BrAccount,DepositorName,IMEI) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                                            .execute(Tuple.of(json.getString("ShortCode"),
                                                    json.getString("AccountNumber"),
                                                    json.getString("Amount"),
                                                    json.getString("Message"),
                                                    json.getString("Stan"),
                                                    json.getString("TransactionDate"),
                                                    json.getString("ProcessCode"),
                                                    json.getString("AccountNames"),
                                                    json.getString("BrAccount", "***"),
                                                    json.getString("DepositorName", "***"),
                                                    json.getString("IMEI")), ar2 -> {
                                                if (ar2.succeeded()) {
                                                    // Commit the transaction
                                                    // the connection will automatically return to the pool
                                                    tx.commit(ar3 -> {
                                                        if (ar3.succeeded()) {
                                                            System.out.println("ComAndTransactionRecords | update Transaction succeeded");
                                                        } else {
                                                            System.out.println("ComAndTransactionRecords | Transaction failed " + ar3.cause().getMessage());
                                                        }
                                                    });
                                                }
                                            });
                                } else {
                                    System.out.printf("ComAndTransactionRecords connection error | %s", ar1.cause());
                                    // No need to close connection as transaction will abort and be returned to the pool
                                }
                            });
                }
            });
        });
    }
}
