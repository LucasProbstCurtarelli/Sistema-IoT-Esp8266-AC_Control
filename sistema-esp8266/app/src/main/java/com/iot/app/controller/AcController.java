package com.iot.app.controller;

import com.iot.app.dto.AcPayload;
import com.iot.app.service.MqttService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ac")
public class AcController {

    @Autowired
    private MqttService mqttService;

    // Tópico definido no seu código C++
    private static final String TOPIC_CMD = "/iot/sensores/ac/comando";

    @PostMapping
    public ResponseEntity<String> sendCommand(@RequestBody AcPayload payload) {
        try {
            // Converte o objeto Java de volta para JSON String
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = mapper.writeValueAsString(payload);

            // Envia para o MQTT
            mqttService.publish(TOPIC_CMD, jsonString);

            return ResponseEntity.ok("Comando enviado com sucesso!");
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro: " + e.getMessage());
        }
    }
}