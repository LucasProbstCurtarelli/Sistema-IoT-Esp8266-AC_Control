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
#include <SPI.h>
#include <MFRC522.h>
#include <LittleFS.h>
#include <cstring>
#include <cstdlib>

// IR send stays on GPIO 5 (D1). IR recv is GPIO 4 (D2).
// MFRC522 SPI: CLK 14 (D5), MISO 12 (D6), MOSI 13 (D7), SS 2 (D4), RST 16 (D0).
// LDR is on A0 behind a divider that keeps the pin at about 1 V max.
// Status is Serial at 115200. There is no RGB status LED.

const uint16_t PIN_IR_SEND = 5;
const uint16_t PIN_IR_RECV = 4;
const uint8_t PIN_RFID_SS = 2;
const uint8_t PIN_RFID_RST = 16;
const int PIN_BUTTON_RESET = 0;

const char* TOPIC_CMD = "/iot/sensores/ac/comando";
const char* TOPIC_STATE = "/iot/sensores/ac/estado";
const char* TOPIC_PRESENCE = "/iot/entrada/presenca";
const char* MQTT_CONFIG_PATH = "/mqtt.json";

const int MQTT_PAYLOAD_MAX_SIZE = 256;
const unsigned long MQTT_RECONNECT_DELAY_MS = 2000;
const unsigned long PRESENCE_SCAN_MS = 250;
const unsigned long PRESENCE_HEARTBEAT_MS = 2000;

const int TEMP_MIN = 17;
const int TEMP_MAX = 30;
const int TEMP_DEFAULT = 22;

const unsigned long IR_PROCESSING_DELAY_MS = 600;
const unsigned long BUTTON_HOLD_TIME_MS = 3000;
const unsigned long BUTTON_DEBOUNCE_MS = 50;

const char* WIFI_AP_NAME = "ESP8266 Lucas Curtarelli";
const int WIFI_CONFIG_PORTAL_TIMEOUT_SEC = 180;

char mqttHost[64] = "";
char mqttPortText[8] = "1883";
char mqttUser[40] = "";
char mqttPass[64] = "";
bool shouldSaveMqtt = false;

IRCoolixAC ac(PIN_IR_SEND);
IRrecv irrecv(PIN_IR_RECV);
decode_results results;
WiFiClient espClient;
PubSubClient client(espClient);
MFRC522 reader(PIN_RFID_SS, PIN_RFID_RST);

bool irWaitActive = false;
unsigned long irWaitStartedMs = 0;
unsigned long nextReconnectMs = 0;

bool hasFact = false;
bool factPresent = false;
bool factDirty = false;
char factUid[21] = "";
unsigned long lastPublishMs = 0;
unsigned long lastScanMs = 0;

void mqttCallback(char* topic, byte* payload, unsigned int length);
void processarComando(String jsonPayload);
void enviarSinalAC(bool power, int temp);
void serviceIrWait();
void publicarStatusOnline();
void reconnectMQTT();
void configModeCallback(WiFiManager* myWiFiManager);
void onWifiConfigSaved();
void handleResetButton();
void servicePresence();
int clampTemperature(int temp);
bool isValidPowerValue(String power);
String generateMQTTClientId();
void loadMqttConfig();
void saveMqttConfig();
void copyParam(char* dest, size_t destLen, const char* value);
int mqttPortNumber();

void configModeCallback(WiFiManager* myWiFiManager) {
  Serial.println("Entrou no modo AP");
  Serial.print("IP do AP: ");
  Serial.println(WiFi.softAPIP());
}

void onWifiConfigSaved() {
  shouldSaveMqtt = true;
}

void copyParam(char* dest, size_t destLen, const char* value) {
  if (value == nullptr) {
    dest[0] = '\0';
    return;
  }
  strncpy(dest, value, destLen - 1);
  dest[destLen - 1] = '\0';
}

int mqttPortNumber() {
  if (mqttPortText[0] == '\0') {
    return 1883;
  }
  char* end = nullptr;
  long parsed = strtol(mqttPortText, &end, 10);
  if (end == mqttPortText || parsed <= 0 || parsed > 65535) {
    return 1883;
  }
  return static_cast<int>(parsed);
}

