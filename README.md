# NetK Notes

NetK Notes est un bloc-notes multimédia Android natif qui fonctionne entièrement hors ligne. Les textes sont conservés dans une base Room locale et les images sont copiées dans le stockage interne privé de l'application.

## Fonctionnalités

- création, consultation, modification et suppression des notes ;
- titre, contenu et dates de création et modification ;
- ajout de plusieurs images depuis le sélecteur Android ;
- consultation et retrait des images liées à une note ;
- interface Material Design 3 adaptée au téléphone, sans compte ni connexion Internet.

## Architecture

L'application utilise Kotlin, Jetpack Compose, Navigation Compose, Room et une architecture MVVM. Les entités `Note` et `ImageEntity` sont reliées par une clé étrangère. Le repository est responsable de la base et de la copie des images dans `filesDir/note_images`.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`. Le workflow GitHub Actions existant compile aussi l'APK à chaque push sur `main` et peut être lancé manuellement.
