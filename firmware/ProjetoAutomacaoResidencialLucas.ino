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

// ==========================================
// CONSTANTS & CONFIGURATION
// ==========================================

// Hardware Pin Definitions
const uint16_t PIN_IR_SEND = 5;      // D1 (GPIO 5)
const uint16_t PIN_IR_RECV = 12;     // D6 (GPIO 12)
const uint16_t LED_R_PIN = 4;        // D2 (GPIO 4)
const uint16_t LED_G_PIN = 14;       // D5 (GPIO 14)
const uint16_t LED_B_PIN = 13;        // D7 (GPIO 13)
const int PIN_BUTTON_RESET = 0;       // Botão FLASH (GPIO 0)

// MQTT Configuration
const char* MQTT_SERVER = "192.168.1.2";
const int MQTT_PORT = 1883;
const char* TOPIC_CMD = "/iot/sensores/ac/comando";
const char* TOPIC_STATE = "/iot/sensores/ac/estado";
const int MQTT_PAYLOAD_MAX_SIZE = 256;
const unsigned long MQTT_RECONNECT_DELAY_MS = 2000;

// Temperature Limits
const int TEMP_MIN = 17;
const int TEMP_MAX = 30;
const int TEMP_DEFAULT = 22;

// Timing Constants
const unsigned long IR_PROCESSING_DELAY_MS = 600;
const unsigned long BUTTON_HOLD_TIME_MS = 3000;
const unsigned long BUTTON_DEBOUNCE_MS = 50;
const float LED_BLINK_INTERVAL_SEC = 0.3f;

// WiFi Manager Configuration
const char* WIFI_AP_NAME = "ESP8266 Lucas Curtarelli";
const int WIFI_CONFIG_PORTAL_TIMEOUT_SEC = 180;

// LED States
enum EstadoLED {
  DESLIGADO,
  PISCANDO_AZUL,      // Conectando WiFi
  PISCANDO_VERDE,     // Processando
  PISCANDO_VERMELHO,  // Resetando
  SOLIDO_VERDE,       // Conectado e Ocioso
  SOLIDO_BRANCO       // Modo AP
};

// ==========================================
// GLOBAL OBJECTS
// ==========================================

IRCoolixAC ac(PIN_IR_SEND);
IRrecv irrecv(PIN_IR_RECV);
decode_results results;
WiFiClient espClient;
PubSubClient client(espClient);
Ticker blinker;

// ==========================================
// GLOBAL STATE VARIABLES
// ==========================================

volatile int estadoAtualLED = DESLIGADO;
volatile bool ledState = false;

// ==========================================
// FUNCTION PROTOTYPES
// ==========================================

// LED Control
void setLedState(int estado);
void tickLED();
void atualizarCorHardware(bool ligado, int corBase);

// MQTT & Command Processing
void mqttCallback(char* topic, byte* payload, unsigned int length);
void processarComando(String jsonPayload);
void enviarSinalAC(bool power, int temp);
void publicarStatusOnline();
void reconnectMQTT();

// WiFi Manager
void configModeCallback(WiFiManager* myWiFiManager);

// Button Handling
void handleResetButton();

// Utility
int clampTemperature(int temp);
bool isValidPowerValue(String power);
String generateMQTTClientId();

// ==========================================
// LED CONTROL IMPLEMENTATION
// ==========================================

void tickLED() {
  ledState = !ledState;

  // Force always on for solid states
  if (estadoAtualLED == SOLIDO_VERDE || estadoAtualLED == SOLIDO_BRANCO) {
    ledState = true;
  }

  atualizarCorHardware(ledState, estadoAtualLED);
}

void atualizarCorHardware(bool ligado, int modo) {
  // Turn off all LEDs first
  digitalWrite(LED_R_PIN, LOW);
  digitalWrite(LED_G_PIN, LOW);
  digitalWrite(LED_B_PIN, LOW);

  if (!ligado) return;

  switch (modo) {
    case PISCANDO_AZUL:
      digitalWrite(LED_B_PIN, HIGH);
      break;
    case PISCANDO_VERDE:
      digitalWrite(LED_G_PIN, HIGH);
      break;
    case SOLIDO_VERDE:
      digitalWrite(LED_G_PIN, HIGH);
      break;
    case PISCANDO_VERMELHO:
      digitalWrite(LED_R_PIN, HIGH);
      break;
    case SOLIDO_BRANCO:
      digitalWrite(LED_R_PIN, HIGH);
      digitalWrite(LED_G_PIN, HIGH);
      digitalWrite(LED_B_PIN, HIGH);
      break;
    case DESLIGADO:
    default:
      // Already turned off above
      break;
  }
}

