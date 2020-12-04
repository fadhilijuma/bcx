import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import util.PinVerify;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

public class Funds extends AbstractVerticle {
    @Override
    public void start(Promise<Void> promise) {
        EventBus eventBus = this.vertx.eventBus();
        System.out.println("Funds started....");
        eventBus.<JsonObject>consumer("funds", message -> {
            JsonObject jsonObject = message.body();
            jsonObject.put("Topic", jsonObject.getString("IMEI").concat("/ft"));
            jsonObject.put("Key", MainLauncher.KEY);
            jsonObject.put("ProcessCode", "05");
            System.out.println("Funds Transfer Request |: " + jsonObject.encodePrettily());
            JsonObject json = PinVerify.Validate(jsonObject, MainLauncher.channelManager, vertx);
            JsonObject customer = PinVerify.getCustomerDetails(json.getString("AccountNumber"), config().getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL"));
            if (json.getString("Status").equals("00")) {
                try {
                    Connection connection = PinVerify.getDataSource(config().getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL")).getConnection();
                    if (connection != null) {
                        System.out.println("Connection to DB successful");
                        CallableStatement statement = connection.prepareCall("{ call proc_AgencyFunds(?,?,?,?,?,?,?,?,?,?,?) }");
                        statement.setString(1, json.getString("ShortCode"));
                        statement.setString(2, "05");
                        statement.setString(3, json.getString("AccountNumber"));
                        statement.setString(4, json.getString("Amount"));
                        statement.setString(5, config().getString("FT_TRANSFER_CHARGE"));
                        statement.setString(6, json.getString("Stan"));
                        statement.setString(7, json.getString("AgentAccount"));
                        statement.setString(8, json.getString("BrAccount"));
                        statement.setString(9, config().getString("COMMISSION_GL"));
                        statement.setString(10, config().getString("COMMISSION_TO_AGENT_ON_FT"));
                        statement.setString(11, config().getString("COMMISSION_TO_UCB_ON_FT"));
                        java.sql.ResultSet result = statement.executeQuery();

                        if (!result.next()) {
                            System.out.println("=======================Funds ResultSet in empty===============");
                            eventBus.publish("mqtt", new JsonObject()
                                    .put("Status", "01")
                                    .put("Topic", json.getString("Topic"))
                                    .put("ProcessCode", json.getString("ProcessCode"))
                                    .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                        } else {

                            do {
                                String Field2 = result.getString("Field2");
                                String Field1 = result.getString("Field1");
                                System.out.printf("Response back from BRNET | %s\n", Field2);
                                if (Field2.equals("00")) {
                                    String transactionDate = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.UK));

                                    eventBus.publish("updateCommission", json
                                            .put("AccountNames", customer.getString("Name", "Jina la mteja"))
                                            .put("AgentCommission", config().getString("COMMISSION_TO_AGENT_ON_FT"))
                                            .put("Message", json.getString("ProcessCode").concat(" | ").concat(json.getString("BrAccount")).concat(" | Amount: ").concat(json.getString("Amount"))));

                                    eventBus.publish("mqtt", new JsonObject()
                                            .put("Status", "00")
                                            .put("TransactionDate", transactionDate)
                                            .put("Topic", json.getString("Topic"))
                                            .put("AccountNumber", json.getString("AccountNumber"))
                                            .put("ProcessCode", "05")
                                            .put("AccountNames", customer.getString("Name", "Jina la mteja"))
                                            .put("BrAccount", json.getString("BrAccount"))
                                            .put("Amount", json.getString("Amount")));
                                } else {
                                    eventBus.publish("mqtt", new JsonObject()
                                            .put("Status", "01")
                                            .put("Topic", json.getString("Topic"))
                                            .put("ProcessCode", "05")
                                            .put("Message", Field1));
                                }

                            } while (result.next());
                        }


                    } else {
                        eventBus.publish("mqtt", new JsonObject()
                                .put("Status", "01")
                                .put("Topic", json.getString("Topic"))
                                .put("ProcessCode", json.getString("ProcessCode"))
                                .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                        System.out.println("=========Funds unable to obtain database connection=======");
                    }
                } catch (SQLException throwables) {
                    eventBus.publish("mqtt", new JsonObject()
                            .put("Status", "01")
                            .put("Topic", json.getString("Topic"))
                            .put("ProcessCode", json.getString("ProcessCode"))
                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));

                    throwables.printStackTrace();
                }
            } else {
                eventBus.publish("mqtt", new JsonObject()
                        .put("Status", "01")
                        .put("Topic", json.getString("Topic"))
                        .put("ProcessCode", "05")
                        .put("Message", json.getString("Status")));
            }

        });
        promise.complete();
    }
}
