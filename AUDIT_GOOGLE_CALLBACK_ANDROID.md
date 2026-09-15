# Audit du callback Google Sign-In Android

## Périmètre

Cet audit porte uniquement sur le trajet Android entre l'ouverture du sélecteur de compte Google
et la remise du résultat à `MainActivity`. La logique d'authentification Firebase n'a pas été
modifiée. Aucun token, ID token ou credential n'est écrit dans les logs ou dans le diagnostic.

## Résultat de l'audit statique

- Le `ActivityResultLauncher` est déclaré comme propriété de `MainActivity` avec
  `registerForActivityResult(...)`. Il est donc enregistré pendant l'initialisation de l'Activity,
  avant que `showGoogleAccountChooser()` puisse l'utiliser.
- `googleSignInLauncher.launch(signInIntent)` est bien exécuté après la construction de l'intent.
  Le log `[GOOGLE_CALLBACK] launcher_launch_called` confirme le passage exact à cet endroit.
- Le callback ne contient aucun appel à `finish()`.
- Le callback ne contient ni `loadSite()`, ni `WebView.reload()`, ni `WebView.loadUrl()`. Le retour
  de Google ne provoque donc aucun rechargement explicite de la WebView.

## Diagnostic à relever sur l'appareil

Filtrer Logcat avec la commande suivante :

```bash
adb logcat -s FirebaseAuth
```

Puis sélectionner un compte Google et lire les événements dans cet ordre :

1. `[GOOGLE_CALLBACK] launcher_launch_called` : Android a demandé l'ouverture du sélecteur.
2. `[GOOGLE_CALLBACK] activity_result_received` : **le callback Android est appelé**.
3. `[GOOGLE_CALLBACK] resultCode=RESULT_OK`, `RESULT_CANCELED` ou `OTHER(<code>)` : résultat exact
   rendu par l'Activity Google.
4. `[GOOGLE_CALLBACK] intent_received=true/false` : présence ou absence de l'intent de retour.
5. `[GOOGLE_CALLBACK] extracting_account` : début de l'extraction du compte.
6. `event=GOOGLE_ACCOUNT_RECEIVED` et le diagnostic visible « Compte Google reçu » : **le compte
   Google est reçu**.
7. En cas d'échec, `[GOOGLE_CALLBACK] ApiException code=<code> message=<message>` donne le
   **statusCode exact** et son message sans exposer de secret.

## Interprétation et prochaine correction

| Dernier événement observé | Conclusion | Prochaine correction nécessaire |
| --- | --- | --- |
| Aucun `launcher_launch_called` | Le pont WebView n'atteint pas le lancement Android. | Auditer l'injection/clic JavaScript et la validation de l'URL de confiance. |
| `launcher_launch_called`, sans `activity_result_received` | Le sélecteur est lancé, mais Android ne remet pas de résultat à cette instance d'Activity. | Relever le cycle de vie et les logs système de l'Activity sur l'appareil; vérifier qu'elle n'est pas recréée ou détruite par la configuration/manifeste. |
| `RESULT_CANCELED` | Google a rendu une annulation; aucune extraction de compte n'est tentée. | Relever les logs Google Play Services et vérifier la configuration OAuth, le SHA et le nom de package. |
| `RESULT_OK` avec `intent_received=false` | Le résultat est déclaré valide mais sans intent exploitable. | Auditer la version/comportement de Google Play Services sur l'appareil. |
| `ApiException code=<code>` | Le callback fonctionne, mais Google refuse ou ne peut pas fournir le compte. | Corriger la configuration indiquée par ce code exact (client OAuth, SHA, package ou état Play Services), sans modifier Firebase avant cette résolution. |
| `GOOGLE_ACCOUNT_RECEIVED` | Le callback et l'extraction du compte fonctionnent. | Poursuivre le diagnostic à l'étape suivante du flux; aucune correction du callback Android n'est nécessaire. |

## État avant exécution sur appareil

L'audit statique confirme que le callback est enregistré et que le launcher est appelé. En
revanche, un audit du code seul ne peut pas affirmer quel événement est réellement reçu sur un
appareil. La séquence Logcat ci-dessus rend désormais observables, sans ambiguïté, l'appel du
callback, la réception du compte et le code exact de tout échec. La prochaine correction doit être
choisie uniquement à partir du dernier événement observé dans ce tableau.
