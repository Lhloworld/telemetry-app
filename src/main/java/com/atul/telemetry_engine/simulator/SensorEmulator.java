package com.atul.telemetry_engine.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Random;
import java.util.UUID;

public class SensorEmulator {

    public static void main(String[] args) {
        // 1. Configuration
        String brokerUrl = "tcp://127.0.0.1:1883";
        // String topic = "energy/sensor/device-001";
        // Let's use a topic that definitely matches "energy/sensor/+"
        String topic = "energy/sensor/device-001";

        String clientId = "simulator-" + UUID.randomUUID().toString();

        System.out.println("=========================================");
        System.out.println("Starting Sensor Emulator...");
        System.out.println("Broker: " + brokerUrl);
        System.out.println("Topic:  " + topic);
        System.out.println("Client: " + clientId);
        System.out.println("=========================================");

        try {
            // 2. Connect to Mosquitto
            MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            System.out.println("Attempting to connect...");
            client.connect(options);
            System.out.println("CONNECTED to Broker!");

            // 3. Start generating data
            ObjectMapper mapper = new ObjectMapper();
            Random random = new Random();

            while (true) {
                if (!client.isConnected()) {
                    System.err.println("Client disconnected. Reconnecting...");
                    // client.reconnect() might be automatic, but good to know
                }

                // Simulate Sensor Readings
                // Normal is 220V, fluctuates by +/- 5
                double voltage = 220 + (random.nextGaussian() * 5);
                // Normal is 10A
                double current = 10 + (random.nextGaussian() * 1);
                // 40 degrees C
                double temperature = 40 + (random.nextGaussian() * 2);

                // Occasional "Noise" (Spikes)
                if (random.nextInt(10) == 0) {
                    voltage += 50.0; // Sudden spike!
                    System.out.println(">>> INJECTING NOISE <<<");
                }

                // Create JSON payload
                ObjectNode payload = mapper.createObjectNode();
                payload.put("sensorId", "device-001");
                payload.put("voltage", voltage);
                payload.put("current", current);
                payload.put("temperature", temperature);
                payload.put("timestamp", System.currentTimeMillis());

                String jsonString = mapper.writeValueAsString(payload);

                // 4. Publish to MQTT
                MqttMessage message = new MqttMessage(jsonString.getBytes());
                message.setQos(1);
                client.publish(topic, message);

                System.out.println("[SENT] " + jsonString);

                // Wait 1 second before next reading
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}