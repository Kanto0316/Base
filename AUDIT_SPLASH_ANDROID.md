# Audit du splash Android piloté par Firestore

## Périmètre

Cette évolution est limitée au projet Android. Elle ne modifie ni Firebase Auth Android, ni les
téléchargements et exports, ni les notifications, ni la navigation retour. Le splash ne déduit
jamais l'état de connexion de l'utilisateur et n'attend aucun événement Firebase Auth.

## Implémentation

- `MainActivity` construit le splash natif avant la `WebView`, puis le place au premier plan. La
  `WebView` reste invisible afin qu'aucun contenu partiellement rendu ne transparaisse.
- Le splash présente l'icône de l'application, « Suivi Matériel » et un indicateur natif qui anime
  successivement `.`, `..` et `...`.
- Une troisième interface JavaScript est enregistrée sous le nom `AndroidApp`, à côté des bridges
  `AndroidAuth` et `AndroidDownloads` existants.
- La page Web signale que ses données sont utilisables avec :

  ```javascript
  window.AndroidApp?.readyFirestore();
  ```

  L'appel peut être effectué que l'utilisateur soit connecté ou non. Android vérifie que l'URL
  courante appartient au site HTTPS approuvé avant de retirer le splash sur le thread de la
  `WebView`.

## Recommandation côté Web

Appeler `readyFirestore()` dès que l'état initial nécessaire à l'écran est résolu : données
Firestore reçues, absence de session constatée, collection vide, ou erreur Firestore gérée. Il ne
faut pas attendre Firebase Auth uniquement pour effectuer cet appel.

Pour une page qui n'utilise pas Firestore, appeler le bridge dès que la page est prête à être
affichée. Cela évite d'attendre le mécanisme de secours Android.

## Cas de secours et réseau

Un délai de sécurité de 12 secondes retire automatiquement le splash si le signal JavaScript
n'arrive jamais. Ce comportement couvre notamment une page sans Firestore, une intégration Web
ancienne, une erreur réseau ou une erreur JavaScript, et empêche un blocage permanent. L'écran
d'erreur Web existant devient alors visible s'il a été activé. Le délai est annulé dès que le
bridge valide est reçu et lors de la destruction de l'Activity.

## Logs de diagnostic

Filtrer Logcat avec :

```bash
adb logcat -s FirebaseAuth
```

Séquence nominale :

1. `[SPLASH] affiché`
2. `[SPLASH] bridge prêt reçu`
3. `[SPLASH] splash masqué`

En l'absence de signal, le log `[SPLASH] délai dépassé, affichage de la WebView` précède
`[SPLASH] splash masqué`. Un appel provenant d'une URL non approuvée est ignoré et journalisé.
