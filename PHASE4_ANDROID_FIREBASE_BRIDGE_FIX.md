# Phase 4 — Audit et correction du pont Firebase Android → WebView

## Résultat de l'audit

### Ligne de rupture identifiée

- **Fichier responsable :** `app/src/main/java/com/netk/app/MainActivity.kt`.
- **Fonctions responsables avant correction :** le callback de l'Activity Result et
  `sendGoogleIdTokenToWebView`.
- **Rupture :** après récupération du `GoogleSignInAccount`, le token était envoyé directement à
  Firebase Web. Android n'appelait jamais `FirebaseAuth.getInstance()`, ne créait pas de credential
  `GoogleAuthProvider` et ne validait donc jamais un `FirebaseUser` natif.
- **Rupture secondaire :** l'interface exposée s'appelait `AndroidGoogleSignIn` et sa méthode
  `openAccountChooser()`, alors que le contrat Phase 3 est
  `window.AndroidAuth.startGoogleSignIn()`.
- **Risque de timing :** `onPageFinished()` injectait le gestionnaire de clic, mais l'envoi du token
  ne vérifiait pas réellement que `window.firebaseLoginWithToken` était déjà une fonction.

### Correction appliquée

1. L'Activity Result extrait le compte et vérifie `account.idToken` sans jamais journaliser sa
   valeur.
2. `signInToFirebase()` construit le credential Google, appelle
   `FirebaseAuth.signInWithCredential()` et exige un `auth.currentUser` non nul.
3. Le token n'est placé en mémoire que dans `pendingGoogleIdToken`; il n'est envoyé qu'après le
   chargement d'une URL HTTPS de l'hôte autorisé et après confirmation JavaScript de la présence de
   `window.firebaseLoginWithToken`.
4. `onPageFinished()` démarre la détection du pont. Une courte relance couvre le chargement différé
   du module Firebase Web.
5. L'objet JavaScript exposé est exactement `AndroidAuth`, avec
   `startGoogleSignIn()` et un callback interne de diagnostic.
6. L'appel Web est protégé par `JSONObject.quote()`. Aucun log ne contient l'ID token.

## Configuration Google / Firebase

- Le client demandé par `requestIdToken()` est `default_web_client_id`, déclaré comme client OAuth
  **Web** dans `app/src/main/res/values/strings.xml`.
- Le dépôt ne contient actuellement **aucun** `app/google-services.json`. Il est impossible de
  vérifier hors ligne que le client Web appartient bien au même projet Firebase, ni d'initialiser
  Firebase Android sur un appareil sans ce fichier.
- Le plugin Google Services est déclaré et s'applique automatiquement lorsque
  `app/google-services.json` est fourni. Son absence ne casse pas les builds de contrôle du dépôt;
  l'application publie alors `FIREBASE_CONFIGURATION_ERROR` au lieu de planter.
- À provisionner avant validation sur appareil : télécharger depuis la console Firebase le
  `google-services.json` de l'application Android `com.netk.app`, le placer dans `app/`, puis
  confirmer que son client OAuth Web correspond à `default_web_client_id`.

## Logs de diagnostic ajoutés

```text
[ANDROID_AUTH] start_google_signin
[GOOGLE_RESULT] account_received
[GOOGLE_RESULT] idToken_present=true/false
[FIREBASE_RESULT] uid_received
[FIREBASE_RESULT] email_received
[WEBVIEW_BRIDGE] bridge_ready
[WEBVIEW_BRIDGE] token_sent
[WEBVIEW_BRIDGE] javascript_callback_success
[WEBVIEW_BRIDGE] javascript_callback_error
```

## Fichiers audités

- `MainActivity.kt` : flux Google, Firebase natif, interface JavaScript et timing WebView corrigés.
- `app/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` : Firebase Auth/BOM et
  plugin Google Services ajoutés.
- `AndroidManifest.xml` : permissions réseau et déclaration de l'Activity déjà adaptées; aucune
  modification nécessaire.
- `google-services.json` : absent du dépôt; provisionnement requis comme indiqué ci-dessus.

