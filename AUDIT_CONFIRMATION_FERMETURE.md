# Audit — Confirmation de fermeture Android

## Objectif

La gestion du bouton Retour Android doit conserver la navigation dans l'historique de la WebView,
puis demander une confirmation avant de fermer l'application lorsque cet historique est vide.

## État initial

`MainActivity` surchargeait l'ancienne méthode `onBackPressed()` :

- si `webView.canGoBack()` était vrai, elle appelait `webView.goBack()` ;
- sinon, elle déléguait immédiatement à `super.onBackPressed()`, ce qui fermait l'activité sans
  confirmation.

La création et la configuration de la WebView, Firebase Auth, les téléchargements et le bridge
JavaScript `AndroidDownloads` sont indépendants de cette gestion du retour.

## Implémentation

La surcharge obsolète a été remplacée par un `OnBackPressedCallback` enregistré auprès de
`onBackPressedDispatcher` et lié au cycle de vie de `MainActivity`.

À chaque retour Android :

1. le retour est journalisé avec `[APP_EXIT] retour intercepté` ;
2. si la WebView possède un historique, `webView.goBack()` conserve le comportement de navigation
   existant ;
3. sinon, une `AlertDialog` native affiche :
   - le titre **« Quitter l'application ? »** ;
   - le message **« Voulez-vous fermer Suivi Matériel ? »** ;
   - **« Non »**, qui ferme uniquement la boîte de dialogue ;
   - **« Oui »**, qui journalise la fermeture et appelle `finishAffinity()`.

Une boîte déjà visible n'est pas créée une seconde fois. Sa référence est libérée à sa fermeture et
la boîte est explicitement fermée pendant `onDestroy()` afin d'éviter de conserver la fenêtre de
l'activité.

## Journalisation ajoutée

- `[APP_EXIT] retour intercepté`
- `[APP_EXIT] confirmation affichée`
- `[APP_EXIT] application fermée`

## Périmètre préservé

Aucune modification n'a été apportée :

- aux paramètres, au client ou au chargement de la WebView ;
- au flux Firebase Auth et Google Sign-In ;
- au gestionnaire de téléchargement ;
- au bridge `AndroidDownloads` ;
- aux scripts injectés dans la page Web.

## Vérification attendue sur appareil ou émulateur

1. Naviguer vers une seconde page dans la WebView, puis appuyer sur Retour : la page précédente
   doit être restaurée sans dialogue.
2. À la racine de l'historique, appuyer sur Retour : la confirmation doit apparaître.
3. Choisir **« Non »** : la boîte doit disparaître et l'application doit rester ouverte.
4. Ouvrir de nouveau la confirmation puis choisir **« Oui »** : l'application doit se fermer.
5. Contrôler Logcat avec le filtre `APP_EXIT` pour vérifier les trois événements.
