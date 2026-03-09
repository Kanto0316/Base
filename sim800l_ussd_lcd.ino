#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <SoftwareSerial.h>

// ======== Cablage (UNO) ========
// LCD 16x2 I2C:
// SDA -> A4, SCL -> A5
// Adresse I2C LCD la plus courante: 0x27 (parfois 0x3F)
// SIM800L:
// SIM800L TX -> D2 (RX logiciel)
// SIM800L RX -> D3 (TX logiciel) via diviseur de tension (5V -> ~2.8V)

LiquidCrystal_I2C lcd(0x27, 16, 2);
SoftwareSerial sim800(2, 3);  // RX, TX

const unsigned long MODEM_TIMEOUT_MS = 12000;
const unsigned long SIM_READY_TIMEOUT_MS = 45000;

void lcdPrint2Lines(const String &l1, const String &l2) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(l1.substring(0, 16));
  lcd.setCursor(0, 1);
  lcd.print(l2.substring(0, 16));
}

bool waitForToken(const String &token, unsigned long timeoutMs, String &fullResponse) {
  unsigned long start = millis();
  fullResponse = "";

  while (millis() - start < timeoutMs) {
    while (sim800.available()) {
      char c = sim800.read();
      fullResponse += c;
      if (fullResponse.indexOf(token) >= 0) {
        return true;
      }
    }
  }
  return false;
}

bool sendAT(const String &cmd, const String &expected, unsigned long timeoutMs, String &response) {
  while (sim800.available()) sim800.read();
  sim800.println(cmd);
  return waitForToken(expected, timeoutMs, response);
}

bool waitForSimReady(unsigned long timeoutMs) {
  unsigned long start = millis();
  String response;

  while (millis() - start < timeoutMs) {
    bool simReady = sendAT("AT+CPIN?", "+CPIN:", MODEM_TIMEOUT_MS, response) &&
                    response.indexOf("READY") >= 0;

    if (simReady) {
      return true;
    }

    lcdPrint2Lines("Attente SIM", "SIM non prete");
    delay(1500);
  }

  return false;
}

bool initModem() {
  String response;
  for (uint8_t i = 0; i < 4; i++) {
    if (sendAT("AT", "OK", MODEM_TIMEOUT_MS, response)) {
      break;
    }
    delay(500);
    if (i == 3) return false;
  }

  if (!sendAT("ATE0", "OK", MODEM_TIMEOUT_MS, response)) return false;
  // Assure que la SIM est vraiment prete
  if (!waitForSimReady(SIM_READY_TIMEOUT_MS)) return false;

  return true;
}

void setup() {
  lcd.init();
  lcd.backlight();
  lcdPrint2Lines("Demarrage...", "SIM800L + I2C");

  Serial.begin(9600);
  sim800.begin(9600);
  delay(1200);

  lcdPrint2Lines("Init modem...", "patientez");

  if (!initModem()) {
    lcdPrint2Lines("Etat SIM:", "NON PRETE");
    Serial.println("SIM NOT READY");
    return;
  }

  lcdPrint2Lines("Etat SIM:", "PRETE");
  Serial.println("SIM READY");
}

void loop() {
  // Pas d'action continue: affichage simple de l'etat SIM.
}
