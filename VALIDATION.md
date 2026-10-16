# Correctif Options 1.2.1

Mise en page uniquement : descriptions sous les boutons et largeur complète. Vérification Chromium à 320/393/420/840 px, taille de texte normale et doublée : aucun débordement horizontal, descriptions non comprimées. Aucun changement au calcul, aux données ou à la confirmation native d’effacement. APK 1.2.1 non généré : récupération de la clé de signature nécessaire.

Les résultats ci-dessous concernent la version 1.2.0 précédente.

# Validation KiloM 1.2.0 — 16 octobre 2026

## Fonctionnement et interface

- 100 contrôles DOM réussis : profil, trajets, persistance simulée, historique, exports, thèmes, Maps/Waze, sélection de sortie et groupes terminés.
- 61 contrôles d’optimisation et de réorganisation réussis.
- 165 assertions Java sur le moteur et le modèle Excel réussies.
- Scénarios dans Chromium : création du profil, aller-retour, circuit, détour, modification, export, partage, cinq pages aux largeurs 320/393/840, quatre thèmes, erreurs de stockage/export persistantes. Aucune exception JavaScript.
- Inspection des captures : surfaces unies, icônes Material Symbols, résumé rose et fond OLED corrects. Les captures sont des vérifications internes, avec données de test.
- Moteur Java Excel, grille de distances et modèle XLSX identiques octet pour octet à ceux de la version 1.1.9 sauvegardée. La refonte ne change pas les calculs.

## Portée et limites

La passerelle Android est simulée dans les tests de navigateur. Aucun téléphone Samsung ni émulateur Android n’a été utilisé pour exécuter cette version. Il reste à vérifier sur appareil : migration sans perte de données, WebView réelle, boutons retour et gestes système, sélecteur de documents, notifications refusées/autorisées, redémarrage, rappels et ouverture des applications de navigation.

Ces contrôles ne constituent ni une certification de sécurité ni une approbation Play Protect, Samsung ou Google Play. Le livrable ne contourne aucune protection d’installation. Les règles de signature et de publication sont décrites dans RELEASE.md.

## Release API 35

- `assembleRelease`, `bundleRelease` et `lintRelease` : compilation réussie.
- APK signé en schémas v2 et v3, même certificat SHA-256 que la 1.1.9 : `83b3cbe1329d6a213520edd241232fb6f0c93015f56e4f46c983305c99b3be6c`.
- Manifeste de l’APK : minSdk 26, targetSdk 35, versionCode 15, versionName 1.2.0, non débogable.
- Deux permissions Android demandées : notifications et réception du redémarrage. Une permission interne AndroidX de niveau signature est également déclarée.
- R8 et réduction des ressources exécutés ; les 14 méthodes JavaScript du pont natif conservent leur nom. Rapports et tables de correspondance dans `release-reports/api35/`.
- Les ressources UI incluses dans l’APK correspondent exactement aux fichiers sources testés.
- Bundle API 35 validé par bundletool et signature JAR vérifiée.

Le lint API 35 signale **0 erreur et 1 avertissement** : JavaScript est activé pour l’interface WebView locale. Ce besoin est intentionnel ; aucune page distante n’est admise, les entrées affichées sont échappées, une CSP restreint les ressources, le débogage et les accès aux fichiers sont désactivés. L’avertissement reste visible dans le rapport ; il n’est pas masqué par une suppression globale.

## Release API 36 pour Play

- `bundleRelease -PkilomTargetSdk=36` et `lintRelease` réussis.
- AAB validé par bundletool, signature JAR valide, minSdk 26 et targetSdk 36 vérifiés dans le manifeste du bundle.
- Lint : 0 erreur, le même avertissement JavaScript intentionnel.
- Les tables R8 propres à ce bundle sont dans `release-reports/api36/`.
- Vérification de la protection du build : la tâche release refuse l’absence des paramètres de signature externes.

Les archives sources ne contiennent aucun keystore ni ancien mot de passe de signature. Pour conserver la compatibilité avec les installations existantes, l’APK fourni reste signé avec le certificat historique ; la gestion et la migration de cette clé sont expliquées dans RELEASE.md.
