#include <SPI.h>
#include <MFRC522.h>

// Mesmos pinos do sketch da casa.
// SCK D5 (GPIO14), MISO D6 (GPIO12), MOSI D7 (GPIO13), SDA D4 (GPIO2), RST D0 (GPIO16).
// Alimentação: 3V3 e GND. O pino IRQ fica solto.

const uint8_t PIN_RFID_SS = 2;
const uint8_t PIN_RFID_RST = 16;
const unsigned long SCAN_MS = 300;

MFRC522 reader(PIN_RFID_SS, PIN_RFID_RST);
char lastUid[21] = "";

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println("Teste do MFRC522");
  Serial.println("SCK=D5  MISO=D6  MOSI=D7  SDA=D4  RST=D0  3V3  GND");

  SPI.begin();
  reader.PCD_Init();
  delay(50);
  reportChip();
}

void loop() {
  static unsigned long lastScanMs = 0;
  unsigned long now = millis();
  if (now - lastScanMs < SCAN_MS) {
    return;
  }
  lastScanMs = now;

  byte atqa[2];
  byte atqaLen = sizeof(atqa);
  MFRC522::StatusCode status = reader.PICC_RequestA(atqa, &atqaLen);
  if (status != MFRC522::STATUS_OK && status != MFRC522::STATUS_COLLISION) {
    lastUid[0] = '\0';
    return;
  }
  if (!reader.PICC_ReadCardSerial()) {
    return;
  }

  char uid[21];
  uidToHex(uid, sizeof(uid));
  reader.PICC_HaltA();
  reader.PCD_StopCrypto1();
  if (uid[0] == '\0' || strcmp(uid, lastUid) == 0) {
    return;
  }
  strncpy(lastUid, uid, sizeof(lastUid) - 1);
  lastUid[sizeof(lastUid) - 1] = '\0';
  Serial.print("Tag lida: ");
  Serial.println(uid);
}

void reportChip() {
  byte version = reader.PCD_ReadRegister(MFRC522::VersionReg);
  Serial.print("VersionReg: 0x");
  Serial.println(version, HEX);

  if (version == 0x91 || version == 0x92) {
    Serial.println("O chip responde. Encosta uma tag.");
    return;
  }
  if (version == 0x00 || version == 0xFF) {
    Serial.println("O chip nao responde. Confere 3V3, GND e os cinco fios de sinal.");
    return;
  }
  Serial.println("Resposta inesperada. Confere a solda de SCK, MISO, MOSI, SDA e RST.");
}

void uidToHex(char* dest, size_t destLen) {
  size_t used = 0;
  dest[0] = '\0';
  for (byte i = 0; i < reader.uid.size; i++) {
    if (used + 2 >= destLen) {
      break;
    }
    sprintf(dest + used, "%02X", reader.uid.uidByte[i]);
    used += 2;
  }
  dest[used] = '\0';
}
