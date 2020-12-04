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

public class ExecuteBalAndMini extends AbstractVerticle {
    @Override
    public void start(Promise<Void> promise) {
        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("bal_mini", message -> {
            JsonObject json = message.body();
            System.out.printf("New Request to Transaction Execution: %s\n", json.encodePrettily());
            try {
                JsonObject customer = PinVerify.getCustomerDetails(json.getString("AccountNumber"), config().getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL"));
                if (customer.getString("Status").equals("00")) {
                    json.put("Mobile", customer.getString("Mobile"));
                    json.put("AccountNames", customer.getString("Name"));
                } else {
                    json.put("AccountNames", "Jina lake mteja");
                }
                Connection connection = PinVerify.getDataSource(config().getString("USER"), config().getString("PASSWORD"), config().getString("JDBC_URL")).getConnection();
                if (connection != null) {
                    System.out.println("Connection to DB successful");
                    CallableStatement statement = connection.prepareCall("{ call proc_AgencyMini(?,?,?,?,?,?,?,?,?) }");
                    statement.setString(1, json.getString("ShortCode"));
                    statement.setString(2, json.getString("ProcessCode"));
                    statement.setString(3, json.getString("AccountNumber"));
                    statement.setInt(4, 500);
                    statement.setString(5, json.getString("Stan"));
                    statement.setString(6, json.getString("AgentAccount"));
                    statement.setString(7, config().getString("COMMISSION_GL"));
                    statement.setInt(8, 0);
                    statement.setInt(9, 500);
                    java.sql.ResultSet result = statement.executeQuery();

                    if (!result.next()) {
                        System.out.println("=======================ResultSet in empty===============");
                        eventBus.publish("mqtt", new JsonObject()
                                .put("Status", "01")
                                .put("Topic", json.getString("Topic"))
                                .put("ProcessCode", json.getString("ProcessCode"))
                                .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                    } else {

                        do {
                            String Field2 = result.getString("Field2");
                            String status = result.getString("Field1");
                            System.out.printf("Response back from BRNET | %s\n", Field2);
                            if (status.equals("00")) {
                                String transactionDate = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.UK));
                                switch (json.getString("ProcessCode")) {
                                    case "03":
                                        eventBus.publish("mqtt", new JsonObject()
                                                .put("Status", "00")
                                                .put("TransactionDate", transactionDate)
                                                .put("AccountNumber", json.getString("AccountNumber"))
                                                .put("Topic", json.getString("Topic"))
                                                .put("AgentCode", json.getString("ShortCode"))
                                                .put("AccountNames", json.getString("AccountNames"))
                                                .put("ProcessCode", json.getString("ProcessCode"))
                                                .put("Amount", Field2));

                                        eventBus.publish("updateTransaction", json
                                                .put("Message", "Balance Inquiry")
                                                .put("Amount", Field2));

                                        break;
                                    case "04":
                                        eventBus.publish("mqtt", new JsonObject()
                                                .put("Status", "00")
                                                .put("Topic", json.getString("Topic"))
                                                .put("ProcessCode", json.getString("ProcessCode"))
                                                .put("TransactionDate", transactionDate)
                                                .put("AccountNumber", json.getString("AccountNumber"))
                                                .put("Message", Field2));

                                        eventBus.publish("updateTransaction", json
                                                .put("Message", Field2)
                                                .put("Amount", Field2));

                                        break;
                                }
                            } else {
                                eventBus.publish("mqtt", new JsonObject()
                                        .put("Status", "01")
                                        .put("ProcessCode", json.getString("ProcessCode"))
                                        .put("Topic", json.getString("Topic"))
                                        .put("Message", Field2));
                            }


                        } while (result.next());
                    }


                } else {
                    eventBus.publish("mqtt", new JsonObject()
                            .put("Status", "01")
                            .put("Topic", json.getString("Topic"))
                            .put("ProcessCode", json.getString("ProcessCode"))
                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                    System.out.println("=========unable to obtain database connection=======");
                }
            } catch (SQLException throwables) {
                eventBus.publish("mqtt", new JsonObject()
                        .put("Status", "01")
                        .put("Topic", json.getString("Topic"))
                        .put("ProcessCode", json.getString("ProcessCode"))
                        .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));

                throwables.printStackTrace();
            }
        });

    }


}