void setLedState(int estado) {
  estadoAtualLED = estado;
  blinker.detach();

  switch (estado) {
    case PISCANDO_AZUL:
    case PISCANDO_VERDE:
    case PISCANDO_VERMELHO:
      blinker.attach(LED_BLINK_INTERVAL_SEC, tickLED);
      break;
    case SOLIDO_VERDE:
    case SOLIDO_BRANCO:
      tickLED();
      break;
    case DESLIGADO:
      atualizarCorHardware(false, DESLIGADO);
      break;
  }
}

// ==========================================
// WIFI MANAGER CALLBACKS
// ==========================================

void configModeCallback(WiFiManager* myWiFiManager) {
  Serial.println("Entrou no modo AP");
  Serial.print("IP do AP: ");
  Serial.println(WiFi.softAPIP());
  setLedState(SOLIDO_BRANCO);
}

// ==========================================
// MQTT & COMMAND PROCESSING
// ==========================================

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  Serial.println("------------------------------------------------");
  Serial.print("Mensagem recebida no tópico: ");
  Serial.println(topic);

  // Validate payload size
  if (length > MQTT_PAYLOAD_MAX_SIZE) {
    Serial.print("ERRO: Payload muito grande (");
    Serial.print(length);
    Serial.print(" bytes, máximo: ");
    Serial.print(MQTT_PAYLOAD_MAX_SIZE);
    Serial.println(" bytes)");
    Serial.println("------------------------------------------------");
    return;
  }

  // Convert payload to String
  String msg = "";
  msg.reserve(length + 1);
  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }

  Serial.print("Conteúdo: [");
  Serial.print(msg);
  Serial.println("]");

  // Verify topic matches command topic
  if (String(topic) == TOPIC_CMD) {
    Serial.println("Tópico válido. Processando comando...");
    processarComando(msg);
  } else {
    Serial.print("Tópico ignorado. Esperado: ");
    Serial.println(TOPIC_CMD);
  }
  Serial.println("------------------------------------------------");
}

void processarComando(String jsonPayload) {
  setLedState(PISCANDO_VERDE);

  StaticJsonDocument<200> doc;
  DeserializationError error = deserializeJson(doc, jsonPayload);

  if (error) {
    Serial.print("ERRO ao processar JSON: ");
    Serial.println(error.c_str());
    setLedState(SOLIDO_VERDE);
    return;
  }

  // Validate and extract power value
  String powerStr = doc["power"] | "";
  if (!isValidPowerValue(powerStr)) {
    Serial.print("ERRO: Valor de power inválido: ");
    Serial.println(powerStr);
    setLedState(SOLIDO_VERDE);
    return;
  }
  bool power = (powerStr == "on");

  // Validate and clamp temperature
  int temp = doc["temp"] | TEMP_DEFAULT;
  temp = clampTemperature(temp);

  Serial.print("Comando: power=");
  Serial.print(power ? "on" : "off");
  Serial.print(", temp=");
  Serial.println(temp);

  enviarSinalAC(power, temp);
}

void enviarSinalAC(bool power, int temp) {
  // Configure IR AC
  ac.setPower(power);
  if (power) {
    ac.setMode(kCoolixCool);
    ac.setTemp(temp);
  }
  ac.send();

  // Wait for IR processing (non-blocking alternative would require state machine)
  delay(IR_PROCESSING_DELAY_MS);

  // Check loopback (verify IR transmission)
  bool loopbackDetected = false;
  if (irrecv.decode(&results)) {
    loopbackDetected = true;
    irrecv.resume();
  }

  // Publish status response
  String statusMessage = "{\"status\":\"ok\", \"loopback\":\"";
  statusMessage += loopbackDetected ? "true" : "false";
  statusMessage += "\"}";

  if (client.publish(TOPIC_STATE, statusMessage.c_str())) {
    Serial.println("Status publicado com sucesso");
  } else {
    Serial.println("ERRO: Falha ao publicar status");
  }

  setLedState(SOLIDO_VERDE);
}

void publicarStatusOnline() {
  if (!client.connected()) {
    Serial.println("ERRO: Cliente MQTT não conectado para publicar status");
    return;
  }

  String onlineMessage = "{\"status\":\"online\", \"device\":\"ESP8266-AC\", \"ready\":true}";
  
  if (client.publish(TOPIC_STATE, onlineMessage.c_str())) {
    Serial.println("Status online publicado com sucesso");
  } else {
    Serial.println("ERRO: Falha ao publicar status online");
  }
}

