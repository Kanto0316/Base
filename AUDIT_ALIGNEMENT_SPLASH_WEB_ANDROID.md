# Audit d’alignement du Splash Android avec la charte Web

## Objectif

Cette intervention aligne uniquement l’apparence du Splash Screen Android sur la charte graphique
officielle de l’application Web Suivi Matériel. Aucun comportement applicatif n’est modifié.

## Alignement visuel réalisé

- Le fond du splash utilise désormais le dégradé vertical Web allant du bleu clair `#59B8FF` au
  bleu foncé `#278DDA`.
- Le titre « Suivi Matériel » est affiché en blanc (`#FFFFFF`) afin de respecter la couleur de
  texte prévue sur fond bleu.
- Le libellé « Chargement des données… » est blanc avec une opacité de `85 %`, pour conserver une
  hiérarchie visuelle lisible.
- Le copyright est blanc avec une opacité réduite de `65 %`.
- Les trois points animés sont désormais blancs (`#FFFFFF`).

Les couleurs sont centralisées dans `res/values/colors.xml`. Le dégradé continue de les appliquer
depuis `res/drawable/splash_background.xml`, tandis que `res/drawable/splash_dot.xml` utilise la
couleur de contenu sur fond primaire.

## Éléments conservés

Le périmètre de cette modification est exclusivement graphique. Sont conservés sans changement :

- l’icône `@mipmap/ic_launcher` ;
- l’animation d’apparition de l’icône et l’animation des trois points ;
- le signal `readyFirestore()` ;
- le timeout de sécurité de 12 secondes ;
- la `WebView` et sa configuration ;
- l’intégration Firebase ;
- le bridge `AndroidDownloads` et la gestion des téléchargements.

## Fichiers audités

- `app/src/main/java/com/netk/app/MainActivity.kt` : couleurs et opacités des textes du splash ;
- `app/src/main/res/values/colors.xml` : couleurs du dégradé ;
- `app/src/main/res/drawable/splash_background.xml` : application du dégradé ;
- `app/src/main/res/drawable/splash_dot.xml` : couleur des points animés.

## Conclusion

Le Splash Screen Android reprend maintenant les couleurs officielles de l’application Web tout en
préservant intégralement son cycle de chargement et les fonctionnalités existantes.
