#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <SoftwareSerial.h>

// ======== Cablage (UNO) ========
// LCD 16x2 I2C:
// SDA -> A4, SCL -> A5
// Adresse I2C LCD la plus courante: 0x27 (parfois 0x3F)
// SIM800L:
// SIM800L TX -> D7 (RX logiciel)
// SIM800L RX -> D8 (TX logiciel) via diviseur de tension (5V -> ~2.8V)

LiquidCrystal_I2C lcd(0x27, 16, 2);
SoftwareSerial sim800(7, 8);  // RX, TX

const unsigned long MODEM_TIMEOUT_MS = 12000;
const unsigned long SMS_POLL_INTERVAL_MS = 3000;
const unsigned long SMS_TREATMENT_COOLDOWN_MS = 60000;
unsigned long lastSmsPollMs = 0;
unsigned long lastSmsTreatmentMs = 0;

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
  if (!sendAT("AT+CSCS=\"GSM\"", "OK", MODEM_TIMEOUT_MS, response)) return false;
  if (!sendAT("AT+CNMI=1,1,0,0,0", "OK", MODEM_TIMEOUT_MS, response)) return false;

  return true;
}

String trimSmsText(String text) {
  text.trim();
  text.replace("\r", "");
  text.replace("\n", "");
  text.trim();
  return text;
}

String generateCode4() {
  static const char alphabet[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  String code = "";
  for (uint8_t i = 0; i < 4; i++) {
    uint8_t index = random(0, sizeof(alphabet) - 1);
    code += alphabet[index];
  }
  return code;
}

bool sendSms(const String &phone, const String &message) {
  String response;
  while (sim800.available()) sim800.read();

  sim800.print("AT+CMGS=\"");
  sim800.print(phone);
  sim800.println("\"");

  if (!waitForToken(">", MODEM_TIMEOUT_MS, response)) {
    return false;
  }

  sim800.print(message);
  sim800.write(26);  // Ctrl+Z
  return waitForToken("OK", MODEM_TIMEOUT_MS, response);
}

void clearMessageBoxes() {
  String response;
  // Nettoie toutes les boites (recu, lu, envoye, brouillon, etc.).
  sendAT("AT+CMGDA=\"DEL ALL\"", "OK", MODEM_TIMEOUT_MS, response);
}

void processUnreadSms() {
  if (lastSmsTreatmentMs != 0 && millis() - lastSmsTreatmentMs < SMS_TREATMENT_COOLDOWN_MS) {
    // Dernier message traite il y a moins d'1 minute: ne rien faire.
    return;
  }

  String response;
  if (!sendAT("AT+CMGL=\"REC UNREAD\"", "OK", MODEM_TIMEOUT_MS, response)) {
    return;
  }

  int searchPos = 0;
  bool treatedAtLeastOne = false;
  while (true) {
    int headerPos = response.indexOf("+CMGL:", searchPos);
    if (headerPos < 0) break;

    int numberStart = response.indexOf("\"", headerPos);
    if (numberStart < 0) break;
    numberStart = response.indexOf("\"", numberStart + 1);
    if (numberStart < 0) break;
    numberStart = response.indexOf("\"", numberStart + 1);
    if (numberStart < 0) break;
    int numberEnd = response.indexOf("\"", numberStart + 1);
    if (numberEnd < 0) break;

    String sender = response.substring(numberStart + 1, numberEnd);

    int bodyStart = response.indexOf("\n", numberEnd);
    if (bodyStart < 0) break;
    bodyStart += 1;
    int bodyEnd = response.indexOf("\n+CMGL:", bodyStart);
    if (bodyEnd < 0) {
      bodyEnd = response.indexOf("\nOK", bodyStart);
      if (bodyEnd < 0) bodyEnd = response.length();
    }

    String smsBody = trimSmsText(response.substring(bodyStart, bodyEnd));
    Serial.print("SMS recu de ");
    Serial.print(sender);
    Serial.print(": ");
    Serial.println(smsBody);

    if (smsBody == "Code") {
      String code = generateCode4();
      String reply = "Votre Code est " + code;
      bool sent = sendSms(sender, reply);
      Serial.println(sent ? "Reponse envoyee" : "Echec envoi reponse");
      lcdPrint2Lines("Code envoye a:", sender.substring(0, 16));
    }

    treatedAtLeastOne = true;
    searchPos = bodyEnd;
  }

  if (treatedAtLeastOne) {
    lastSmsTreatmentMs = millis();
    clearMessageBoxes();
  }
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
    lcdPrint2Lines("Erreur modem", "Init impossible");
    return;
  }

  randomSeed(analogRead(A0));
  lcdPrint2Lines("Mode SMS actif", "Attente message");
}

void loop() {
  if (millis() - lastSmsPollMs >= SMS_POLL_INTERVAL_MS) {
    lastSmsPollMs = millis();
    processUnreadSms();
  }
}
