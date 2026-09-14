# NetK Music

NetK Music est un lecteur audio Android local, hors ligne, conçu avec Material Design 3.

## Fonctionnalités

- détection automatique via Android MediaStore des fichiers MP3, WAV, M4A et FLAC ;
- bibliothèque affichant titre, artiste, album et durée ;
- lecture, pause, piste suivante/précédente et recherche dans la piste ;
- lecture en arrière-plan grâce à un `MediaSessionService` au premier plan et sa notification média ;
- favoris, historique d'écoute et playlists persistés localement avec Room ;
- gestion des autorisations adaptée à Android 8–12 et Android 13 ou ultérieur ;
- interface Jetpack Compose avec les pages Bibliothèque, Lecteur, Favoris et Playlists.

Les fichiers audio ne sont jamais copiés : l'application lit les URI sécurisées fournies par MediaStore.

## Architecture

L'application utilise Kotlin, MVVM, Repository Pattern, Room, Jetpack Compose et AndroidX Media3 (ExoPlayer + MediaSession). `MusicService` reste propriétaire du lecteur lorsque l'activité disparaît et publie automatiquement les commandes Lecture/Pause, Suivant et Précédent dans la notification média.

## Compiler

Prérequis : JDK 17 et Android SDK 35.

```bash
./gradlew assembleDebug
```

L'APK est généré localement dans un dossier `build/` ignoré par Git. Le workflow GitHub Actions existant est chargé de publier l'APK comme artifact.
