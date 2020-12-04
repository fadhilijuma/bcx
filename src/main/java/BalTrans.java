import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;

public class BalTrans extends AbstractVerticle {
    @Override
    public void start() {
        System.out.println("BalTrans started...");

        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("updateTransaction", req -> {
            JsonObject json = req.body();
            System.out.printf("Mini/Bal updateTransaction update request  |  %s\n", json.encodePrettily());
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

                    // All operations execute on the same connection
                    conn
                            .preparedQuery("INSERT INTO tb_transactions (ShortCode,AccountNumber,Amount,Message,Stan,TransactionDate,ProcessCode,AccountNames,BrAccount,DepositorName,IMEI) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                            .execute(Tuple.of(json.getString("ShortCode"),
                                    json.getString("AccountNumber"),
                                    json.getString("Amount"),
                                    json.getString("Message"),
                                    json.getString("Stan"),
                                    json.getString("TransactionDate"),
                                    json.getString("ProcessCode"),
                                    json.getString("AccountNames"),
                                    "***",
                                    "***",
                                    json.getString("IMEI")), ar2 -> {
                                if (ar2.succeeded()) {
                                    System.out.println("=================Update Mini/Bal Transaction update successful========");
                                } else {
                                    // Release the connection to the pool
                                    conn.close();
                                }
                            });
                } else {
                    System.out.println("=================mini/Bal Could not connect to mysql to update transaction: " + ar1.cause().getMessage());
                }
            });
        });

    }
}
