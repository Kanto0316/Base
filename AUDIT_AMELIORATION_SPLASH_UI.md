# Audit de l’amélioration UI du Splash Screen Android

## Analyse de l’existant

Avant cette évolution, le splash était créé nativement dans `MainActivity.createSplashView()` et
affiché au-dessus d’une `WebView` maintenue invisible. Il présentait l’icône issue de
`R.mipmap.ic_launcher`, le nom « Suivi Matériel » et un unique champ texte alternant `.`, `..` et
`...` sur fond blanc.

Sa fermeture reposait déjà sur deux mécanismes complémentaires :

1. le bridge JavaScript `AndroidApp.readyFirestore()`, accepté uniquement depuis le site HTTPS de
   confiance ;
2. un délai de sécurité de 12 secondes, qui empêche le splash de rester affiché indéfiniment.

La fermeture annulait l’animation, rendait la `WebView` visible puis masquait le splash. Les mêmes
nettoyages étaient effectués à la destruction de l’Activity.

## Améliorations visuelles réalisées

- Le fond blanc est remplacé par un dégradé vertical léger dérivé de la couleur principale de
  l’application.
- L’icône est placée sur une carte blanche aux coins arrondis, avec une élévation produisant une
  ombre douce.
- Son apparition combine un fondu et un agrandissement progressif.
- L’ancien texte de points est remplacé par trois pastilles graphiques. Leur opacité et leur
  échelle sont animées avec un décalage successif et une répétition infinie.
- Le libellé « Chargement des données… » est affiché sous l’indicateur.
- La mention « © 2026 Suivi Matériel » est positionnée discrètement en bas de l’écran.
- Les textes, couleurs et formes ont été placés dans les ressources Android dédiées.

## Fonctionnement conservé

Cette modification est strictement visuelle. Elle conserve sans changement :

- la création et la configuration de la `WebView` ;
- le bridge `AndroidApp` et le signal `readyFirestore()` ;
- la validation de l’origine HTTPS avant fermeture ;
- le timeout de sécurité de 12 secondes ;
- les bridges Firebase Auth et AndroidDownloads ;
- la gestion des téléchargements, des exports et des notifications ;
- les fonctions existantes de navigation, de diagnostic et d’erreur réseau.

Les nouveaux animateurs sont arrêtés et libérés dans `hideSplash()` et `onDestroy()`, comme
l’ancien animateur, afin qu’aucune animation ne continue après la fermeture du splash.

## Fichiers concernés

- `app/src/main/java/com/netk/app/MainActivity.kt` : composition visuelle et animations du splash.
- `app/src/main/res/drawable/splash_background.xml` : dégradé de fond.
- `app/src/main/res/drawable/splash_icon_background.xml` : surface arrondie de l’icône.
- `app/src/main/res/drawable/splash_dot.xml` : forme d’un point de chargement.
- `app/src/main/res/values/colors.xml` : couleurs du fond et de la surface.
- `app/src/main/res/values/strings.xml` : libellé de chargement et copyright.

## Conclusion

Le splash conserve son rôle de masque jusqu’à la disponibilité des données Firestore ou jusqu’au
timeout de secours. Seule sa présentation est modernisée, sans modification de Firebase, de la
`WebView`, d’AndroidDownloads ou des exports.
