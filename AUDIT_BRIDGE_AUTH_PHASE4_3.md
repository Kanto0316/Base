# Audit Bridge Android Auth — phase 4.3

## Périmètre et méthode

L'audit est limité à `MainActivity.kt`. Aucun comportement fonctionnel n'a été modifié : seuls les
cinq marqueurs temporaires `[BRIDGE_CHECK]` demandés ont été ajoutés. L'analyse est statique, car
aucun appareil Android et aucune commande `adb` exploitable ne sont disponibles dans cet
environnement.

## 1. Création du WebView et interface JavaScript

- Le `WebView` est construit dans `onCreate()` par `WebView(this)` et JavaScript est activé avant le
  chargement du site.
- `addJavascriptInterface(AndroidAuthBridge(), GOOGLE_BRIDGE_NAME)` est exécuté pendant la
  construction du `WebView`.
- `GOOGLE_BRIDGE_NAME` vaut exactement `AndroidAuth`.
- `AndroidAuthBridge.startGoogleSignIn()` existe, porte `@JavascriptInterface`, puis repasse sur la
  file du `WebView` avant d'appeler `showGoogleAccountChooser()`.
- Le script injecté appelle exactement `window.AndroidAuth.startGoogleSignIn()` lorsqu'il reconnaît
  un clic sur un élément dont la description contient `google`.

Les marqueurs `webview_created` et `javascript_interface_added` permettent de distinguer la création
du composant de l'exposition effective de l'interface.

## 2. Inventaire exhaustif de `evaluateJavascript()`

| Fonction Kotlin appelante | Moment de l'appel | JavaScript envoyé |
| --- | --- | --- |
| `SiteWebViewClient.onPageFinished()` | À la fin du chargement d'une URL HTTPS dont l'hôte est `kanto0316.github.io` | `GOOGLE_BUTTON_BRIDGE_SCRIPT` : installe une seule fois un écouteur de clic en capture; recherche un bouton/lien/élément `role=button` décrit comme Google; annule le clic puis appelle `window.AndroidAuth.startGoogleSignIn()` |
| `checkWebBridgeReady()` | Après `onPageFinished()`, puis toutes les 250 ms, au maximum 21 évaluations, et à nouveau lorsqu'un ID token attend d'être remis | `typeof window.firebaseLoginWithToken === 'function'` |
| `deliverPendingGoogleIdToken()` | Après succès Firebase, uniquement si la page, l'origine et le bridge Web sont prêts | Une IIFE vérifie `window.firebaseLoginWithToken`, l'appelle avec le token encodé JSON, puis appelle `window.AndroidAuth.onJavascriptCallback(true, null)` ou le callback d'échec. Le token n'est volontairement pas reproduit dans cet audit. |
| `deliverPendingFirebaseDiagnostic()` | Lorsqu'un diagnostic est publié et après chaque fin de chargement de page, si l'URL est approuvée | `updateFirebaseDiagnostic(<objet JSON du diagnostic>)` |

Chaque site d'appel est précédé du même marqueur temporaire
`[BRIDGE_CHECK] evaluate_javascript_called`. Ce marqueur ne contient ni argument JavaScript, ni
token, ni credential, ni adresse e-mail.

### Point critique du diagnostic bloqué

Le chemin Android nommé `updateAuthDebug()` **n'appelle pas une fonction JavaScript de ce nom**.
Il fabrique un `FirebaseDiagnostic`, puis `deliverPendingFirebaseDiagnostic()` tente d'appeler la
fonction globale Web `updateFirebaseDiagnostic(...)`. De plus, le callback de
`evaluateJavascript()` ignore sa valeur de retour et supprime le diagnostic en attente même si le
JavaScript a échoué, par exemple avec un `ReferenceError`. Une fonction absente ou renommée côté
page laisse donc la carte sur « Initialisation » sans nouvelle tentative et sans log explicite de
l'erreur JavaScript.

Cette incompatibilité ne peut pas être confirmée côté page dans le périmètre demandé, mais c'est le
premier défaut statique directement compatible avec le symptôme observé. La correction doit faire
correspondre le nom/contrat de la fonction avec celui réellement exposé par la page (probablement
`updateAuthDebug(...)` si tel est le contrat de la carte), puis ne supprimer le diagnostic en attente
qu'après confirmation d'une exécution JavaScript réussie.

