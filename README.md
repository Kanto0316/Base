# NetK File Manager

NetK File Manager est un gestionnaire de documents local Android, hors ligne, conçu avec Material Design 3.

## Fonctionnalités

- détection via Android MediaStore des fichiers PDF, Excel (`.xls`, `.xlsx`), Word (`.doc`, `.docx`), texte et image ;
- navigation par onglets PDF, Excel, Word, TXT et Images ;
- recherche instantanée par nom dans la catégorie active ;
- liste compacte indiquant le type, le nom, la date de modification et la taille ;
- ouverture avec l'application Android compatible choisie par l'utilisateur ;
- permissions adaptées à Android 8–12 et à l'autorisation Images d'Android 13 ou ultérieur.

Les documents ne sont jamais copiés ou modifiés : l'application consulte les URI sécurisées indexées par MediaStore.

## Architecture

L'application conserve son package `com.netk.mvola` et utilise Kotlin, MVVM, Repository Pattern et Jetpack Compose avec Material Design 3.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

Le dossier `build/` et les APK sont ignorés par Git. Le workflow GitHub Actions existant compile et publie seul l'APK comme artifact.
