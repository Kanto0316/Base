# Audit Phase 4.4 — Correction du callback diagnostic Auth

## Cause trouvée

Le pont Android appelait `updateFirebaseDiagnostic(...)`, alors que la carte de diagnostic Web
expose `window.updateAuthDebug(status, message)`. En outre, le callback de
`evaluateJavascript` supprimait systématiquement le diagnostic en attente, sans vérifier si le
JavaScript avait réellement trouvé et exécuté la fonction Web.

## Fichier modifié

- `app/src/main/java/com/netk/app/MainActivity.kt`

## Fonction modifiée

`deliverPendingFirebaseDiagnostic()` appelle désormais
`window.updateAuthDebug(status, message)`. Le message conserve l'événement et, lorsqu'elle existe,
la description de l'erreur. Le script renvoie explicitement `true` uniquement après l'appel de la
fonction Web :

- en cas de succès, le log `[BRIDGE_CHECK] diagnostic_send_success` est écrit et le diagnostic
  concerné est supprimé de la file d'attente ;
- en cas d'échec, les logs `[BRIDGE_CHECK] diagnostic_send_failed` et
  `[BRIDGE_CHECK] javascript_callback_failed` sont écrits, et le diagnostic reste en attente.

Le début de chaque tentative est signalé par `[BRIDGE_CHECK] diagnostic_send_start`. Les logs ne
contiennent aucun token, credential ou email complet ; seule la présence éventuelle d'un email est
indiquée.

## Résultat attendu après installation de l'APK

Après chargement de la page Web approuvée et déclenchement d'un événement Auth, la carte diagnostic
doit recevoir le statut et le message via `window.updateAuthDebug`. Une exécution confirmée vide le
diagnostic en attente. Si la fonction Web est absente ou lève une exception, les marqueurs d'échec
apparaissent dans Logcat et le diagnostic est conservé afin qu'une tentative ultérieure puisse le
livrer.
