# NetK Mini Caisse

NetK Mini Caisse est une application Android native de gestion des ventes, conçue pour les petites boutiques. Elle fonctionne entièrement hors ligne : les ventes sont conservées dans une base Room locale et les statistiques sont actualisées automatiquement.

## Fonctionnalités

- tableau de bord du jour (chiffre d'affaires, espèces et MVola) ;
- saisie d'une vente avec calcul automatique du montant ;
- historique, recherche par produit et suppression ;
- nom de boutique enregistré localement et devise Ariary ;
- navigation Material Design 3 adaptée au téléphone.

## Architecture

L'application utilise Kotlin, Jetpack Compose, Navigation Compose, Room et une architecture MVVM. La séparation `data/local`, repository, ViewModel et écrans Compose permet d'ajouter ultérieurement des implémentations de synchronisation Firebase, de lecture SMS MVola, de notifications ou d'export PDF sans modifier le modèle local.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`. Le workflow GitHub Actions existant compile aussi l'APK à chaque push sur `main` et peut être lancé manuellement.
