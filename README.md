# PDF by Kanto

Application Android locale qui transforme une ou plusieurs images en un document PDF.

## Fonctionnalités V1

- sélection d'une ou plusieurs images avec Android Photo Picker ;
- sélection de toutes les images accessibles via MediaStore ;
- aperçu, nom et compteur des images choisies ;
- une page PDF par image, avec orientation portrait ou paysage conservée ;
- nom de fichier automatique `PDF_Kanto_date_heure.pdf` ;
- historique Room des projets, de leurs images et du chemin du PDF ;
- consultation d'un ancien projet et ouverture du PDF avec une application compatible ;
- messages explicites lors d'un refus de permission.

Les PDF sont enregistrés dans le dossier Documents propre à l'application. Aucune image source n'est modifiée.

## Architecture

Le package est `com.netk.app`. L'application utilise Kotlin, Jetpack Compose, Material Design 3, MVVM, un repository et Room.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

Les APK et fichiers générés ne doivent pas être versionnés. Le workflow GitHub Actions compile l'APK et le publie uniquement comme artifact.
