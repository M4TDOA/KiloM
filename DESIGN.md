# KiloM — interface Material 3

La couleur laissée en exemple dans la demande, **#00549F**, sert de couleur principale personnalisée. Il n’y a ni dégradé ni flou de verre. Les quatre modes clair, sombre, rose et OLED restent accessibles dans Profil → Options. Le mode rose recolore aussi le résumé des kilomètres ; le mode OLED utilise un fond noir.

Le projet conserve son moteur local Java et son interface WebView hors ligne pour préserver les données et l’export Excel. Les boîtes de dialogue natives utilisent Material Components 1.12.0 et les thèmes Material 3. L’interface principale applique les rôles et dimensions Material 3 en CSS ; elle n’est pas une conversion vers Jetpack Compose.

## Système visuel

| Élément | Choix |
| --- | --- |
| Police | Roboto, embarquée, aucune police distante |
| Titres | 28/36, 22/28, poids 500 |
| Corps et libellés | 16/24 et 14/20 ; navigation 12/16 |
| Espacements | 8, 16, 24, 32 dp (px CSS dans la WebView à viewport natif) |
| Cartes | Rayon 16, surfaces unies, contour fin |
| Champs | Hauteur minimale 56, rayon 8 |
| Boutons | Cible minimale 48, rayon 24, variantes rempli/tonal/contour |
| Navigation | Barre fixe de 80, indicateur tonal et libellés visibles |
| Icônes d’interface | Material Symbols Outlined, SVG locaux 24, Apache-2.0 |
| Marque | Icône adaptative vectorielle K + compteur, version monochrome Android 13 |
| Mouvements | 200 ms, arrêt des animations si mouvement réduit |

Les quelques valeurs non multiples de 8 correspondent aux traits, aux tailles typographiques, aux dimensions prescrites des symboles et aux compensations de bordure ; elles ne créent pas de marges de mise en page arbitraires.

Le démarrage utilise le splash système sur Android 12+ et un fond de lancement local sur les versions précédentes. Le chargement affiche des squelettes, les listes vides indiquent leur état, et les erreurs d’ouverture/export/enregistrement restent visibles avec une action de reprise appropriée. Le pied de page est limité à « KiloM • Mathias Dassylva ».

Les noms, matricules, trajets et résultats kilométriques ne sont pas modifiés par le changement de thème. L’icône KiloM ne constitue pas une marque officielle du centre scolaire. Les licences Roboto et Material Symbols sont dans `licenses/`.
