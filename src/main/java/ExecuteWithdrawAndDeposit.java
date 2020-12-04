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

public class ExecuteWithdrawAndDeposit extends AbstractVerticle {
    @Override
    public void start(Promise<Void> promise) {
        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("withdraw_deposit_exec", message -> {
            JsonObject json = message.body();

            System.out.printf("Withdraw| Deposit Request | %s", json.encodePrettily());

            try {
                JsonObject customer = PinVerify
                        .getCustomerDetails(json.getString("AccountNumber"),config().getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL"));
                json.put("Mobile", customer.getString("Mobile", "Namba ya Simu"));
                json.put("AccountNames", customer.getString("Name", "Jina la mteja"));

                Connection connection = PinVerify.getDataSource(config()
                        .getString("USER"), config().getString("PASSWORD"),config().getString("JDBC_URL")).getConnection();
                if (connection != null) {
                    System.out.println("Connection to DB successful");
                    CallableStatement statement = connection.prepareCall("{ call proc_AgencyPostingWC(?,?,?,?,?,?,?,?,?,?,?) }");
                    statement.setString(1, json.getString("ShortCode"));
                    statement.setString(2, json.getString("ProcessCode"));
                    statement.setString(3, json.getString("AccountNumber"));
                    statement.setString(4, json.getString("Amount"));
                    statement.setInt(5, 0);
                    statement.setString(6, json.getString("DepositorName"));
                    statement.setString(7, json.getString("AgentAccount"));
                    statement.setString(8, String.valueOf(json.getInteger("ChargeAmount")));
                    statement.setString(9, json.getString("Stan"));
                    statement.setString(10, String.valueOf(json.getInteger("AgentCommission")));
                    statement.setString(11, String.valueOf(json.getInteger("UcbCommission")));
                    java.sql.ResultSet result = statement.executeQuery();

                    if (!result.next()) {
                        System.out.println("=======================ExecuteWithdrawAndDeposit ResultSet in empty===============");
                        eventBus.publish("mqtt", new JsonObject()
                                .put("Status", "01")
                                .put("Topic", json.getString("Topic"))
                                .put("ProcessCode", json.getString("ProcessCode"))
                                .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                    } else {

                        do {
                            String status = result.getString("Field2");
                            String Field1 = result.getString("Field1");
                            System.out.printf("Response back from BRNET | %s\n", Field1);
                            if (status.equals("00")) {
                                String transactionDate = LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.UK));

                                eventBus.publish("updateCommission", json
                                        .put("Message",json.getString("ProcessCode").concat(" | ").concat(json.getString("Amount")))
                                        .put("AgentCommission", String.valueOf(json.getInteger("AgentCommission"))));

                                eventBus.publish("mqtt", new JsonObject()
                                        .put("Status", "00")
                                        .put("TransactionDate", transactionDate)
                                        .put("Topic", json.getString("Topic"))
                                        .put("ProcessCode", json.getString("ProcessCode"))
                                        .put("Message", json.getString("ProcessCode").concat(" | ").concat(json.getString("Amount")))
                                        .put("AccountNames", json.getString("AccountNames")));
                            } else {
                                eventBus.publish("mqtt", new JsonObject()
                                        .put("Status", "01")
                                        .put("ProcessCode", json.getString("ProcessCode"))
                                        .put("Topic", json.getString("Topic"))
                                        .put("Message", status));
                            }

                        } while (result.next());
                    }


                } else {
                    eventBus.publish("mqtt", new JsonObject()
                            .put("Status", "01")
                            .put("Topic", json.getString("Topic"))
                            .put("ProcessCode", json.getString("ProcessCode"))
                            .put("Message", "Kuna tatizo la kimitambo. Tafadhali jaribu tena baadaye."));
                    System.out.println("=========ExecuteWithdrawAndDeposit unable to obtain database connection=======");
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
