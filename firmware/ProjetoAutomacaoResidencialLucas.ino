#include <FS.h>
#include <ESP8266WiFi.h>
#include <DNSServer.h>
#include <ESP8266WebServer.h>
#include <WiFiManager.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <IRremoteESP8266.h>
#include <IRsend.h>
#include <IRrecv.h>
#include <IRutils.h>
#include <ir_Coolix.h>
#include <Ticker.h>

// --- HARDWARE ---
const uint16_t PIN_IR_SEND = 5;   // D1 (GPIO 5) [cite: 1788]
const uint16_t PIN_IR_RECV = 12;  // D6 (GPIO 12) [cite: 1451]

// LEDs RGB (Cátodo Comum: HIGH acende)
const uint16_t LED_R_PIN = 4;   // D2 (GPIO 4) [cite: 1787]
const uint16_t LED_G_PIN = 14;  // D5 (GPIO 14) [cite: 1452]
const uint16_t LED_B_PIN = 13;  // D7 (GPIO 13) [cite: 1453]

const int PIN_BUTTON_RESET = 0;  // Botão FLASH (GPIO 0) [cite: 410]

// --- ESTADOS DO LED ---
enum EstadoLED {
  DESLIGADO,
  PISCANDO_AZUL,      // Conectando WiFi
  PISCANDO_VERDE,     // Processando
  PISCANDO_VERMELHO,  // Resetando
  SOLIDO_VERDE,       // Conectado e Ocioso
  SOLIDO_BRANCO       // Modo AP
};

// --- OBJETOS ---
IRCoolixAC ac(PIN_IR_SEND);
IRrecv irrecv(PIN_IR_RECV);
decode_results results;
WiFiClient espClient;
PubSubClient client(espClient);
Ticker blinker;  // Objeto para controlar o pisca-pisca

// --- CONFIGURAÇÃO MQTT ---
const char* mqtt_server = "192.168.1.2";  // SEU IP DO BROKER/NODE-RED
const int mqtt_port = 1883;
const char* topic_cmd = "/iot/sensores/ac/comando";
const char* topic_state = "/iot/sensores/ac/estado";

// Variáveis Globais de Controle de LED
int estadoAtualLED = DESLIGADO;
bool ledState = false;  // Usado pelo Ticker para alternar on/off

// --- PROTÓTIPOS ---
void setLedState(int estado);
void tickLED();
void atualizarCorHardware(bool ligado, int corBase);
void processarComando(String payload);
void enviarSinalAC(bool power, int temp);

// ==========================================
// CONTROLE AVANÇADO DE LEDS
// ==========================================

// Função chamada automaticamente pelo Ticker a cada 300ms
void tickLED() {
  ledState = !ledState;  // Inverte estado

  // Se for estado SÓLIDO, força sempre ligado
  if (estadoAtualLED == SOLIDO_VERDE || estadoAtualLED == SOLIDO_BRANCO) {
    ledState = true;
  }

  atualizarCorHardware(ledState, estadoAtualLED);
}

// Atualiza os pinos físicos
void atualizarCorHardware(bool ligado, int modo) {
  // Primeiro apaga tudo
  digitalWrite(LED_R_PIN, LOW);
  digitalWrite(LED_G_PIN, LOW);
  digitalWrite(LED_B_PIN, LOW);

  if (!ligado) return;  // Se for o ciclo "apagado" do pisca, sai aqui

  switch (modo) {
    case PISCANDO_AZUL: digitalWrite(LED_B_PIN, HIGH); break;
    case PISCANDO_VERDE: digitalWrite(LED_G_PIN, HIGH); break;
    case SOLIDO_VERDE: digitalWrite(LED_G_PIN, HIGH); break;
    case PISCANDO_VERMELHO: digitalWrite(LED_R_PIN, HIGH); break;
    case SOLIDO_BRANCO:
      digitalWrite(LED_R_PIN, HIGH);
      digitalWrite(LED_G_PIN, HIGH);
      digitalWrite(LED_B_PIN, HIGH);
      break;
  }
}

// Função principal para mudar o comportamento
void setLedState(int estado) {
  estadoAtualLED = estado;

  // Remove qualquer timer anterior
  blinker.detach();

  // Configura o novo comportamento
  switch (estado) {
    case PISCANDO_AZUL:
    case PISCANDO_VERDE:
    case PISCANDO_VERMELHO:
      blinker.attach(0.3, tickLED);  // Pisca a cada 300ms
      break;
    case SOLIDO_VERDE:
    case SOLIDO_BRANCO:
      tickLED();  // Aplica imediatamente a cor sólida
      break;
    case DESLIGADO:
      atualizarCorHardware(false, DESLIGADO);
      break;
  }
}

// ==========================================
// CALLBACKS DO WIFIMANAGER
// ==========================================
// Chamado quando entra no modo AP (Configuração)
void configModeCallback(WiFiManager* myWiFiManager) {
  Serial.println("Entrou no modo AP");
  Serial.println(WiFi.softAPIP());
  // Pedido: Modo AP = BRANCO SÓLIDO
  setLedState(SOLIDO_BRANCO);
}