void loadMqttConfig() {
  mqttHost[0] = '\0';
  strncpy(mqttPortText, "1883", sizeof(mqttPortText));
  mqttPortText[sizeof(mqttPortText) - 1] = '\0';
  mqttUser[0] = '\0';
  mqttPass[0] = '\0';

  if (!LittleFS.begin()) {
    Serial.println("LittleFS indisponivel. Grave o broker no portal.");
    return;
  }
  if (!LittleFS.exists(MQTT_CONFIG_PATH)) {
    return;
  }
  File file = LittleFS.open(MQTT_CONFIG_PATH, "r");
  if (!file) {
    return;
  }
  StaticJsonDocument<384> doc;
  DeserializationError error = deserializeJson(doc, file);
  file.close();
  if (error) {
    Serial.println("MQTT: configuracao na flash ilegivel. Use o portal.");
    return;
  }
  copyParam(mqttHost, sizeof(mqttHost), doc["host"] | "");
  copyParam(mqttPortText, sizeof(mqttPortText), doc["port"] | "1883");
  copyParam(mqttUser, sizeof(mqttUser), doc["user"] | "");
  copyParam(mqttPass, sizeof(mqttPass), doc["pass"] | "");
}

void saveMqttConfig() {
  if (!LittleFS.begin()) {
    Serial.println("MQTT: nao foi possivel gravar a configuracao na flash");
    return;
  }
  StaticJsonDocument<384> doc;
  doc["host"] = mqttHost;
  doc["port"] = mqttPortText;
  doc["user"] = mqttUser;
  doc["pass"] = mqttPass;
  File file = LittleFS.open(MQTT_CONFIG_PATH, "w");
  if (!file) {
    Serial.println("MQTT: nao foi possivel gravar a configuracao na flash");
    return;
  }
  serializeJson(doc, file);
  file.close();
  Serial.println("MQTT: configuracao gravada na flash");
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
  Serial.println("------------------------------------------------");
  Serial.print("Mensagem recebida no topico: ");
  Serial.println(topic);

  if (length > MQTT_PAYLOAD_MAX_SIZE) {
    Serial.println("ERRO: payload maior que o limite");
    Serial.println("------------------------------------------------");
    return;
  }

  String msg = "";
  msg.reserve(length + 1);
  for (unsigned int i = 0; i < length; i++) {
    msg += static_cast<char>(payload[i]);
  }

  if (String(topic) == TOPIC_CMD) {
    processarComando(msg);
  } else {
    Serial.print("Topico ignorado. Esperado: ");
    Serial.println(TOPIC_CMD);
  }
  Serial.println("------------------------------------------------");
}

void processarComando(String jsonPayload) {
  StaticJsonDocument<200> doc;
  DeserializationError error = deserializeJson(doc, jsonPayload);
  if (error) {
    Serial.print("ERRO ao processar JSON: ");
    Serial.println(error.c_str());
    return;
  }

  String powerStr = doc["power"] | "";
  if (!isValidPowerValue(powerStr)) {
    Serial.print("ERRO: valor de power invalido: ");
    Serial.println(powerStr);
    return;
  }
  bool power = (powerStr == "on");
  int temp = doc["temp"] | TEMP_DEFAULT;
  temp = clampTemperature(temp);

  Serial.print("Comando: power=");
  Serial.print(power ? "on" : "off");
  Serial.print(", temp=");
  Serial.println(temp);
  enviarSinalAC(power, temp);
}

void enviarSinalAC(bool power, int temp) {
  ac.setPower(power);
  if (power) {
    ac.setMode(kCoolixCool);
    ac.setTemp(temp);
  }
  ac.send();
  irrecv.enableIRIn();
  irWaitActive = true;
  irWaitStartedMs = millis();
  Serial.println("IR: aguardando loopback");
}

void serviceIrWait() {
  if (!irWaitActive) {
    return;
  }
  if (millis() - irWaitStartedMs < IR_PROCESSING_DELAY_MS) {
    return;
  }
  irWaitActive = false;

  bool loopbackDetected = false;
  if (irrecv.decode(&results)) {
    loopbackDetected = true;
    irrecv.resume();
  }

  String statusMessage = "{\"status\":\"ok\", \"loopback\":\"";
  statusMessage += loopbackDetected ? "true" : "false";
  statusMessage += "\"}";

  if (client.publish(TOPIC_STATE, statusMessage.c_str())) {
    Serial.println("Status publicado com sucesso");
  } else {
    Serial.println("ERRO: falha ao publicar status");
  }
  Serial.println("IR: janela de 600 ms encerrada, leitura da chave segue");
}

