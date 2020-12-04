import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class ToMQTT extends AbstractVerticle {

    @Override
    public void start() {
        EventBus eventBus = vertx.eventBus();
        eventBus.<JsonObject>consumer("mqtt", req -> {
            JsonObject jsonObject = req.body();
            System.out.printf("Message to MQTT | %s\n", jsonObject.encodePrettily());
            int qos = 1;
            String clientId = "uchumi-02-agent";

            try {
                MqttClient sampleClient = new MqttClient(config().getString("MQTT_FULL_PATH"), clientId);
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);

                System.out.println("Connecting to broker: " + config().getString("MQTT_FULL_PATH"));

                sampleClient.connect(connOpts);

                System.out.println("Connected");
                System.out.println("Publishing message: " + jsonObject.encodePrettily());

                MqttMessage message = new MqttMessage(jsonObject.encode().getBytes());
                message.setQos(qos);
                sampleClient.publish(jsonObject.getString("Topic"), message);

                System.out.println("Message published");

                sampleClient.disconnect();
                System.out.println("Disconnected");

            } catch (MqttException me) {
                System.out.println("reason " + me.getReasonCode());
                System.out.println("msg " + me.getMessage());
                System.out.println("loc " + me.getLocalizedMessage());
                System.out.println("cause " + me.getCause());
                System.out.println("excep " + me);
                me.printStackTrace();
            }

        });

    }
}
