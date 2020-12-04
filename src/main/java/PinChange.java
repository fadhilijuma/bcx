import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.*;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.ISO93APackager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static util.PinVerify.*;

public class PinChange extends AbstractVerticle {
    @Override
    public void start() {
        EventBus eventBus = vertx.eventBus();
        System.out.println("PinChange started.....");
        eventBus.<JsonObject>consumer("changePIN", message -> {
            JsonObject jsonObject = message.body();
            System.out.printf("ATM PIN change request | %s", jsonObject.encodePrettily());
            jsonObject.put("Topic", jsonObject.getString("ShortCode").concat("/pin_reset_response"));
            try {
                System.out.println("KEY: " + MainLauncher.KEY);
                System.out.println("Pin Verification message received: " + message.body());
                LocalDate currentDate = LocalDate.now();
                LocalDateTime localDateTime = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMdd");
                DateTimeFormatter format = DateTimeFormatter.ofPattern("MMddHHmmss");
                DateTimeFormatter formats = DateTimeFormatter.ofPattern("hhmmss");
                String OLD_PIN = jsonObject.getString("old_pin");
                String NEW_PIN = jsonObject.getString("new_pin");
                String PAN = jsonObject.getString("pan");
                String OLDPIN_PINBLOCK = DecryptPWK(OLD_PIN, PAN, MainLauncher.KEY);
                String NEWPIN_PINBLOCK = DecryptPWK(NEW_PIN, PAN, MainLauncher.KEY);
                String TRACK2DATA = jsonObject.getString("track2");
                System.out.printf("ICC DATA  >>>>> [ %s ]\n", jsonObject.getString("ICC"));
                Map<String, String> _switchFields = new HashMap<>();
                ISOPackager packagr = new ISO93APackager();
                ISOMsg data = new ISOMsg();
                data.setPackager(packagr);
                data.setMTI("0600");
                data.set(3, "920000");
                data.set(7, localDateTime.format(format));
                data.set(11, "102926");
                data.set(12, localDateTime.format(formats));
                data.set(13, currentDate.format(formatter));
                data.set(18, "6011");
                data.set(22, "051");
                data.set(25, "00");
                data.set(26, "04");
                data.set(35, trimLeftString(TRACK2DATA));
                data.set(41, "UCB00101");
                data.set(42, "UCB000000000001");
                data.set(43, "Uchumi HQ Moshi        Kilimanjaro    TZ");
                data.set(52, ISOUtil.hex2byte(OLDPIN_PINBLOCK));
                String builder = "01" + NEWPIN_PINBLOCK + "303030303030303030303030303030303030303030303030303030303030303030303030303030";
                data.set(53, ISOUtil.hex2byte(builder));
                data.set(56, "0000");
                data.set(123, "911101511344101");
                logISOMsg(data);
                ISOMsg _isoResponse = MainLauncher.channelManager.sendMsg(data);
                if (_isoResponse != null) {
                    logISOMsg(_isoResponse);

                    for (int i = 1; i <= _isoResponse.getMaxField(); ++i) {
                        if (_isoResponse.hasField(i)) {
                            _switchFields.put(String.valueOf(i), _isoResponse.getString(i));
                        }
                    }

                    if (_switchFields.get("39").equals("00")) {
                        System.out.println(">>>>>>>>Pin Change Successful");

                        sendNotification(jsonObject.getString("ShortCode"),"PIN change successful.");

                    } else if (_switchFields.get("39").equals("55")) {
                        sendNotification(jsonObject.getString("ShortCode"),"PIN change failed. Invalid PIN.");
                    } else {
                        System.out.println(">>>>>>>>>Pin Invalid");
                        sendNotification(jsonObject.getString("ShortCode"),"PIN change failed. Invalid PIN.");
                    }
                } else {
                    System.out.println(">>>>>>>>>Pin Invalid");
                    sendNotification(jsonObject.getString("ShortCode"),"PIN change failed. Invalid PIN.");
                }
            } catch (Exception exception) {
                sendNotification(jsonObject.getString("ShortCode"),"PIN change failed. Invalid PIN.");
                System.out.printf("Exception: %s", exception.getMessage());
            }
        });
    }

    private void sendNotification(String agentID, String Message) {
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

                System.out.println("PIN Change Connected to Uchumi database");

                // Obtain our connection
                SqlConnection conn = ar1.result();
//SELECT Commission FROM tb_DepositCommission where ? BETWEEN FromAmount AND ToAmount
                // All operations execute on the same connection
                conn
                        .preparedQuery("SELECT MobileNumber FROM tb_agents where AgentID=?")
                        .execute(Tuple.of(agentID), ar -> {
                            if (ar.succeeded()) {
                                RowSet<Row> rows = ar.result();
                                if (rows.size() == 0) {
                                    System.out.printf("Agent does not exist %s |\n", agentID);

                                } else {
                                    System.out.println("Got " + rows.size() + " rows ");
                                    for (Row row : rows) {
                                        System.out.printf("ShortCode: %s | MobileNumber: %s\n", agentID, row.getValue("MobileNumber"));

                                        WebClient webClient = WebClient.create(vertx);

                                        String MSISDN = row.getValue("MobileNumber").toString();
                                        if (MSISDN.startsWith("0")) {
                                            MSISDN = MSISDN.replaceFirst("0", "255");
                                        } else if (MSISDN.startsWith("+")) {
                                            MSISDN = MSISDN.replace("\\+", "");
                                        }

                                        String smsRequest = "<?xml version=\"1.0\"?>" .concat("<COMMAND>")
                                                .concat("<SMS_TITLE>UCHUMI BANK</SMS_TITLE>")
                                                .concat("<CLIENT_ID>5029190195746813</CLIENT_ID>")
                                                .concat("<MSISDN>")
                                                .concat(MSISDN)
                                                .concat("</MSISDN>")
                                                .concat("<MESSAGE>")
                                                .concat(Message)
                                                .concat("</MESSAGE>")
                                                .concat("</COMMAND>");

                                        webClient.post(7071, "172.25.27.80", "/UmojaMobileTransactions/services/UmojaMobileService/smsgateway/")
                                                .as(BodyCodec.jsonObject())
                                                .sendBuffer(Buffer.buffer(smsRequest), responseSms -> {
                                                    if (ar.succeeded()) {
                                                        System.out.println("PIN change notification SMS Connection to BCX Successful....");
                                                        HttpResponse<JsonObject> bufferHttpResponse = responseSms.result();
                                                        JsonObject body = bufferHttpResponse.body();
                                                        System.out.printf("PIN change notification SMS Response from BCX | %s\n", body.encodePrettily());
                                                    } else {
                                                        System.out.printf("PIN change notification SMS Connection to BCX Failed....| %s", ar.cause());

                                                    }

                                                });
                                    }
                                }

                            } else {
                                System.out.println("PIN change notification Failure: " + ar.cause().getMessage());
                            }
                        });

            } else {
                System.out.println("PIN Change agent notification Could not connect: " + ar1.cause().getMessage());
            }
        });
    }

}