void publicarStatusOnline() {
  if (!client.connected()) {
    Serial.println("ERRO: cliente MQTT nao conectado para publicar status");
    return;
  }
  const char* onlineMessage = "{\"status\":\"online\", \"device\":\"ESP8266-AC\", \"ready\":true}";
  if (client.publish(TOPIC_STATE, onlineMessage)) {
    Serial.println("Status online publicado com sucesso");
  } else {
    Serial.println("ERRO: falha ao publicar status online");
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
  unsigned long now = millis();
  if (now < nextReconnectMs) {
    return;
  }
  if (mqttHost[0] == '\0') {
    Serial.println("MQTT: host vazio. Grave o broker no portal WiFiManager.");
    nextReconnectMs = now + MQTT_RECONNECT_DELAY_MS;
    return;
  }

  String clientId = generateMQTTClientId();
  Serial.print("Tentando conectar ao MQTT em ");
  Serial.print(mqttHost);
  Serial.print(":");
  Serial.println(mqttPortNumber());

  bool connected = false;
  if (mqttUser[0] != '\0') {
    connected = client.connect(clientId.c_str(), mqttUser, mqttPass);
  } else {
    connected = client.connect(clientId.c_str());
  }

  if (connected) {
    Serial.println("Conectado ao MQTT broker");
    if (client.subscribe(TOPIC_CMD)) {
      Serial.print("Inscrito no topico: ");
      Serial.println(TOPIC_CMD);
    } else {
      Serial.println("ERRO: falha ao se inscrever no topico");
    }
    publicarStatusOnline();
    return;
  }

  Serial.print("ERRO: falha ao conectar ao MQTT. Codigo: ");
  Serial.println(client.state());
  nextReconnectMs = now + MQTT_RECONNECT_DELAY_MS;
}

void handleResetButton() {
  if (digitalRead(PIN_BUTTON_RESET) != LOW) {
    return;
  }

  unsigned long startTime = millis();
  Serial.println("Botao de reset pressionado");
  while (digitalRead(PIN_BUTTON_RESET) == LOW) {
    if (millis() - startTime > BUTTON_HOLD_TIME_MS) {
      Serial.println("Reset de configuracao WiFi iniciado...");
      WiFiManager wm;
      wm.resetSettings();
      Serial.println("Reiniciando ESP8266...");
      ESP.restart();
      return;
    }
    client.loop();
    delay(BUTTON_DEBOUNCE_MS);
    ESP.wdtFeed();
  }
}

int clampTemperature(int temp) {
  if (temp < TEMP_MIN) {
    return TEMP_MIN;
  }
  if (temp > TEMP_MAX) {
    return TEMP_MAX;
  }
  return temp;
}

bool isValidPowerValue(String power) {
  return power == "on" || power == "off";
}

void uidToHex(char* out, size_t outLen) {
  size_t pos = 0;
  if (outLen == 0) {
    return;
  }
  for (byte i = 0; i < reader.uid.size && pos + 2 < outLen; i++) {
    sprintf(out + pos, "%02X", reader.uid.uidByte[i]);
    pos += 2;
  }
  out[pos] = '\0';
}

void notePresent(const char* uid) {
  if (!hasFact || !factPresent || strcmp(factUid, uid) != 0) {
    factDirty = true;
    Serial.print("RFID: ");
    Serial.println(uid);
  }
  hasFact = true;
  factPresent = true;
  strncpy(factUid, uid, sizeof(factUid) - 1);
  factUid[sizeof(factUid) - 1] = '\0';
}

void noteAbsent() {
  if (!hasFact || factPresent) {
    factDirty = true;
    Serial.println("RFID: suporte vazio");
  }
  hasFact = true;
  factPresent = false;
  factUid[0] = '\0';
}

void publishPresenceIfDue(unsigned long now) {
  if (!hasFact || !client.connected()) {
    return;
  }
  bool heartbeat = lastPublishMs == 0 || (now - lastPublishMs) >= PRESENCE_HEARTBEAT_MS;
  if (!factDirty && !heartbeat) {
    return;
  }

  StaticJsonDocument<128> doc;
  doc["present"] = factPresent;
  if (factPresent) {
    doc["uid"] = factUid;
  }
  int lux = analogRead(A0);
  if (lux >= 0 && lux <= 1023) {
    doc["lux"] = lux;
  }
  char payload[128];
  size_t written = serializeJson(doc, payload, sizeof(payload));
  if (written == 0 || written >= sizeof(payload)) {
    return;
  }
  if (!client.publish(TOPIC_PRESENCE, payload)) {
    Serial.println("ERRO: falha ao publicar presenca");
    return;
  }
  factDirty = false;
  lastPublishMs = now;
  Serial.print("Presenca: ");
  Serial.println(payload);
}

void scanReader() {
  byte atqa[2];
  byte atqaLen = sizeof(atqa);
  MFRC522::StatusCode status = reader.PICC_RequestA(atqa, &atqaLen);
  if (status == MFRC522::STATUS_TIMEOUT) {
    noteAbsent();
    return;
  }
  if (status != MFRC522::STATUS_OK && status != MFRC522::STATUS_COLLISION) {
    return;
  }
  if (!reader.PICC_ReadCardSerial()) {
    return;
  }
  char uid[21];
  uidToHex(uid, sizeof(uid));
  reader.PICC_HaltA();
  reader.PCD_StopCrypto1();
  if (uid[0] == '\0') {
    return;
  }
  notePresent(uid);
}

void servicePresence() {
  unsigned long now = millis();
  if (now - lastScanMs >= PRESENCE_SCAN_MS) {
    lastScanMs = now;
    scanReader();
  }
  publishPresenceIfDue(now);
}

void setup() {
  Serial.begin(115200);
  Serial.println("\n\n=== Inicializando ESP8266 entrada + ar ===");
  Serial.println("Status apenas no Monitor Serial a 115200. Sem LED RGB.");
  Serial.println("LDR em A0 atras do divisor, maximo cerca de 1 V.");

  pinMode(PIN_BUTTON_RESET, INPUT_PULLUP);
  randomSeed(micros());

  ac.begin();
  irrecv.enableIRIn();
  Serial.println("Componentes IR inicializados");

  SPI.begin();
  reader.PCD_Init();
  byte version = reader.PCD_ReadRegister(MFRC522::VersionReg);
  Serial.print("MFRC522 version: 0x");
  Serial.println(version, HEX);

  loadMqttConfig();

  WiFiManager wm;
  wm.setAPCallback(configModeCallback);
  wm.setSaveConfigCallback(onWifiConfigSaved);
  wm.setConfigPortalTimeout(WIFI_CONFIG_PORTAL_TIMEOUT_SEC);

  WiFiManagerParameter paramHost("mqtt_host", "MQTT host", mqttHost, sizeof(mqttHost) - 1);
  WiFiManagerParameter paramPort("mqtt_port", "MQTT port", mqttPortText, sizeof(mqttPortText) - 1);
  WiFiManagerParameter paramUser("mqtt_user", "MQTT user", mqttUser, sizeof(mqttUser) - 1);
  WiFiManagerParameter paramPass("mqtt_pass", "MQTT senha", mqttPass, sizeof(mqttPass) - 1);
  wm.addParameter(&paramHost);
  wm.addParameter(&paramPort);
  wm.addParameter(&paramUser);
  wm.addParameter(&paramPass);

  Serial.print("Conectando ao WiFi (AP: ");
  Serial.print(WIFI_AP_NAME);
  Serial.println(")...");

  if (!wm.autoConnect(WIFI_AP_NAME)) {
    Serial.println("ERRO: falha na conexao WiFi. Reiniciando...");
    delay(1000);
    ESP.restart();
  }

  copyParam(mqttHost, sizeof(mqttHost), paramHost.getValue());
  copyParam(mqttPortText, sizeof(mqttPortText), paramPort.getValue());
  copyParam(mqttUser, sizeof(mqttUser), paramUser.getValue());
  copyParam(mqttPass, sizeof(mqttPass), paramPass.getValue());
  if (mqttPortText[0] == '\0') {
    strncpy(mqttPortText, "1883", sizeof(mqttPortText));
    mqttPortText[sizeof(mqttPortText) - 1] = '\0';
  }
  saveMqttConfig();

  Serial.print("WiFi conectado! IP: ");
  Serial.println(WiFi.localIP());

  if (mqttHost[0] == '\0') {
    Serial.println("MQTT: host vazio. Grave o broker no portal WiFiManager.");
  } else {
    client.setServer(mqttHost, mqttPortNumber());
    client.setCallback(mqttCallback);
    Serial.print("MQTT configurado: ");
    Serial.print(mqttHost);
    Serial.print(":");
    Serial.print(mqttPortNumber());
    Serial.print(", usuario ");
    Serial.println(mqttUser[0] == '\0' ? "ausente" : "definido");
  }

  Serial.println("=== Inicializacao completa ===\n");
}

void loop() {
  handleResetButton();
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();
  serviceIrWait();
  servicePresence();
  ESP.wdtFeed();
}
