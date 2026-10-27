#include <IRremoteESP8266.h>
#include <IRsend.h>

// Mesmo pino do sketch da casa: D1 (GPIO5).
// D1 -> dois resistores de 330 ohm em paralelo -> perna longa do LED -> perna curta -> GND.
// Nao liga o LED em VIN nem em 5 V.

const uint16_t PIN_IR_SEND = 5;
const uint16_t CARRIER_HZ = 38000;
const unsigned long PAUSE_MS = 2000;

IRsend irsend(PIN_IR_SEND);

// Varios pulsos de 20 ms. Um pulso sozinho e curto demais para a camera.
uint16_t burst[41];

void buildBurst() {
  for (uint8_t i = 0; i < 40; i += 2) {
    burst[i] = 20000;
    burst[i + 1] = 500;
  }
  burst[40] = 20000;
}

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println("Teste do LED de infravermelho");
  Serial.println("D1 -> 2x 330 ohm em paralelo -> perna longa -> perna curta -> GND");
  Serial.println("Aponte a camera para o LED. O flash sai a cada 2 segundos.");
  buildBurst();
  irsend.begin();
}

void loop() {
  Serial.println("Ligado por 1 segundo. Olhe na camera agora.");
  pinMode(PIN_IR_SEND, OUTPUT);
  digitalWrite(PIN_IR_SEND, HIGH);
  delay(1000);
  digitalWrite(PIN_IR_SEND, LOW);
  delay(PAUSE_MS);

  Serial.println("Flash modulado, o mesmo tipo de sinal do ar.");
  irsend.sendRaw(burst, sizeof(burst) / sizeof(burst[0]), CARRIER_HZ);
  delay(PAUSE_MS);
}
