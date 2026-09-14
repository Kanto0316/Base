# PDF by Kanto

Application Android légère qui affiche le site distant de PDF by Kanto dans une WebView.

## Fonctionnalités

- chargement automatique de `http://kanto0316.github.io/Album` ;
- JavaScript et stockage DOM activés ;
- navigation des liens et historique dans la WebView ;
- sélection d'un compte Google via le sélecteur Android natif, sans popup ni redirection Firebase ;
- message d'erreur avec action de nouvelle tentative lorsque le site est inaccessible.

## Pont Google natif

Le clic sur le bouton Google existant est intercepté sans modifier sa présentation. Le résultat est
renvoyé à la page par l'événement JavaScript `android-google-account-result`. Sa propriété `detail`
contient `{ account, error }` ; `account` expose `email` et `type` lorsque la sélection réussit. La
page peut aussi déclarer `window.onAndroidGoogleAccountResult(result)`. L'adresse sélectionnée est
affichée sous le bouton Google existant.

## Architecture

Le package est `com.netk.app`. L'application utilise Kotlin et la WebView Android native, sans base de données ni fonctionnalité locale de conversion PDF.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

Les APK et fichiers générés ne doivent pas être versionnés. Le workflow GitHub Actions compile l'APK et le publie uniquement comme artifact.
