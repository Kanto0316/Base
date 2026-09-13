# Base Android

Application Android native en Kotlin qui fournit une base concrète et moderne pour un futur produit. L'écran d'accueil présente les capacités prévues et mène à un second écran, ce qui valide une navigation extensible. L'interface repose sur Jetpack Compose et Material Design 3, avec prise en charge des thèmes clair, sombre et dynamique.

## Prérequis

- Android Studio Ladybug ou plus récent ;
- JDK 17 ;
- Android SDK 35 installé (le `minSdk` 26 assure la compatibilité à partir d'Android 8.0).

## Installation locale

1. Cloner ce dépôt.
2. Ouvrir sa racine dans Android Studio.
3. Laisser Gradle synchroniser les dépendances.
4. Sélectionner un appareil ou un émulateur Android 8.0+ puis exécuter la configuration `app`.

Il est aussi possible de compiler directement depuis un terminal :

```bash
gradle assembleDebug
```

L'APK local est produit dans `app/build/outputs/apk/debug/app-debug.apk`.

## APK depuis GitHub Actions

Chaque push sur la branche `main` déclenche le workflow **Android APK**. Dans GitHub, ouvrir **Actions**, sélectionner l'exécution concernée, puis télécharger l'artifact **base-android-debug** depuis la section *Artifacts* de la page de résumé.

Le workflow peut également être lancé manuellement avec **Run workflow**.

## Architecture et évolutions

Le projet suit une séparation MVVM légère :

- `ui/navigation` centralise les destinations ;
- `ui/home` regroupe l'état, le ViewModel et l'interface de la fonctionnalité d'accueil ;
- `ui/settings` illustre l'ajout d'une page indépendante ;
- `ui/theme` centralise Material 3 et les thèmes système.

Les futures fonctionnalités peuvent être ajoutées sous forme de modules ou de packages dédiés sans coupler l'interface aux sources de données :

- **connexion** : couche `auth` et fournisseur d'identité injecté dans un ViewModel ;
- **Firebase** : plugins et implémentations dans une couche `data` ;
- **API REST** : interface de service et repository dans `data/remote` ;
- **Room** : base, DAO et entités dans `data/local` ;
- **notifications** : service spécialisé et gestion des permissions selon la version Android.

Ces bibliothèques ne sont volontairement pas encore déclarées afin de garder la compilation rapide et de ne pas imposer une solution avant son utilisation réelle.
