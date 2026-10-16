# KiloM 1.2.0 — signature, compilation et distribution

Le projet s’ouvre dans Android Studio avec **File → Open → dossier KiloM**. Les fichiers Gradle et le manifeste fournis sont complets. Le build release utilise R8, la réduction des ressources et une signature obligatoire. Aucun keystore ni mot de passe n’est inclus dans cette nouvelle archive.

## Fichiers livrés

`KiloM-1.2.0.apk` cible API 35 ; `KiloM-1.2.0-Play-API36.aab` cible API 36. Ils portent le même identifiant et le même certificat historique que KiloM 1.1.9. Le projet complet est fourni séparément, sans clé privée. Si votre fiche Play Console utilise une autre clé d’importation, recompiler l’AAB avec cette clé avant de le téléverser. Aucune publication n’a été effectuée.

## 1. Préparer Android Studio

Installer un Android Studio compatible avec Android Gradle Plugin 8.9.2. Dans Settings → Build Tools → Gradle, sélectionner un **JDK 17**. Le wrapper du projet télécharge Gradle **8.11.1** avec vérification SHA-256. Ne pas remplacer ces versions séparément sans vérifier leur compatibilité.

Si `keytool` ou `java` est introuvable dans PowerShell, ajouter le dossier `bin` de votre JDK 17 au PATH, ou appeler l’exécutable avec son chemin complet. Vérifier avec `java -version`.

Dans Tools → SDK Manager, installer Android SDK Platform **35**, Android SDK Build-Tools **35.0.0** et Android SDK Platform-Tools. Installer également Platform **36** pour le bundle Play décrit plus bas. Android Studio crée `local.properties` avec le chemin local du SDK ; ne pas partager ce fichier.

Configuration par défaut : minSdk 26 (Android 8), targetSdk 35, compileSdk 35, versionName 1.2.0, versionCode 15, applicationId `ca.m4tdoa.kilom`. Augmenter versionCode à chaque future soumission Play.

## 2. Choisir la bonne clé

**Mise à jour d’une installation existante : conserver la clé qui a signé cette installation.** Un nouveau certificat, même avec le même nom de fichier ou le même alias, ne permet pas de mettre à jour une ancienne application. Une désinstallation supprimerait les données locales : exporter les Excel avant toute opération de ce type.

L’ancienne archive privée de KiloM contenait `signing/kilom-local.p12` et son ancien mot de passe dans `build.sh`. Récupérer cette clé hors du projet et changer son mot de passe. Le nouveau projet ne contient plus ce secret. Le changement de mot de passe ne change pas le certificat et ne révoque pas les anciennes copies de la clé ; une clé déjà diffusée doit être considérée comme exposée. Pour une distribution organisée, utiliser Play App Signing et une clé d’importation séparée, puis prévoir la migration de signature avec Play si nécessaire.

