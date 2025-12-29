package com.iot.app.service;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.stereotype.Service;

@Service
public class MqttService {

    // Endereço do seu broker (conforme seu código C++: 192.168.1.2)
    private static final String BROKER_URL = "tcp://192.168.1.2:1883";
    private static final String CLIENT_ID = "SpringAppServer";

    public void publish(String topic, String content) {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            MqttClient sampleClient = new MqttClient(BROKER_URL, CLIENT_ID, persistence);
            
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            
            // Se seu broker tiver senha, configure aqui:
            // connOpts.setUserName("seu_usuario");
            // connOpts.setPassword("sua_senha".toCharArray());

            System.out.println("Conectando ao broker: " + BROKER_URL);
            sampleClient.connect(connOpts);
            
            System.out.println("Publicando mensagem: " + content);
            MqttMessage message = new MqttMessage(content.getBytes());
            message.setQos(1); // Qualidade de serviço 1 (garante entrega)
            
            sampleClient.publish(topic, message);
            
            sampleClient.disconnect();
            
        } catch (MqttException me) {
            System.err.println("Erro MQTT: " + me.getMessage());
            me.printStackTrace();
        }
    }
}