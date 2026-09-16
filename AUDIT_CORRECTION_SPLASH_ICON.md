# Audit de correction de l’icône du Splash Screen Android

## Fichier modifié

- `app/src/main/java/com/netk/app/MainActivity.kt`

## Correction appliquée

- Ancienne référence : `R.drawable.ic_launcher`
- Nouvelle référence : `R.mipmap.ic_launcher`

La méthode `createSplashView()` charge désormais l’icône du lanceur depuis les ressources `mipmap`.

## Vérifications effectuées

1. Recherche des anciennes références avec `rg -n "R\\.drawable\\.ic_launcher|@drawable/ic_launcher" app/src` : aucune occurrence trouvée.
2. Recherche de la nouvelle référence avec `rg -n "R\\.mipmap\\.ic_launcher" app/src/main/java` : la référence est présente dans `MainActivity.kt`.
3. Contrôle du manifeste : `android:icon="@mipmap/ic_launcher"` et `android:roundIcon="@mipmap/ic_launcher_round"` sont conservés.
4. Compilation lancée avec `./gradlew :app:assembleDebug`. Dans l’environnement d’audit, la première tentative a été bloquée par Java 25 (`25.0.2`). Une seconde tentative avec Java 17, conforme à la CI, a été bloquée par l’impossibilité de télécharger le plugin Android Gradle `com.android.application:8.9.0` depuis les dépôts externes (accès réseau HTTP 403). Cette limitation d’environnement est indépendante de la correction.

## Conclusion

L’ancien logo B fourni par `drawable/ic_launcher.xml` n’est plus utilisé par le Splash Screen personnalisé. Celui-ci utilise maintenant `R.mipmap.ic_launcher`.
