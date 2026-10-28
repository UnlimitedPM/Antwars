# AntWars

AntWars est un mini-jeu Minecraft de type **bed-rush souterrain** (la « version 1 »,
variante de BedWars) : les équipes creusent la terre, dépensent la terre minée dans
une boutique, protègent leur lit et tentent de faire sauter celui des autres à la TNT.
Tant que le lit d'une équipe tient, ses joueurs réapparaissent ; une fois détruit,
chaque mort est définitive. La dernière équipe en vie gagne.

Ce dépôt contient le plugin, la map d'origine (deux terrains) et un serveur de
test Paper 1.21.8 prêt à lancer, avec file d'attente automatique et quatre modes
de jeu : `2x2`, `4x4`, `2x2x2x2` et `4x4x4x4`.

## Origine et licence

Ce projet est un **clone du plugin open source AntWars de xLaugh**, publié sous
licence **GPL-3.0** :

- Dépôt d'origine : <https://github.com/xLaugh/AntWars>
- Page SpigotMC : <https://www.spigotmc.org/resources/antwars-1-8-26-3.139714/>
- Map fournie par l'auteur dans les releases du dépôt d'origine.

Conformément à la GPL-3.0, la licence et l'attribution sont conservées. Les
modifications locales sont signalées ici (GPL-3.0, section 5a) :

