package com.atul.telemetry_engine.config;

import com.atul.telemetry_engine.model.SensorData;
import com.atul.telemetry_engine.model.TelemetryEntity;
import com.atul.telemetry_engine.repository.TelemetryRepository;
import com.atul.telemetry_engine.service.DspFilterService;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLSocketFactory;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

@EnableIntegration
@Configuration
public class MqttConfig {

    @Autowired
    private DspFilterService dspFilterService;
    @Autowired
    private TelemetryRepository telemetryRepository;
    @Value("${mqtt.broker}")
    private String brokerUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Value("${mqtt.topic}")
    private String topic;

    // @Value("${mqtt.username}")
    // private String username;

    // @Value("${mqtt.password}")
    // private String password;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { brokerUrl });
        options.setCleanSession(true);
        // options.setUserName(username);
        // options.setPassword(password.toCharArray());
        // options.setSocketFactory(SSLSocketFactory.getDefault());
        options.setAutomaticReconnect(true);
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        DirectChannel channel = new DirectChannel();
        channel.addInterceptor(new org.springframework.messaging.support.ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                System.err.println("DEBUG: Channel Interceptor caught message: " + message.getPayload());
                return message;
            }
        });
        return channel;
    }

    @Bean
    public MessageProducer inbound() {
        String uniqueClientId = clientId + "-" + java.util.UUID.randomUUID().toString();
        System.out.println("Using Client ID: " + uniqueClientId);

        DefaultPahoMessageConverter myConverter = new DefaultPahoMessageConverter();

        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(uniqueClientId,
                mqttClientFactory(), topic) {
            @Override
            public void messageArrived(String topic, org.eclipse.paho.client.mqttv3.MqttMessage mqttMessage) {
                // System.err.println("DEBUG:
                // MqttPahoMessageDrivenChannelAdapter.messageArrived() called for topic: " +
                // topic);
                try {
                    Message<?> message = myConverter.toMessage(topic, mqttMessage);
                    if (message != null) {
                        MessageChannel outputChannel = (MessageChannel) getOutputChannel();
                        if (outputChannel != null) {
                            outputChannel.send(message);
                        } else {
                            System.err.println("ERROR: Output channel is null!");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("ERROR: Failed to process MQTT message: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            @Override
            protected void onInit() {
                super.onInit();
            }
        };

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(myConverter);
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public org.springframework.boot.CommandLineRunner runner() {
        return args -> {
            new Thread(() -> {
                while (true) {
                    try {
                        System.out.println("App is running... " + java.time.LocalDateTime.now());
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        };
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler handler() {
        return message -> {
            try {
                Object payload = message.getPayload();
                System.out.println("DEBUG: Handler invoked! Payload class: " + payload.getClass().getName());

                String textPayload;
                if (payload instanceof byte[]) {
                    textPayload = new String((byte[]) payload);
                } else {
                    textPayload = payload.toString();
                }

                System.out.println("MQTT MESSAGE RECEIVED: " + textPayload);
                ObjectMapper mapper = new ObjectMapper();
                SensorData rawData = mapper.readValue(textPayload, SensorData.class);
                double originalVoltage = rawData.getVoltage();

                // 2. Pass it through the DSP Filter
                SensorData cleanedData = dspFilterService.processAndClean(rawData);

                // 3. Print the results to see the filter in action
                System.out.println("Processed -> Raw Voltage: " + String.format("%.2f", originalVoltage) +
                        " | Cleaned Voltage: " + String.format("%.2f", cleanedData.getVoltage()));
                        TelemetryEntity entity = new TelemetryEntity(
                        cleanedData.getSensorId(),
                        cleanedData.getVoltage(),
                        cleanedData.getCurrent(),
                        cleanedData.getTemperature(),
                        cleanedData.getTimestamp()
                );

                // 4. SAVE TO DATABASE
                telemetryRepository.save(entity);

                System.out.println("✅ Saved to DB -> ID: " + entity.getSensorId() + 
                                   " | V: " + String.format("%.2f", entity.getVoltage()));
            } catch (Exception e) {
                System.err.println("CRITICAL ERROR IN HANDLER: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }

    @EventListener
    public void handleMqttConnectionFailed(MqttConnectionFailedEvent event) {
        System.err.println("ERROR: MQTT Connection Failed: " + event.getCause().getMessage());
        event.getCause().printStackTrace();
    }

    @EventListener
    public void handleMqttSubscribed(MqttSubscribedEvent event) {
        System.out.println("SUCCESS: Connected and Subscribed to MQTT Broker on topic: " + event.getMessage());
    }
}