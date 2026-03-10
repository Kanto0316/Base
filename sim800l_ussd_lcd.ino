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
const unsigned long LCD_STATUS_DISPLAY_MS = 1500;
const unsigned long MODEM_RETRY_INTERVAL_MS = 5000;
const unsigned long OUTGOING_CALL_WATCH_MS = 45000;
unsigned long lastSmsPollMs = 0;
unsigned long lastModemRetryMs = 0;

bool modemInitialized = false;

String lastIncomingCaller = "";
bool incomingCallRinging = false;

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

bool configureModem() {
  String response;
  if (!sendAT("ATE0", "OK", MODEM_TIMEOUT_MS, response)) return false;
  if (!sendAT("AT+CMGF=1", "OK", MODEM_TIMEOUT_MS, response)) return false;
  if (!sendAT("AT+CSCS=\"GSM\"", "OK", MODEM_TIMEOUT_MS, response)) return false;
  if (!sendAT("AT+CLIP=1", "OK", MODEM_TIMEOUT_MS, response)) return false;
  if (!sendAT("AT+CNMI=1,1,0,0,0", "OK", MODEM_TIMEOUT_MS, response)) return false;
  return true;
}

bool ensureModemReady() {
  if (modemInitialized) return true;

  if (millis() - lastModemRetryMs < MODEM_RETRY_INTERVAL_MS) {
    return false;
  }
  lastModemRetryMs = millis();

  if (!sim800Responds()) {
    Serial.println("SIM800 indisponible, nouvelle tentative...");
    return false;
  }

  modemInitialized = configureModem();
  if (modemInitialized) {
    Serial.println("SIM800 pret.");
    showTemporaryStatus("SIM800 pret", "SMS actifs");
  } else {
    Serial.println("SIM800 detecte mais config echouee.");
  }

  return modemInitialized;
}

bool sim800Responds() {
  String response;
  for (uint8_t i = 0; i < 4; i++) {
    if (sendAT("AT", "OK", MODEM_TIMEOUT_MS, response)) {
      return true;
    }
    delay(500);
  }
  return false;
}

String trimSmsText(String text) {
  text.trim();
  text.replace("\r", "");
  text.replace("\n", "");
  text.trim();
  return text;
}

String normalizeSmsCommand(String text) {
  text = trimSmsText(text);
  text.toUpperCase();
  return text;
}

String generateCode4() {
  static const char alphabet[] = "0123456789";
  String code = "";
  for (uint8_t i = 0; i < 6; i++) {
    uint8_t index = random(0, sizeof(alphabet) - 1);
    code += alphabet[index];
  }
  return code;
}

bool sendSms(const String &phone, const String &message) {
  if (!ensureModemReady()) {
    return false;
  }

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

bool callPhone(const String &phone) {
  if (!ensureModemReady()) {
    return false;
  }

  String response;
  while (sim800.available()) sim800.read();

  sim800.print("ATD");
  sim800.print(phone);
  sim800.println(";");

  return waitForToken("OK", MODEM_TIMEOUT_MS, response);
}

void monitorOutgoingCallAndHangup() {
  unsigned long start = millis();
  String modemLine = "";

  while (millis() - start < OUTGOING_CALL_WATCH_MS) {
    while (sim800.available()) {
      char c = sim800.read();
      if (c == '\r') continue;

      if (c != '\n') {
        modemLine += c;
        continue;
      }

      modemLine.trim();
      if (modemLine.length() == 0) {
        modemLine = "";
        continue;
      }

      if (modemLine == "CONNECT" || modemLine == "VOICE CALL: BEGIN") {
        String response;
        sendAT("ATH", "OK", MODEM_TIMEOUT_MS, response);
        return;
      }

      if (modemLine == "BUSY" || modemLine == "NO CARRIER" || modemLine == "NO ANSWER") {
        return;
      }

      modemLine = "";
    }
  }

  String response;
  sendAT("ATH", "OK", MODEM_TIMEOUT_MS, response);
}

void sendCodeToPhone(const String &phone, const String &contextLabel) {
  String code = generateCode4();
  String reply = "Votre Code est " + code;
  bool sent = sendSms(phone, reply);

  Serial.print(contextLabel);
  Serial.print(" -> ");
  Serial.println(sent ? "Code envoye" : "Echec envoi code");

  showTemporaryStatus(sent ? "Code envoye a:" : "Echec envoi SMS", phone.substring(0, 16));
}


void showIdleScreen() {
  lcdPrint2Lines("Mode SMS actif", "Attente message");
}

void showTemporaryStatus(const String &l1, const String &l2) {
  lcdPrint2Lines(l1, l2);
  delay(LCD_STATUS_DISPLAY_MS);
  showIdleScreen();
}

void processIncomingCallEvents() {
  if (!ensureModemReady()) {
    return;
  }

  static String modemLine = "";

  while (sim800.available()) {
    char c = sim800.read();
    if (c == '\r') continue;

    if (c != '\n') {
      modemLine += c;
      continue;
    }

    modemLine.trim();
    if (modemLine.length() == 0) {
      modemLine = "";
      continue;
    }

    if (modemLine == "RING") {
      incomingCallRinging = true;
    } else if (modemLine.startsWith("+CLIP:")) {
      int firstQuote = modemLine.indexOf('"');
      int secondQuote = modemLine.indexOf('"', firstQuote + 1);
      if (firstQuote >= 0 && secondQuote > firstQuote) {
        lastIncomingCaller = modemLine.substring(firstQuote + 1, secondQuote);
      }
    } else if (modemLine == "NO CARRIER") {
      if (incomingCallRinging && lastIncomingCaller.length() > 0) {
        sendCodeToPhone(lastIncomingCaller, "Appel manque");
      }
      incomingCallRinging = false;
      lastIncomingCaller = "";
    }

    modemLine = "";
  }
}

void clearMessageBoxes() {
  String response;
  // Nettoie toutes les boites (recu, lu, envoye, brouillon, etc.).
  sendAT("AT+CMGDA=\"DEL ALL\"", "OK", MODEM_TIMEOUT_MS, response);
}

void processUnreadSms() {
  if (!ensureModemReady()) {
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

    String smsCommand = normalizeSmsCommand(smsBody);
    if (smsCommand == "CODE") {
      sendCodeToPhone(sender, "Message CODE");
    } else if (smsCommand == "APPEL") {
      bool called = callPhone(sender);
      Serial.println(called ? "Appel lance" : "Echec lancement appel");
      showTemporaryStatus(called ? "Appel vers:" : "Echec appel", sender.substring(0, 16));
      if (called) {
        monitorOutgoingCallAndHangup();
      }
    } else {
      Serial.println("SMS ignore (commande non geree)");
    }

    treatedAtLeastOne = true;
    searchPos = bodyEnd;
  }

  if (treatedAtLeastOne) {
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

  lcdPrint2Lines("Test reponse", "SIM800...");

  modemInitialized = sim800Responds() && configureModem();

  lcdPrint2Lines(modemInitialized ? "SIM800 repond" : "Attente SIM800", "Mode SMS actif");
  delay(1200);

  randomSeed(analogRead(A0));
  showIdleScreen();
}

void loop() {
  ensureModemReady();
  processIncomingCallEvents();

  if (millis() - lastSmsPollMs >= SMS_POLL_INTERVAL_MS) {
    lastSmsPollMs = millis();
    processUnreadSms();
  }
}