Exemple PowerShell pour changer le mot de passe d’une clé existante, en répondant aux invites interactives :

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\KiloM-keys"
# Copier votre ancienne clé dans ce dossier avant la commande suivante.
keytool -storepasswd -keystore "$env:USERPROFILE\KiloM-keys\kilom-local.p12"
```

Pour connaître l’alias et le certificat, sans placer le mot de passe dans la commande :

```powershell
keytool -list -v -keystore "$env:USERPROFILE\KiloM-keys\kilom-local.p12"
```

**Nouvelle clé d’importation Play**, pour un nouveau circuit de distribution :

```powershell
keytool -genkeypair -v -keystore "$env:USERPROFILE\KiloM-keys\kilom-upload.p12" -storetype PKCS12 -alias kilom-upload -keyalg RSA -keysize 3072 -validity 10000
```

Choisir un mot de passe long et unique dans un gestionnaire de mots de passe. Saisir l’identité réelle du propriétaire de la clé ; ne pas déclarer le centre scolaire propriétaire sans son accord. Garder deux sauvegardes chiffrées du keystore, séparées du mot de passe. Ne jamais le committer, l’inclure dans le ZIP public ou l’envoyer à des collègues avec l’APK.

## 3. Fournir la signature au build

Ouvrir PowerShell dans le dossier contenant `gradlew.bat`. Adapter le chemin et l’alias à la clé réellement choisie. Les valeurs restent dans l’environnement de ce terminal ; elles ne sont pas écrites dans le projet.

```powershell
$env:KILOM_KEYSTORE = "$env:USERPROFILE\KiloM-keys\kilom-upload.p12"
$env:KILOM_KEY_ALIAS = "kilom-upload"
$kilomPassword = Read-Host "Mot de passe du keystore" -AsSecureString
$kilomPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($kilomPassword)
try {
    $env:KILOM_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($kilomPointer)
    $env:KILOM_KEY_PASSWORD = $env:KILOM_STORE_PASSWORD
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($kilomPointer)
    $kilomPassword.Dispose()
}
```

Pour la clé historique, remplacer le chemin par celui de `kilom-local.p12` et l’alias par celui affiché par `keytool -list`. Si le mot de passe de la clé est différent de celui du magasin, saisir `KILOM_KEY_PASSWORD` séparément avec la même méthode interactive.

Alternativement, ces quatre propriétés peuvent être placées dans le fichier Gradle **personnel** `~/.gradle/gradle.properties`, protégé par les permissions du compte. Elles ne doivent jamais être ajoutées à `KiloM/gradle.properties`.

## 4. Produire l’APK et l’AAB signés, cible 35

```powershell
.\gradlew.bat --no-daemon clean :app:assembleRelease :app:bundleRelease
```

Sur macOS/Linux, utiliser `./gradlew` et fournir les mêmes variables dans le terminal, avec saisie silencieuse du mot de passe. Le script `./build.sh` appelle exactement les tâches release Gradle ; il ne génère aucune clé et n’utilise aucun fallback debug.

Résultats :

- `app/build/outputs/apk/release/app-release.apk` : APK signé installable.
- `app/build/outputs/bundle/release/app-release.aab` : bundle signé pour distribution par une boutique, pas installable directement.
- `app/build/outputs/mapping/release/mapping.txt` : table R8 à conserver pour analyser les crashs de cette version.
- `app/build/outputs/mapping/release/configuration.txt` : configuration R8 effective.

La compilation release échoue si la signature est absente. Le variant debug existe pour le développement, porte le suffixe `.debug`, et n’est pas le livrable release. Les méthodes du pont JavaScript sont explicitement conservées dans `app/proguard-rules.pro` pour que R8 ne casse pas les interactions.

Vérifier l’APK avec l’outil du SDK (adapter le chemin si le SDK est ailleurs) :

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
jarsigner -verify app\build\outputs\bundle\release\app-release.aab
```

Un certificat autosigné dans un keystore Android est normal. Cela ne signifie pas que l’APK est non signé. Vérifier le certificat SHA-256 et le comparer à celui de l’APK précédent avant une mise à jour.

## 5. Bundle pour Google Play : cible 36

Au **16 octobre 2026**, la règle générale de Google Play demande API 36 pour les nouvelles apps et mises à jour depuis le 31 août 2026. La demande initiale targetSdk 35 reste la valeur par défaut du projet. Pour une soumission actuelle, installer Platform 36 puis compiler :

```powershell
.\gradlew.bat --no-daemon clean :app:bundleRelease -PkilomTargetSdk=36
```

Le résultat reste `app/build/outputs/bundle/release/app-release.aab`, cette fois avec cible 36. Cette commande change compileSdk et targetSdk ensemble ; minSdk reste 26. Une éventuelle prolongation ou exception pour une application privée d’organisation doit être vérifiée dans votre propre Play Console. Ne pas supposer que le test interne autorise une cible obsolète.

## 6. Test interne Google Play

1. Dans votre compte Play Console, créer KiloM ou ouvrir sa fiche existante. Le package doit correspondre à `ca.m4tdoa.kilom` ; si ce package est déjà enregistré dans un autre compte, il faut son propriétaire ou une migration.
2. Configurer **Play App Signing**. Distinguer la clé de signature d’application (celle des APK installés par Play) et la clé d’importation (celle qui signe l’AAB téléversé). Pour conserver la mise à jour des installations hors Play, décider de la compatibilité du certificat **avant** le premier déploiement. Ne pas accepter une nouvelle identité de signature sans évaluer cette migration.
3. Aller dans **Tests → Test interne → Créer une version**. Téléverser l’AAB cible 36 signé par la clé d’importation enregistrée. Une mise à jour doit avoir un versionCode supérieur à la précédente.
4. Remplir les champs demandés : fiche, contenu, sécurité des données et politique de confidentialité. Décrire le stockage local des noms, matricules, numéros de fournisseur et trajets ; le partage d’Excel est déclenché par l’utilisateur. Les requêtes de lieu envoyées à Google Maps ou Waze sont traitées par ces services externes.
5. Ajouter votre liste de testeurs, vérifier les alertes Play Console et publier dans le canal interne. Leur transmettre ensuite le lien d’inscription au test. Ils installent depuis Google Play avec un compte autorisé.
6. Tester la migration depuis l’ancienne version, l’enregistrement d’une sortie, les quatre thèmes, les permissions refusées, le redémarrage, les exports et l’ouverture de Maps/Waze sur les appareils réellement utilisés.

