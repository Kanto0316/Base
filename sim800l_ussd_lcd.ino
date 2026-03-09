#include <LiquidCrystal.h>
#include <SoftwareSerial.h>

// ======== Cablage (UNO) ========
// LCD 16x2 (mode 4 bits):
// RS=7, E=8, D4=9, D5=10, D6=11, D7=12
// SIM800L:
// SIM800L TX -> D2 (RX logiciel)
// SIM800L RX -> D3 (TX logiciel) via diviseur de tension (5V -> ~2.8V)

LiquidCrystal lcd(7, 8, 9, 10, 11, 12);
SoftwareSerial sim800(2, 3);  // RX, TX

const char USSD_CODE[] = "#111*1*6*1#";
const unsigned long MODEM_TIMEOUT_MS = 12000;
const unsigned long USSD_TIMEOUT_MS = 30000;

String extractQuoted(const String &line) {
  int first = line.indexOf('"');
  if (first < 0) return "";
  int second = line.indexOf('"', first + 1);
  if (second < 0) return "";
  return line.substring(first + 1, second);
}

void lcdPrint2Lines(const String &l1, const String &l2) {
  lcd.clear();
  lcd.setCursor(0, 0);
  lcd.print(l1.substring(0, 16));
  lcd.setCursor(0, 1);
  lcd.print(l2.substring(0, 16));
}

void lcdScrollMessage(const String &msg, unsigned long stepDelay = 260) {
  String padded = msg + "                ";
  for (unsigned int i = 0; i + 16 <= padded.length(); i++) {
    lcd.setCursor(0, 1);
    lcd.print(padded.substring(i, i + 16));
    delay(stepDelay);
  }
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
  if (!sendAT("AT+CMGF=1", "OK", MODEM_TIMEOUT_MS, response)) return false;

  if (!sendAT("AT+CREG?", "+CREG:", MODEM_TIMEOUT_MS, response)) return false;
  if (response.indexOf(",1") < 0 && response.indexOf(",5") < 0) return false;

  if (!sendAT("AT+CSQ", "+CSQ:", MODEM_TIMEOUT_MS, response)) return false;

  if (!sendAT("AT+CUSD=1", "OK", MODEM_TIMEOUT_MS, response)) return false;

  return true;
}

bool requestUSSD(const String &code, String &resultText) {
  String response;
  String cmd = "AT+CUSD=1,\"" + code + "\",15";

  if (!sendAT(cmd, "OK", MODEM_TIMEOUT_MS, response)) {
    return false;
  }

  unsigned long start = millis();
  String line = "";
  String all = "";

  while (millis() - start < USSD_TIMEOUT_MS) {
    while (sim800.available()) {
      char c = sim800.read();
      all += c;
      if (c == '\n') {
        line.trim();
        if (line.startsWith("+CUSD:")) {
          String text = extractQuoted(line);
          if (text.length() > 0) {
            resultText = text;
            return true;
          }
        }
        if (line.indexOf("ERROR") >= 0) {
          return false;
        }
        line = "";
      } else if (c != '\r') {
        line += c;
      }
    }
  }

  resultText = "Timeout USSD";
  return false;
}

void setup() {
  lcd.begin(16, 2);
  lcdPrint2Lines("Demarrage...", "SIM800L + LCD");

  Serial.begin(9600);
  sim800.begin(9600);
  delay(1200);

  lcdPrint2Lines("Init modem...", "patientez");

  if (!initModem()) {
    lcdPrint2Lines("Echec modem", "Verifier reseau");
    return;
  }

  lcdPrint2Lines("USSD en cours", String(USSD_CODE));

  String ussdResult;
  if (requestUSSD(USSD_CODE, ussdResult)) {
    lcdPrint2Lines("Reponse USSD:", "");
    delay(900);
    if (ussdResult.length() <= 16) {
      lcdPrint2Lines("Reponse USSD:", ussdResult);
    } else {
      lcdPrint2Lines("Reponse USSD:", ussdResult.substring(0, 16));
      delay(1000);
      lcdScrollMessage(ussdResult);
    }
    Serial.println("USSD OK: " + ussdResult);
  } else {
    lcdPrint2Lines("USSD echec", "ou timeout");
    Serial.println("USSD Failed / timeout");
  }
}

void loop() {
  // Aucun polling continu pour eviter d'envoyer plusieurs fois le code USSD.
}
