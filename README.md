# PDF by Kanto

Application Android légère qui affiche le site distant de PDF by Kanto dans une WebView.

## Fonctionnalités

- chargement automatique de `https://kanto0316.github.io/Album` ;
- JavaScript et stockage DOM activés ;
- navigation des liens et historique dans la WebView ;
- connexion Firebase via Google Sign-In Android, sans popup ni redirection Firebase dans la WebView ;
- message d'erreur avec action de nouvelle tentative lorsque le site est inaccessible.

## Pont Google natif

Renseigner `default_web_client_id` dans `app/src/main/res/values/strings.xml` avec l'identifiant du
client OAuth **Web** associé au projet Firebase (et non l'identifiant du client Android). Le clic sur
le bouton Google existant est intercepté sans modifier sa présentation. Android ouvre Google
Sign-In, demande un ID token, puis appelle `window.firebaseLoginWithToken(idToken)` dans la page.

La page distante doit exposer cette fonction depuis son module Firebase existant, sans créer une
seconde application Firebase :

```js
window.firebaseLoginWithToken = async function (idToken) {
  console.log("Firebase Auth: token Android reçu");
  const credential = GoogleAuthProvider.credential(idToken);
  const result = await signInWithCredential(auth, credential);
  console.log("Firebase Auth : CONNECTÉ");
  console.log("email", result.user.email);
  console.log("uid", result.user.uid);
  return result;
};
```

Le flux WebView ne doit appeler ni `signInWithRedirect()` ni `getRedirectResult()` ; l'observateur
`onAuthStateChanged` existant continue ainsi à piloter l'état utilisateur de l'interface.

## Architecture

Le package est `com.netk.app`. L'application utilise Kotlin et la WebView Android native, sans base de données ni fonctionnalité locale de conversion PDF.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

Les APK et fichiers générés ne doivent pas être versionnés. Le workflow GitHub Actions compile l'APK et le publie uniquement comme artifact.
