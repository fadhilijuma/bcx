package util;

import channel.ChannelManager;
import com.zaxxer.hikari.HikariDataSource;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOPackager;
import org.jpos.iso.ISOUtil;
import org.jpos.iso.packager.ISO93APackager;
import org.jpos.security.EncryptedPIN;
import org.jpos.security.SMException;
import org.jpos.security.SecureDESKey;
import org.jpos.security.jceadapter.JCEHandlerException;

import javax.sql.DataSource;
import java.security.Security;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class PinVerify {

    public static JsonObject Validate(JsonObject jsonObject, ChannelManager channelManager, Vertx vertx) {
        System.out.println("PIN verification message: " + jsonObject.encodePrettily());
        try {
            System.out.println("KEY: " + jsonObject.getString("Key"));
            LocalDate currentDate = LocalDate.now();
            LocalDateTime localDateTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMdd");
            DateTimeFormatter format = DateTimeFormatter.ofPattern("MMddHHmmss");
            DateTimeFormatter formats = DateTimeFormatter.ofPattern("hhmmss");
            System.out.printf("ICC DATA  >>>>> [ %s ]\n", jsonObject.getString("ICC"));
            System.out.println("Constructing ISO Message");
            String PIN = jsonObject.getString("pin");
            String PAN = jsonObject.getString("pan");
            String PINBLOCK = DecryptPWK(PIN, PAN, jsonObject.getString("Key"));
            Map<String, String> _switchFields = new HashMap<>();
            ISOPackager packagr = new ISO93APackager();
            ISOMsg data = new ISOMsg();
            data.setPackager(packagr);
            data.setMTI("0100");
            String TRACK2DATA = jsonObject.getString("track2");
            String EXP = jsonObject.getString("expiry");
            data.set(2, PAN);
            data.set(3, "390000");
            data.set(4, "000000000000");
            data.set(7, localDateTime.format(format));
            data.set(11, "102926");
            data.set(12, localDateTime.format(formats));
            data.set(13, currentDate.format(formatter));
            data.set(14, EXP);
            data.set(18, "6011");
            data.set(22, "051");
            data.set(25, "00");
            data.set(26, "04");
            data.set(32, "502919");
            data.set(35, trimLeftString(TRACK2DATA));
            data.set(37, "000407393861");
            data.set(40, "206");
            data.set(41, "UCB00101");
            data.set(42, "UCB000000000001");
            data.set(43, "Uchumi HQ Moshi        Kilimanjaro    TZ");
            data.set(49, "834");
            data.set(52, ISOUtil.hex2byte(PINBLOCK));
            data.set(123, "911101511344101");
            logISOMsg(data);
            ISOMsg _isoResponse = channelManager.sendMsg(data);
            if (_isoResponse != null) {
                logISOMsg(_isoResponse);

                for (int i = 1; i <= _isoResponse.getMaxField(); ++i) {
                    if (_isoResponse.hasField(i)) {
                        _switchFields.put(String.valueOf(i), _isoResponse.getString(i));
                    }
                }

                System.out.printf("Response from BCX [ %s ]\n", _switchFields);
                if (_switchFields.get("39").equals("00")) {
                    System.out.println("Successful response from BCX. Obtaining account details from DB");
                    String AccountNumber = _switchFields.get("48").substring(0, 28).trim();

                    jsonObject.put("AccountNumber", AccountNumber);
                    String stamp = Long.toString(Instant.now().getEpochSecond());
                    jsonObject.put("Status", "00");
                    jsonObject.put("Stan", AccountNumber.concat(stamp.substring(stamp.length() - 4)));
                    System.out.printf("Account Number Here >>>>> [ %s ]\n", AccountNumber);
                    return jsonObject;
                } else {
                    System.out.printf("WRONG PIN for Track2 [ %s ]", jsonObject.getString("track2"));
                    return jsonObject.put("Status", "PIN sio sahihi..");
                }
            } else {
                System.out.printf("No response from BCX for Track2 [ %s ]\n", jsonObject.getString("track2"));
                return jsonObject.put("Status", "Kuna tatizo la kiufundi. Jaribu tena baadaye..");

            }
        } catch (Exception ex) {
            System.out.printf("Exception: [%s]\n", ex.getMessage());
            return jsonObject.put("Status", "Kuna tatizo la kiufundi. Jaribu tena baadaye..");
        }
    }

    public static String trimLeft(String string) {
        int stringLength = string.length();

        int i;
        for (i = 0; i < stringLength && string.charAt(i) == ' '; ++i) {
        }

        return i == 0 ? string : string.substring(i);
    }

    public static String trimLeftString(String string) {
        return trimLeft(string);
    }

    public static String DecryptPWK(String PIN, String PAN, String ZPK) throws SMException {
        String EncryptionPin = null;

        try {
            Security.addProvider(new BouncyCastleProvider());
            org.jpos.security.jceadapter.JCESecurityModule jcesecmod = new org.jpos.security.jceadapter.JCESecurityModule("/lmk/uchumi.lmk", "com.sun.crypto.provider.SunJCE");
            SecureDESKey zmk = jcesecmod.formKEYfromClearComponents((short) 128, "ZMK", "04D6E59DEA3EC1762F2C830E1F64F8C1", "F2AB737A9273341CCDB51A3EC119FDB0", "00000000000000000000000000000000");
            System.out.printf("ZMK====>>> [ %s ]\n", ISOUtil.byte2hex(zmk.getKeyBytes()));
            System.out.printf("ZMK CHECKVALUE====>>> [ %s ]\n", ISOUtil.byte2hex(zmk.getKeyCheckValue()));
            System.out.printf("ZPK WITH CHECKVALUE====>>> [ %s ]\n", ZPK);
            String ZPK_HEX = ZPK.substring(0, 32);
            System.out.printf("ZPK WITHOUT CHECKVALUE====>>> [ %s ]\n", ZPK_HEX);
            byte[] ZPK_FROM_BCX = ISOUtil.hex2byte(ZPK_HEX);
            System.out.printf("ZPK FROM BCX_BYTES LENGTH====>>>[ %d ]\n", ZPK_FROM_BCX.length);
            SecureDESKey ZonePinKey = jcesecmod.importKeyImpl((short) 128, "ZPK", ZPK_FROM_BCX, zmk, true);
            System.out.printf("ZPK====>>> [ %s ]\n", ISOUtil.byte2hex(ZonePinKey.getKeyBytes()));
            System.out.printf("ZPK CHECKVALUE====>>> [ %s ]\n", ISOUtil.byte2hex(ZonePinKey.getKeyCheckValue()));
            EncryptedPIN pinUnderLMK = jcesecmod.encryptPIN(PIN, PAN);
            System.out.printf("PIN BLOCK UNDER LMK ==>>[ %s ]\n", ISOUtil.hexString(pinUnderLMK.getPINBlock()));
            EncryptedPIN pinUnderZPK = jcesecmod.exportPIN(pinUnderLMK, ZonePinKey, (byte) 0);
            System.out.printf("PIN BLOCK UNDER ZPK ==>> [ %s ]\n", ISOUtil.hexString(pinUnderZPK.getPINBlock()));
            System.out.printf("CHECK VALUE:====>>>> [ %s ]\n", ISOUtil.byte2hex(ZonePinKey.getKeyCheckValue()));
            EncryptedPIN pinUnderTPK = jcesecmod.exportPIN(pinUnderLMK, ZonePinKey, (byte) 0);
            System.out.printf("PINBLOCK ======>>[  %s  ]\n", ISOUtil.hexString(pinUnderTPK.getPINBlock()));
            EncryptionPin = ISOUtil.hexString(pinUnderTPK.getPINBlock());
        } catch (JCEHandlerException jceHandlerException) {
            System.out.printf("ERROR WHILE CREATING PIN BLOCK ==>> [ %s ]\n", jceHandlerException.getMessage());
        }

        return EncryptionPin;
    }

    public static void logISOMsg(ISOMsg msg) {
        ISO93APackager packager = new ISO93APackager();
        System.out.println("----ISO MESSAGE-----");
        try {
            System.out.printf("  MTI : %s\n", msg.getMTI());
            for (int i = 1; i <= msg.getMaxField(); ++i) {
                if (msg.hasField(i)) {
                    System.out.printf("Field-%s : %s : %s\n", i, packager.getFieldPackager(i).getDescription(), msg.getString(i));
                }
            }
        } catch (ISOException isoException) {
            System.out.printf("Error while logging ISOMsg [ %s ]", isoException.getMessage());
        } finally {
            System.out.println("--------------------");
        }

    }

    public static DataSource getDataSource(String userName, String password,String url) {
        final HikariDataSource ds = new HikariDataSource();
        ds.setMaximumPoolSize(10);
        ds.setDataSourceClassName("com.microsoft.sqlserver.jdbc.SQLServerDataSource");
        ds.addDataSourceProperty("url", url);
        ds.addDataSourceProperty("user", userName);
        ds.addDataSourceProperty("password", password);
        ds.setPoolName("uchumiCP");
        return ds;
    }

    public static JsonObject getCustomerDetails(String accountNumber, String userName, String password,String url) {
        String sql = "select Name,Mobile,ClientID from t_AccountCustomer where AccountID=?";
        String name = "";
        String mobile = "";
        String ClientID = "";
        try {
            PreparedStatement preparedStatement = getDataSource(userName, password,url).getConnection().prepareStatement(sql);
            preparedStatement.setString(1, accountNumber);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next()) {
                System.out.println("=======================ResultSet from check name and mobile===============");
            } else {

                do {
                    name = resultSet.getString("Name");
                    mobile = resultSet.getString("Mobile");
                    ClientID = resultSet.getString("ClientID");

                } while (resultSet.next());
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            return new JsonObject()
                    .put("Status", "01");
        }
        return new JsonObject()
                .put("Status", "00")
                .put("Name", name)
                .put("ClientID", ClientID)
                .put("Mobile", mobile);
    }
}
