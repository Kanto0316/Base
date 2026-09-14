# PDF by Kanto

Application Android légère qui affiche le site distant de PDF by Kanto dans une WebView.

## Fonctionnalités

- chargement automatique de `http://kanto0316.github.io/Album` ;
- JavaScript et stockage DOM activés ;
- navigation des liens et historique dans la WebView ;
- message d'erreur avec action de nouvelle tentative lorsque le site est inaccessible.

## Architecture

Le package est `com.netk.app`. L'application utilise Kotlin et la WebView Android native, sans base de données ni fonctionnalité locale de conversion PDF.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

Les APK et fichiers générés ne doivent pas être versionnés. Le workflow GitHub Actions compile l'APK et le publie uniquement comme artifact.
