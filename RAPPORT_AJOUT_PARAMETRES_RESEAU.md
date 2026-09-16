# Rapport — ajout des paramètres réseau

## Fichiers modifiés

- `app/src/main/java/com/netk/app/MainActivity.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/drawable/ic_network_unavailable.xml`
- `app/src/main/res/drawable/network_button_primary.xml`
- `app/src/main/res/drawable/network_button_secondary.xml`

## Méthode utilisée

L'écran natif affiché lors d'une erreur de chargement de la WebView conserve le bouton **RÉESSAYER** et présente désormais une icône de réseau indisponible, le titre **Connexion impossible**, un message explicatif et le bouton **PARAMÈTRES RÉSEAU**.

Les couleurs, l'icône vectorielle et les fonds des boutons sont des ressources Android. Ils reprennent la palette Suivi Matériel (`#59B8FF`, `#278DDA`, `#F4F7FB`, `#1F2A37` et `#6B7280`). Les libellés et descriptions d'accessibilité sont externalisés dans `strings.xml`.

Le bouton **RÉESSAYER** appelle toujours `loadSite()`, qui contrôle la connexion puis recharge l'URL dans la WebView.

## Ouverture des paramètres et fallback

À partir d'Android 10 (API 29), le bouton lance en priorité :

```kotlin
Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
```

Sur Android 8 et 9, ou si le panneau n'est pas disponible/autorisé, l'application utilise :

```kotlin
Intent(Settings.ACTION_WIRELESS_SETTINGS)
```

Les exceptions `ActivityNotFoundException` et `SecurityException` sont interceptées. Si même l'écran de secours ne peut pas être ouvert, un message Toast informe l'utilisateur sans fermer l'application.

## Tests réalisés

- Vérification statique de l'absence d'erreurs d'espaces avec `git diff --check` : réussie.
- Tentative de `./gradlew test lint` : non exécutable avec le JDK 25 fourni (échec de compatibilité Java `25.0.2`).
- Nouvelle tentative avec le JDK 17 installé (`JAVA_HOME=/root/.local/share/mise/installs/java/17.0.2 ./gradlew test lint`) : bloquée par l'environnement, qui ne peut pas télécharger/résoudre le plugin Android Gradle `com.android.application:8.9.0`.
- Revue statique des parcours : affichage lors d'une absence de réseau ou d'une erreur du cadre principal, ouverture du panneau avec fallback, retour Android sans changement d'état destructif, et rechargement par **RÉESSAYER**.

Les quatre scénarios fonctionnels demandés doivent être validés sur un émulateur ou un appareil disposant des dépendances Gradle : couper Internet, ouvrir **PARAMÈTRES RÉSEAU**, revenir dans l'application, réactiver Internet puis choisir **RÉESSAYER**.

## Absence d'impact

La modification est limitée à la construction de l'écran d'erreur réseau et à l'ouverture des paramètres système. Aucun changement n'a été apporté au Splash Screen Firestore, à `AndroidApp.readyFirestore()`, à `AndroidDownloads`, aux notifications, aux exports `.su`/`.xlsx`, à Firebase Auth, à la navigation de la WebView, ni aux fichiers Web (`index.html`, JavaScript et CSS).
