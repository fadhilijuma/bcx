import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import util.PinVerify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class WebApi extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {
        System.out.println("WebApi started......");
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.post().handler(BodyHandler.create());

        JsonObject jdbcConfig = new JsonObject()
                .put("url", config().getString("JDBC_URL"))
                .put("driver_class", "com.microsoft.sqlserver.jdbc.SQLServerDriver")
                .put("user", config().getString("USER"))
                .put("password", config().getString("PASSWORD"))
                .put("max_pool_size", 30);

        EventBus eventBus = vertx.eventBus();

        router.post("/Ucb/PinChange").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();

            System.out.printf("pin_change request  |  %s\n", json.encodePrettily());

            eventBus.publish("changePIN", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/PinReset").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();

            System.out.printf("pin_reset request  |  %s\n", json.encodePrettily());
            eventBus.publish("pinReset", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/CardActivation").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("card_activation request  |  %s\n", json.encodePrettily());

            eventBus.publish("InstantCardIssuing", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/CashDeposit").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("deposit request  |  %s\n", json.encodePrettily());

            eventBus.publish("deposit", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/Withdrawal").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("withdraw request  |  %s\n", json.encodePrettily());

            eventBus.publish("withdraw", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/Balance").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("balance request  |  %s\n", json.encodePrettily());

            eventBus.publish("balance", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/Mini").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("mini request  |  %s\n", json.encodePrettily());

            eventBus.publish("mini", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/FundsTransfer").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("ft request  |  %s\n", json.encodePrettily());

            eventBus.publish("funds", json);
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .end((new JsonObject())
                            .put("ResponseCode", "00").encode());
        });
        router.post("/Ucb/verifyCustomer").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("verifyCustomer request  |  %s\n", json.encodePrettily());
            System.out.println("Connecting to DB.....");
            try {
                Connection connection = PinVerify.getDataSource(config().getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL")).getConnection();
                if (connection != null) {
                    System.out.println("Connection to DB successful");
                    PreparedStatement statement = connection.prepareStatement("select Name from t_AccountCustomer where AccountID=?");
                    statement.setString(1, json.getString("AccountNumber"));
                    java.sql.ResultSet result = statement.executeQuery();
                    if (!result.next()) {
                        System.out.println("=======================verifyCustomer ResultSet is empty===============");
                        routingContext.response()
                                .putHeader("content-type", "application/json")
                                .end((new JsonObject())
                                        .put("ResponseCode", "01").encode());
                    } else {

                        do {
                            String Name = result.getString("Name");
                            System.out.printf("Response back from BRNET | %s", Name);
                            routingContext.response()
                                    .putHeader("content-type", "application/json")
                                    .end((new JsonObject())
                                            .put("ResponseCode", "00")
                                            .put("AccountNames", Name).encode());

                        } while (result.next());
                    }
                } else {
                    System.out.println("=========verifyCustomer unable to obtain database connection=======");
                    routingContext.response()
                            .putHeader("content-type", "application/json")
                            .end((new JsonObject())
                                    .put("ResponseCode", "01").encode());
                }
            } catch (SQLException throwables) {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .end((new JsonObject())
                                .put("ResponseCode", "01").encode());
                throwables.printStackTrace();
            }
        });
        router.post("/Ucb/AgentAccountBalance").handler(routingContext -> {
            JsonObject json = routingContext.getBodyAsJson();
            System.out.printf("AgentAccountBalance request  |  %s\n", json.encodePrettily());

            System.out.println("Connecting to DB.....");
            SQLClient sqlClient = JDBCClient.createShared(this.vertx, jdbcConfig);
            sqlClient.getConnection((resc) -> {
                if (resc.succeeded()) {
                    System.out.println("Connecting to DB successful.....");
                    SQLConnection connection = resc.result();
                    String PROCEDURE = "{ call proc_AgencyGetDetails(?) }";
                    connection.callWithParams(PROCEDURE, new JsonArray()
                            .add(json.getString("AccountNumber")), null, queryResult -> {
                        connection.close();

                        if (queryResult.succeeded()) {
                            ResultSet rs = queryResult.result();
                            JsonArray jsonArray = new JsonArray(rs.getRows());
                            JsonObject arrayJsonObject = jsonArray.getJsonObject(0);
                            System.out.printf("Results from DB.....%s\n", arrayJsonObject.encodePrettily());

                            routingContext.response()
                                    .putHeader("content-type", "application/json")
                                    .end((new JsonObject())
                                            .put("ResponseCode", "00")
                                            .put("AccountBalance", arrayJsonObject.getString("Balance")).encode());


                        } else {
                            routingContext.response()
                                    .putHeader("content-type", "application/json")
                                    .end((new JsonObject())
                                            .put("ResponseCode", "01").encode());
                            System.out.printf("Connection Exception: %s\n", queryResult.cause().toString());
                        }

                    });
                } else {
                    routingContext.response()
                            .putHeader("content-type", "application/json")
                            .end((new JsonObject())
                                    .put("ResponseCode", "01").encode());
                    System.out.printf("Connection Exception: %s\n", resc.cause().toString());
                }

            });
        });
        server.requestHandler(router).listen(9900, ar -> {
            if (ar.succeeded()) {
                promise.complete();
            } else {
                System.out.printf("Could not start a HTTP server  |  %s\n", ar.cause());
                promise.fail(ar.cause());
            }
        });
    }
}