Le **partage interne d’applications** et le **canal de test interne** ne sont pas identiques : le premier peut re-signer les APK. Utiliser le canal de test interne pour valider votre distribution Play et sa signature. La publication et l’invitation des testeurs restent à effectuer depuis votre compte ; aucun téléversement Play n’est réalisé par ce projet.

Après compilation, retirer les secrets du terminal :

```powershell
Remove-Item Env:KILOM_STORE_PASSWORD, Env:KILOM_KEY_PASSWORD, Env:KILOM_KEYSTORE, Env:KILOM_KEY_ALIAS
```

## 7. Permissions et protections

| Permission Android | Utilité dans KiloM |
| --- | --- |
| `POST_NOTIFICATIONS` | Alerte facultative lorsqu’un Excel est prêt, autorisation demandée sur Android 13+. Un refus n’empêche pas les exports. |
| `RECEIVE_BOOT_COMPLETED` | Reprogramme le rappel des périodes après redémarrage et mise à jour. Pas de permission d’alarme exacte : Android peut retarder le travail. |

AndroidX peut ajouter une permission **personnalisée de niveau signature** `ca.m4tdoa.kilom.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` pour protéger ses récepteurs internes. Ce n’est ni une permission de données personnelles ni une autorisation demandée à l’utilisateur. Le récepteur de profils de performance AndroidX est également protégé par la permission système `DUMP` ; KiloM ne demande pas cette permission. Le manifeste fusionné du livrable est contrôlé lors de la validation.

KiloM ne demande pas INTERNET, localisation, accès global au stockage, SMS, journal d’appels, installation de packages ou services d’accessibilité. Les liens Maps/Waze sont ouverts dans l’app choisie ou le navigateur via un intent ; cette app externe utilise ses propres permissions.

Les fichiers sont dans le stockage privé Android. La sauvegarde automatique et le transfert des données de l’app vers un autre appareil sont explicitement exclus. L’export utilise le sélecteur de document système, et le partage un fournisseur non exporté avec autorisation temporaire en lecture. Les données restent protégées par l’isolation Android et le verrouillage de l’appareil ; ce n’est pas un coffre doté d’un mot de passe séparé. Les Excel enregistrés ailleurs ne sont plus sous le contrôle de KiloM.

La WebView charge seulement les ressources locales, sans débogage distant, cookies, accès aux fichiers, contenu mixte ou navigation arbitraire. Une politique CSP restreint les ressources ; les gestionnaires HTML existants nécessitent encore `unsafe-inline` pour les scripts. Aucune page distante n’est chargée dans cette WebView. Aucun secret ou SDK publicitaire/analytique n’est ajouté.

## 8. Android, Play Protect et Samsung

Une signature release valide et des permissions limitées ne garantissent pas l’absence d’avertissement. **Auto Blocker de Samsung peut bloquer un APK provenant d’une source non autorisée**, même signé. Un message Play Protect, un blocage Auto Blocker, une politique d’entreprise et une incompatibilité de certificat sont des causes différentes.

Distribuer à vos collègues via Google Play interne ou la solution approuvée par leur organisation. Ne pas désactiver Play Protect ou contourner la gestion d’entreprise. Si un blocage demeure, conserver le texte exact du message et le modèle/version Android pour le diagnostiquer. Ce projet ne prétend pas avoir été approuvé par Google, Samsung ou le centre scolaire.

## Références officielles

- Signature : https://developer.android.com/studio/publish/app-signing
- Ligne de commande : https://developer.android.com/build/building-cmdline
- Optimisation R8 : https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- Cible Play : https://support.google.com/googleplay/android-developer/answer/11926878
- Compatibilité Gradle : https://developer.android.com/build/releases/about-agp
- Samsung Auto Blocker : https://www.samsung.com/ca/support/mobile-devices/protect-your-samsung-galaxy-with-auto-blocker/