// ==========================================
// MQTT & LÓGICA
// ==========================================
void callback(char* topic, byte* payload, unsigned int length) {
  // 1. Avisa que algo chegou
  Serial.println("------------------------------------------------");
  Serial.print("!!! RECEBI UMA MENSAGEM NO TÓPICO: ");
  Serial.println(topic);

  // 2. Converte os bytes para String para podermos ler
  String msg = "";
  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }

  Serial.print("CONTEÚDO DA MENSAGEM: [");
  Serial.print(msg);
  Serial.println("]");

  // 3. Verifica se o tópico é exatamente o esperado
  // DICA: O String(topic) cria uma cópia segura para comparação
  if (String(topic) == topic_cmd) {
    Serial.println("✅ Tópico Confere! Enviando para processarComando...");
    processarComando(msg);
  } else {
    Serial.println("❌ Tópico Ignorado (Não é o de comando).");
    Serial.print("Eu esperava: ");
    Serial.println(topic_cmd);
  }
  Serial.println("------------------------------------------------");
}

void processarComando(String jsonPayload) {
  // Pedido: Processando = PISCAR VERDE
  setLedState(PISCANDO_VERDE);

  StaticJsonDocument<200> doc;
  DeserializationError error = deserializeJson(doc, jsonPayload);

  if (error) {
    Serial.println("Erro JSON");
    setLedState(SOLIDO_VERDE);  // Volta ao normal
    return;
  }

  bool power = (doc["power"] == "on");
  int temp = doc["temp"] | 22;

  enviarSinalAC(power, temp);
}

void enviarSinalAC(bool power, int temp) {
  // Envia IR
  ac.setPower(power);
  if (power) {
    ac.setMode(kCoolixCool);
    ac.setTemp(temp);
  }
  ac.send();

  // Simula tempo de processamento/loopback (para dar tempo de ver o pisca verde)
  delay(600);

  // Loopback check
  if (irrecv.decode(&results)) {
    client.publish(topic_state, "{\"status\":\"ok\", \"loopback\":\"true\"}");
    irrecv.resume();
  } else {
    client.publish(topic_state, "{\"status\":\"ok\", \"loopback\":\"false\"}");
  }

  // Pedido: Terminou processamento = VERDE SÓLIDO
  setLedState(SOLIDO_VERDE);
}

void reconnect() {
  if (!client.connected()) {
    // Se perdeu conexão MQTT, volta a piscar AZUL (tentando conectar)
    setLedState(PISCANDO_AZUL);

    String clientId = "ESP8266-AC-" + String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      client.subscribe(topic_cmd);
      // Conectou: VERDE SÓLIDO
      setLedState(SOLIDO_VERDE);
    } else {
      delay(2000);
    }
  }
}

// ==========================================
// SETUP
// ==========================================
void setup() {
  Serial.begin(115200);

  pinMode(LED_R_PIN, OUTPUT);
  pinMode(LED_G_PIN, OUTPUT);
  pinMode(LED_B_PIN, OUTPUT);
  pinMode(PIN_BUTTON_RESET, INPUT_PULLUP);

  // Pedido: Conectando no WiFi = PISCAR AZUL
  setLedState(PISCANDO_AZUL);

  ac.begin();
  irrecv.enableIRIn();

  WiFiManager wm;
  // Define callback para quando entrar no modo AP (Ficar Branco)
  wm.setAPCallback(configModeCallback);
  wm.setConfigPortalTimeout(180);

  if (!wm.autoConnect("ESP8266 Lucas Curtarelli")) {
    Serial.println("Falha na conexão. Reiniciando...");
    ESP.restart();
  }

  // Se passou daqui, está conectado no WiFi
  Serial.println("WiFi Conectado!");

  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);

  // Pedido: Conectado (ocioso) = VERDE SÓLIDO
  setLedState(SOLIDO_VERDE);
}

// ==========================================
// LOOP
// ==========================================
void loop() {
  // --- LÓGICA DO BOTÃO RESET ---
  // Se apertar o botão FLASH (GPIO 0)
  if (digitalRead(PIN_BUTTON_RESET) == LOW) {
    unsigned long startTime = millis();

    // Pedido: Enquanto segura, PISCA VERMELHO
    setLedState(PISCANDO_VERMELHO);

    // Loop de espera enquanto segura o botão
    while (digitalRead(PIN_BUTTON_RESET) == LOW) {
      // Se segurar por mais de 3 segundos
      if (millis() - startTime > 3000) {
        // Ação de reset
        WiFiManager wm;
        wm.resetSettings();
        ESP.restart();
      }
      delay(50);  // Debounce leve
    }

    // Se soltou antes de 3s, volta ao estado normal (Verde Sólido se tiver net)
    if (WiFi.status() == WL_CONNECTED) {
      setLedState(SOLIDO_VERDE);
    } else {
      setLedState(PISCANDO_AZUL);
    }
  }

  if (!client.connected()) {
    reconnect();
  }
  client.loop();
}