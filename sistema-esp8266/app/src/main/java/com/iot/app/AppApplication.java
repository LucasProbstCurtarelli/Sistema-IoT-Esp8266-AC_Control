package com.iot.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal da aplicação Spring Boot.
 * 
 * Sistema de Automação Residencial - Aplicação web para controle de:
 * - Ar Condicionado via ESP8266 (MQTT)
 * - Lâmpadas Elgin 48BLED15WIFI (MQTT -> Node-RED -> UDP/HTTP/Tuya)
 * 
 * A aplicação expõe um dashboard web em http://localhost:8080
 * e se comunica com dispositivos IoT via MQTT através do Node-RED.
 * 
 * @author Sistema de Automação Residencial
 * @version 1.0
 */
@SpringBootApplication
public class AppApplication {

    /**
     * Método principal que inicia a aplicação Spring Boot.
     * 
     * @param args Argumentos da linha de comando
     */
	public static void main(String[] args) {
		SpringApplication.run(AppApplication.class, args);
	}
}