## 3. Retour Google et trace complète

Le retour moderne Activity Result API est utilisé :

- `googleSignInLauncher` est une propriété de `MainActivity`, initialisée par
  `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` ;
- `showGoogleAccountChooser()` construit les options et l'intent Google, puis appelle
  `googleSignInLauncher.launch(signInIntent)` ;
- le callback reçoit l'`ActivityResult`, remet le verrou de sélection à `false` et distingue
  `RESULT_OK`, `RESULT_CANCELED` et les autres codes ;
- en cas de `RESULT_OK`, le compte est extrait avec
  `GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)` ;
- après extraction, `updateAuthDebug("GOOGLE_ACCOUNT_RECEIVED", "Compte Google reçu")` est appelé ;
- aucun remplacement `onActivityResult()` n'existe dans le dépôt.

Trace attendue :

```text
Clic sur un élément identifié comme Google
→ window.AndroidAuth.startGoogleSignIn()
→ AndroidAuthBridge.startGoogleSignIn()
→ showGoogleAccountChooser()
→ [BRIDGE_CHECK] google_launcher_called
→ googleSignInLauncher.launch(intent Google)
→ [BRIDGE_CHECK] activity_result_received
→ RESULT_OK
→ récupération du compte Google
→ updateAuthDebug("GOOGLE_ACCOUNT_RECEIVED", "Compte Google reçu")
→ publishFirebaseDiagnostic()
→ deliverPendingFirebaseDiagnostic()
→ [BRIDGE_CHECK] evaluate_javascript_called
→ updateFirebaseDiagnostic(<diagnostic JSON>)
```

## 4. Confidentialité des logs

Les cinq nouveaux logs `[BRIDGE_CHECK]` sont constants et n'affichent aucun token, credential ou
e-mail. L'audit relève toutefois un écart **préexistant** : `publishFirebaseDiagnostic()` journalise
actuellement `diagnostic.email` en clair. Aucun log ne doit être collecté ou partagé sans filtrer
cette ligne; sa suppression ou son remplacement par un simple booléen `email_present` est requis
lors d'une correction fonctionnelle ultérieure.

## 5. Vérification de l'APK installé

La version installée n'est **pas vérifiable dans cet environnement** : `adb` n'est pas installé,
aucun appareil n'est connecté et aucun APK préconstruit n'est présent dans le dépôt. Une compilation
locale prouve uniquement que les sources compilent; elle ne prouve pas quel binaire est installé sur
un téléphone.

Vérification à effectuer sur l'appareil cible après installation de l'APK issu de ce commit :

```bash
adb shell pm path com.netk.app
adb logcat -c
adb shell am force-stop com.netk.app
adb shell monkey -p com.netk.app 1
adb logcat -d -s FirebaseAuth | grep '\[BRIDGE_CHECK\]'
```

La présence, dans l'ordre, de `webview_created` puis `javascript_interface_added` prouve que le
binaire exécuté contient cette instrumentation. L'absence de ces marqueurs après un démarrage propre
signifie que l'APK installé ne contient pas cette version.

## Conclusion obligatoire

- **Premier point atteint (audit statique) :** création du `WebView`.
- **Dernier point atteint (audit statique) :** l'appel à
  `updateAuthDebug("GOOGLE_ACCOUNT_RECEIVED", ...)` après extraction réussie du compte est présent;
  sa livraison tente ensuite `updateFirebaseDiagnostic(...)` dans la page.
- **Dernier point atteint sur appareil :** indéterminable sans `adb`, appareil et relevé Logcat.
- **Fichier responsable :** `app/src/main/java/com/netk/app/MainActivity.kt`, méthode
  `deliverPendingFirebaseDiagnostic()` (contrat JavaScript non vérifié et résultat ignoré).
- **Correction nécessaire :** aligner le nom et la signature du callback de diagnostic avec la
  fonction réellement exposée par la carte Web, vérifier le résultat de `evaluateJavascript()` et
  conserver/réessayer le diagnostic en cas d'échec. Supprimer aussi le log préexistant de l'e-mail
  complet. Avant toute conclusion runtime, installer l'APK instrumenté et identifier le dernier
  marqueur `[BRIDGE_CHECK]` observé.
