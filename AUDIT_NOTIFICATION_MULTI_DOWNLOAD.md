# Audit — notifications de téléchargements multiples

## Objectif

Empêcher une nouvelle notification de téléchargement terminé de remplacer la précédente, tout en conservant le comportement existant du téléchargement et de l'ouverture du fichier.

## Périmètre analysé

- `app/src/main/java/com/netk/app/MainActivity.kt`
- le bridge JavaScript `AndroidDownloads.saveFile()` ;
- le flux `handleExport()` → `saveExportAsync()` → `showDownloadNotification()` ;
- la création du canal, de la notification et du `PendingIntent` d'ouverture du fichier.

## Cause identifiée

`showDownloadNotification()` utilisait la constante `DOWNLOAD_NOTIFICATION_ID = 1` pour toutes les notifications. Android considère qu'un nouvel appel à `NotificationManager.notify()` avec le même identifiant met à jour la notification existante : la notification du dernier téléchargement remplaçait donc la précédente.

Le même identifiant fixe servait aussi de code de requête au `PendingIntent`. Avec `FLAG_UPDATE_CURRENT`, un nouveau téléchargement pouvait alors mettre à jour l'intention associée aux notifications précédentes et leur faire ouvrir le dernier fichier téléchargé.

## Modification réalisée

La fonction `showDownloadNotification()` génère désormais un identifiant pour chaque notification :

```kotlin
val notificationId = System.currentTimeMillis().toInt()
```

Cet identifiant est utilisé à la fois :

- comme code de requête du `PendingIntent`, afin que chaque notification conserve l'URI de son propre fichier ;
- dans `notificationManager.notify(notificationId, notification)`, afin que les notifications coexistent.

Les traces demandées ont été ajoutées :

```text
[DOWNLOAD_NOTIFICATION] notificationId: <identifiant>
[DOWNLOAD_NOTIFICATION] fileName: <nom du fichier>
```

## Éléments conservés

- le canal existant `downloads` et sa création pour Android 8+ ;
- `setSmallIcon(android.R.drawable.stat_sys_download_done)` ;
- le `PendingIntent` ouvrant l'URI du fichier ;
- les flux d'enregistrement MediaStore et legacy ;
- la logique de téléchargement WebView/DownloadManager ;
- le bridge `AndroidDownloads.saveFile()` ;
- le traitement commun des exports Excel et `.su`.

## Vérifications

| Scénario | Résultat attendu |
| --- | --- |
| Télécharger le fichier A | Une notification A apparaît. |
| Télécharger ensuite le fichier B | Une notification B apparaît sans remplacer A. |
| Appuyer sur chaque notification | A ouvre l'URI de A et B ouvre l'URI de B grâce aux codes de requête distincts. |
| Exporter un fichier Excel puis un fichier `.su` | Les deux formats empruntent toujours le même bridge et le même flux d'enregistrement, sans modification de leur contenu ni de leur type. |

La validation fonctionnelle finale de l'affichage et de l'ouverture des notifications doit être effectuée sur un appareil ou un émulateur Android autorisant les notifications.