String generateMQTTClientId() {
  String clientId = "ESP8266-AC-";
  clientId += String(random(0xffff), HEX);
  return clientId;
}

void reconnectMQTT() {
  if (client.connected()) {
    return;
  }

  setLedState(PISCANDO_AZUL);

  String clientId = generateMQTTClientId();
  Serial.print("Tentando conectar ao MQTT como: ");
  Serial.println(clientId);

  if (client.connect(clientId.c_str())) {
    Serial.println("Conectado ao MQTT broker");
    
    if (client.subscribe(TOPIC_CMD)) {
      Serial.print("Inscrito no tópico: ");
      Serial.println(TOPIC_CMD);
    } else {
      Serial.println("ERRO: Falha ao se inscrever no tópico");
    }

    setLedState(SOLIDO_VERDE);
    publicarStatusOnline();
  } else {
    Serial.print("ERRO: Falha ao conectar ao MQTT. Código: ");
    Serial.println(client.state());
    delay(MQTT_RECONNECT_DELAY_MS);
  }
}

// ==========================================
// BUTTON HANDLING
// ==========================================

void handleResetButton() {
  if (digitalRead(PIN_BUTTON_RESET) != LOW) {
    return;
  }

  unsigned long startTime = millis();
  setLedState(PISCANDO_VERMELHO);

  // Wait while button is held
  while (digitalRead(PIN_BUTTON_RESET) == LOW) {
    if (millis() - startTime > BUTTON_HOLD_TIME_MS) {
      Serial.println("Reset de configuração WiFi iniciado...");
      WiFiManager wm;
      wm.resetSettings();
      Serial.println("Reiniciando ESP8266...");
      ESP.restart();
      return;
    }
    delay(BUTTON_DEBOUNCE_MS);
    ESP.wdtFeed(); // Feed watchdog during long loop
  }

  // Button released before timeout - restore normal state
  if (WiFi.status() == WL_CONNECTED) {
    setLedState(SOLIDO_VERDE);
  } else {
    setLedState(PISCANDO_AZUL);
  }
}

// ==========================================
// UTILITY FUNCTIONS
// ==========================================

int clampTemperature(int temp) {
  if (temp < TEMP_MIN) {
    Serial.print("Temperatura ajustada de ");
    Serial.print(temp);
    Serial.print(" para ");
    Serial.println(TEMP_MIN);
    return TEMP_MIN;
  }
  if (temp > TEMP_MAX) {
    Serial.print("Temperatura ajustada de ");
    Serial.print(temp);
    Serial.print(" para ");
    Serial.println(TEMP_MAX);
    return TEMP_MAX;
  }
  return temp;
}

bool isValidPowerValue(String power) {
  return (power == "on" || power == "off");
}

// ==========================================
// SETUP
// ==========================================

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n=== Inicializando ESP8266 AC Controller ===");

  // Configure pins
  pinMode(LED_R_PIN, OUTPUT);
  pinMode(LED_G_PIN, OUTPUT);
  pinMode(LED_B_PIN, OUTPUT);
  pinMode(PIN_BUTTON_RESET, INPUT_PULLUP);

  setLedState(PISCANDO_AZUL);

  // Initialize IR components
  ac.begin();
  irrecv.enableIRIn();
  Serial.println("Componentes IR inicializados");

  // Configure WiFi Manager
  WiFiManager wm;
  wm.setAPCallback(configModeCallback);
  wm.setConfigPortalTimeout(WIFI_CONFIG_PORTAL_TIMEOUT_SEC);

  Serial.print("Conectando ao WiFi (AP: ");
  Serial.print(WIFI_AP_NAME);
  Serial.println(")...");

  if (!wm.autoConnect(WIFI_AP_NAME)) {
    Serial.println("ERRO: Falha na conexão WiFi. Reiniciando...");
    delay(1000);
    ESP.restart();
  }

  Serial.print("WiFi conectado! IP: ");
  Serial.println(WiFi.localIP());

  // Configure MQTT
  client.setServer(MQTT_SERVER, MQTT_PORT);
  client.setCallback(mqttCallback);

  Serial.print("MQTT configurado: ");
  Serial.print(MQTT_SERVER);
  Serial.print(":");
  Serial.println(MQTT_PORT);

  setLedState(SOLIDO_VERDE);
  Serial.println("=== Inicialização completa ===\n");
}

// ==========================================
// MAIN LOOP
// ==========================================

void loop() {
  // Handle reset button
  handleResetButton();

  // Maintain MQTT connection
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();

  ESP.wdtFeed(); // Feed watchdog
}