- **2026-10-28** : ajout de `compat/BedColors` (les lits d'équipe gardent leur
  couleur sur les serveurs modernes après conversion d'une map 1.8), du serveur
  de test Paper 1.21.8, de la configuration des quatre modes et des scripts de
  build/test.
- **2026-10-28 (correctifs)** : file d'attente automatique (`queue/AutoQueue`),
  spawn dans la salle en verre, modes `2x2` / `4x4` / `2x2x2x2` / `4x4x4x4`,
  centre de bordure par arène, répartition en coins opposés à 2 joueurs,
  liaison automatique aux lits, réapparition près du lit après reconnexion,
  victoire immédiate en 1v1 sur déconnexion, correction de la duplication du
  lit et ouverture de la boutique au clic droit.

Si tu veux une réécriture 100 % originale (sans code de xLaugh), c'est un autre
chantier : il faudra réécrire le plugin de zéro.

## Contenu du dépôt

| Chemin | Rôle |
|---|---|
| `src/main/java/fr/laugh/antwars/` | Code du plugin (Java 8, compilé contre l'API Spigot 1.8.8). |
| `src/main/resources/` | `plugin.yml`, `config.yml`, `arenes.yml`, traductions (`lang/fr.yml` par défaut). |
| `antwars-map/` | Map AntWars d'origine (format 1.8, convertie automatiquement par Paper). |
| `test-server/` | Modèles de configuration du serveur de test (`server.properties`, `arenas.yml`). |
| `scripts/setup-test-server.ps1` | Build + génération du dossier `server/` (Paper, map, plugin, configs). |
| `scripts/patch_config.py` | Applique le lobby et le compte à rebours de test dans `config.yml`. |
| `server/` | Serveur généré (ignoré par git). |

## Prérequis

- **JDK 21** (Paper 1.21.8 ne démarre pas avec Java 8) ;
- **Maven 3.9+** ;
- **Python 3** (uniquement pour patcher `config.yml` au premier lancement) ;
- PowerShell (Windows).

Le dossier `tools/` (ignoré par git) contient les copies locales utilisées pendant
le développement :

- `tools/jdk/jdk-21.0.12.1+1` ;
- `tools/mvn/apache-maven-3.9.9`.

Si tu repars d'une machine vierge, télécharge un JDK 21 (Temurin) et Maven, puis
place-les dans `tools/jdk` et `tools/mvn`, ou adapte les chemins dans
`scripts/setup-test-server.ps1`.

## Démarrage rapide

```powershell
# 1. Construire le plugin et générer le serveur de test
powershell -ExecutionPolicy Bypass -File scripts\setup-test-server.ps1

# 2. Lancer le serveur
powershell -ExecutionPolicy Bypass -File server\start.ps1

# 3. Dans Minecraft : Multijoueur -> localhost:25565 (serveur hors-ligne)
#    Tu apparais dans la salle en verre et tu es automatiquement mis en file.
#    Aucune commande n'est nécessaire pour le mode par défaut (2x2).
```

Pour choisir un mode précis, en jeu :

```text
/aw join 2x2        # 2 équipes de 2 (max 4)
/aw join 4x4        # 2 équipes de 4 (max 8)
/aw join 2x2x2x2    # 4 équipes de 2 (max 8)
/aw join 4x4x4x4    # 4 équipes de 4 (max 16)
```

Le serveur est en **mode hors-ligne** (`online-mode=false`) : n'importe quel
pseudo fonctionne, aucun compte Mojang n'est vérifié. Pour passer en production,
repasse `online-mode=true` et n'ouvre pas le port sans protection.

La map contient deux terrains :

- **2 équipes** : Rouge `(-43, 156)` (sol rose/rouge) et Bleu `(-124, 74)`
  (sol bleu) ;
- **4 équipes** : Rouge `(0, 100)` (sol rose/rouge), Bleu `(0, 0)` (sol bleu),
  Jaune `(100, 100)` (sol jaune), Vert `(100, 0)` (sol vert).

Le lobby/spawn est la salle en verre noire au nord-ouest (`-143, 50, -46`).
Le centre de la bordure de deathmatch est configuré par arène (`borderCenter`),
car le lobby est volontairement loin des terrains.

Les arènes sont configurées dans `test-server/arenas.yml` (copié vers
`server/plugins/AntWars/arenes.yml`) :

| Mode | Équipes | Joueurs/équipe | Max | Terrain |
|---|---:|---:|---:|---|
| `2x2` | 2 | 2 | 4 | 2 équipes |
| `4x4` | 2 | 4 | 8 | 2 équipes |
| `2x2x2x2` | 4 | 2 | 8 | 4 équipes |
| `4x4x4x4` | 4 | 4 | 16 | 4 équipes |

La file automatique choisit le premier mode assez grand pour le nombre de
joueurs connectés : 2 à 4 joueurs → `2x2`, 5 à 8 → `2x2x2x2`, 9 à 16 →
`4x4x4x4`. Un `/aw join <mode>` manuel force le mode voulu.

## Commandes utiles

Joueurs :

```text
/aw join <arene>        Rejoindre une arène
/aw leave               Quitter
/aw list                Lister les arènes
/aw stats [joueur]      Statistiques
/aw top <stat>          Classement (wins, kills, rating, ...)
/aw spectate <arene>    Regarder une partie (si autorisé)
/aw quickbuy            Personnaliser l'achat rapide
/aw chat <team|all>     Choisir le chat par défaut
```

Administration (`antwars.admin`, op par défaut) :

```text
/aw setlobby                         Définir le lobby serveur
/aw create <arene>                   Créer une arène
/aw setarenalobby <arene>            Définir le lobby d'attente
/aw setspawn <arene> <equipe>        Définir le spawn d'une équipe
/aw setbed <arene> <equipe>          Enregistrer un lit (debout sur le lit)
/aw setteams <arene> <2-4>           Nombre d'équipes
/aw setplayersperteam <arene> <n>    Joueurs par équipe
/aw setminplayers / setmaxplayers    Bornes de joueurs
/aw start <arene> / /aw stop         Lancer / arrêter une partie
/aw save <arene> / /aw info <arene>  Sauvegarder / inspecter
/aw reload                           Recharger la configuration
```

Les commandes d'administration doivent être exécutées **en jeu** (pas depuis la
console). Pense à te donner les droits avec `op <pseudo>` dans la console.

## Ce qui a été vérifié

Tests effectués le 2026-10-28 avec Paper 1.21.8 build 60 et des clients
headless (2 et 4 joueurs) :

- le plugin démarre, charge la langue française et les **quatre arènes**
  (`/aw list`) ;
- un joueur qui se connecte apparaît dans la salle en verre
  `(-142.5, 50, -45.5)` et est **automatiquement mis en file** ;
- à 2 joueurs, le mode `2x2` démarre sur le terrain 2 équipes, avec Rouge et
  Bleu dans des bases opposées ;
- à 4 joueurs, le mode `2x2` fait un 2v2 ; la file choisit un mode plus grand
  quand le nombre de joueurs dépasse sa capacité ;
- un joueur tué **réapparaît à côté de son lit sans clic droit préalable**
  (liaison automatique) ;
- une déconnexion en 2v2 laisse la place ouverte : le joueur qui se reconnecte
  réapparaît à côté de son lit ;
- une déconnexion en 1v1 termine immédiatement la partie au profit du joueur
  encore présent ;
- casser son propre lit donne **un seul** lit dans l'inventaire, sans item
  supplémentaire au sol (duplication corrigée) ;
- le NPC de boutique s'ouvre au **clic droit** (fenêtre 9x5 testée) ;
- un bloc miné est restauré en fin de manche ; la map est également nettoyée
  avant le lancement si un suivi résiduel existe ;
- les statistiques locales sont écrites dans `server/plugins/AntWars/stats.yml` ;
- les lits prennent la couleur de l'équipe (rouge, bleu, jaune, vert) ;
- arrêt propre du serveur, sauvegarde comprise.

Le mode `2x2` a été testé en jeu ; les trois autres modes partagent le même
code et les mêmes positions de map, mais n'ont pas été joués avec 8/16 clients.

## Notes et limites

- **Avertissement XSeries** au démarrage : `NoSuchFieldException ... ENDER_PEARL_TICKET_RADIUS`.
  C'est une réflexion interne de XSeries 9.10.0 sur 1.21.8 ; l'erreur est
  interceptée et n'empêche pas le plugin de fonctionner (testé). On peut la faire
  disparaître plus tard en montant XSeries, mais il faut conserver le bytecode
  Java 8 pour ne pas casser la compatibilité 1.8-1.16.
- **Compatibilité** : le jar est compilé en bytecode Java 8 contre l'API Spigot
  1.8.8, ce qui couvre 1.8 → 1.21. Seul **1.21.8** a été testé ici. Les versions
  1.8-1.20 restent à valider si tu en as besoin.
- **Un seul jeu à la fois par monde** : les terrains 2 et 4 équipes sont dans le
  même monde, et la bordure de deathmatch est globale au monde. La file évite de
  lancer deux arènes simultanément ; pour du multi-arènes en parallèle, il faut
  un monde par arène (Multiverse) ou un réseau de serveurs.
- Le lobby en verre est loin des terrains : pendant un deathmatch, un joueur qui
  attend au lobby (sans être dans la partie) peut être hors de la bordure. Pour
  un serveur public, utilise un HUB/proxy dans un autre monde. Pour le serveur
  de test, la file place normalement tout le monde dans la partie.
- Les boutiques sont des **armor stands** par défaut. Avec Citizens installé et
  `shop-npc.type: citizens` dans `config.yml`, le plugin utilise de vrais NPC
  joueurs ; sans Citizens, aucun paquet NMS n'est nécessaire.
- `/aw leave` sort un joueur de la file automatiquement ; il y revient à sa
  reconnexion ou avec `/aw join <mode>`.
- **Pas de proxy** (BungeeCord/Velocity) ni de MySQL dans cette configuration :
  serveur unique, statistiques en YAML. Les options existent dans `config.yml`.
- La map d'origine est en format 1.8 ; Paper la convertit au premier démarrage.
  Ne supprime pas `server/world` si tu veux garder les modifications et les stats.
- `scripts/setup-test-server.ps1` ne remplace plus `config.yml`/`arenes.yml`
  s'ils existent déjà : tes réglages en jeu sont conservés.

## Licence

GPL-3.0 — voir `LICENSE`. Copyright du projet d'origine : xLaugh.
