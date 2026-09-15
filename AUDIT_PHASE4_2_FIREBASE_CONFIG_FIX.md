# Audit Phase 4.2 — Correction de la configuration Firebase Android

## Fichiers déplacés

- `google-services.json` a été déplacé de la racine du dépôt vers
  `app/google-services.json`, emplacement attendu par le plugin Google Services pour le module
  `app`.

## Fichiers modifiés

- `app/build.gradle.kts` applique désormais explicitement l'alias du plugin
  `com.google.gms.google-services` dans le bloc `plugins` du module `app`.
- `app/src/main/res/values/strings.xml` ne contient plus de valeur manuelle pour
  `default_web_client_id`.
- `README.md` indique que `default_web_client_id` provient exclusivement de la configuration
  Firebase traitée par le plugin.
- Aucun code métier Firebase Auth n'a été modifié. En particulier, `MainActivity.kt`, le bridge
  WebView, `login.js` et `app.js` sont inchangés.

## État Gradle

- Le plugin `com.google.gms.google-services` est déclaré à la racine avec `apply false` et est
  appliqué explicitement au module `app`.
- Les commandes `:app:tasks --all` et `:app:processDebugGoogleServices` ont été lancées avec le
  JDK 17 requis.
- L'environnement d'audit n'a pas pu résoudre le plugin Android Gradle
  `com.android.application:8.9.0` depuis les dépôts distants (proxy HTTP : réponse 403). La phase
  de configuration Gradle s'arrête donc avant la création des tâches : l'existence effective de
  `processDebugGoogleServices` devra être confirmée dans CI ou dans un environnement disposant
  des dépendances Gradle.

## OAuth détecté

La configuration Firebase correspond au projet `base-737bf` (numéro `560283994192`) et à
l'application Android `com.netk.app`. Le client OAuth de type 3 (Web) fourni par ce même projet est :

```text
560283994192-7is04i1csfomdtcvv7t89omrbldhd2fp.apps.googleusercontent.com
```

L'ancienne valeur manuelle divergente
`560283994192-ede7aa7a3714c439542955.apps.googleusercontent.com` a été supprimée de
`strings.xml`.

## Ressources générées

Le contenu de `app/google-services.json` fournit les trois sources nécessaires au plugin :

| Ressource | Valeur issue de Firebase |
| --- | --- |
| `google_app_id` | `1:560283994192:android:54f2d94898bbc196542955` |
| `gcm_defaultSenderId` | `560283994192` |
| `default_web_client_id` | `560283994192-7is04i1csfomdtcvv7t89omrbldhd2fp.apps.googleusercontent.com` |

La source versionnée `app/src/main/res/values/strings.xml` ne redéfinit aucune de ces valeurs.
Le fichier attendu après exécution réussie de la tâche est
`app/build/generated/res/google-services/debug/values/values.xml`. Il n'a pas été produit dans
l'environnement d'audit, la résolution d'AGP ayant échoué avant l'exécution de la tâche.

## Problèmes restants

- **Validation Gradle en attente** : exécuter `./gradlew :app:tasks --all` puis
  `./gradlew :app:processDebugGoogleServices` dans CI ou avec un accès aux dépôts Google, et
  contrôler les trois entrées dans le `values.xml` généré.
- Conformément à la consigne de phase, ne pas poursuivre les changements Firebase Auth avant
  cette validation.
