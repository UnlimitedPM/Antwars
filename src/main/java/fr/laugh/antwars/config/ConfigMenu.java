package fr.laugh.antwars.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.lang.Messages;

/**
 * Menu graphique de configuration des arenes : liste, edition d'une arene,
 * choix du format, reglages d'equipe et confirmation de suppression.
 *
 * <p>Meme socle que la boutique : inventaires marques par un {@link ConfigHolder}
 * (jamais reconnus a leur titre), icones construites via {@link AWMaterial} (donc
 * valables de la 1.8 a la 1.21), aucun appel a une methode d'InventoryView. Les
 * positions dans le monde (spawn, lit, lobby, NPC) ne se reglent pas dans le
 * menu mais en "mode pose" (voir {@link Placements}) : cliquer le bouton ferme le
 * menu et l'admin pose en marchant.
 */
public class ConfigMenu {

    // ---- Tailles ----
    private static final int LIST_SIZE = 54;
    private static final int EDIT_SIZE = 54;
    private static final int SMALL_SIZE = 27;

    // ---- Slots du menu d'edition ----
    // Reglages sur la 3e ligne (slots 19-26), spectateurs en dessous, puis les equipes.
    private static final int E_BACK = 0;
    private static final int E_INFO = 4;
    private static final int E_TEAMCOUNT = 19;
    private static final int E_PERTEAM = 20;
    private static final int E_MIN = 21;
    private static final int E_MAX = 22;
    private static final int E_LOBBY = 23;
    private static final int E_NPC = 24;
    private static final int E_REGEN = 25;
    private static final int E_BORDERTIME = 26;
    private static final int E_SPECTATORS = 31;
    private static final int[] E_TEAMS = { 37, 39, 41, 43 };
    private static final int E_DELETE = 45;
    private static final int E_SAVE = 49;

    // ---- Slots du menu d'equipe ----
    private static final int T_BACK = 0;
    private static final int T_INFO = 4;
    private static final int T_SPAWN = 11;
    private static final int T_BED = 15;

    // ---- Slots du menu de suppression ----
    private static final int D_INFO = 4;
    private static final int D_CONFIRM = 11;
    private static final int D_CANCEL = 15;

    private final Main main;

    public ConfigMenu(Main main) {
        this.main = main;
    }

    // ================= Ouverture des menus =================

    public void openList(Player player) {
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.LIST, null, null);
        Inventory inv = Bukkit.createInventory(holder, LIST_SIZE, Messages.tr("config.title.list"));
        holder.setInventory(inv);
        fill(inv);

        List<Arena> arenas = arenas();
        for (int i = 0; i < arenas.size() && i < 45; i++) {
            Arena arena = arenas.get(i);
            boolean ready = arena.isConfigured();
            String name = (ready ? ChatColor.GREEN : ChatColor.RED) + arena.getName();
            List<String> lore = new ArrayList<String>();
            lore.add(Messages.tr("config.list.state", "state", arena.getState().name()));
            lore.add(Messages.tr("config.list.players",
                    "current", arena.getPlayerCount(), "max", arena.getMaxPlayers()));
            if (ready) {
                lore.add(Messages.tr("config.list.ready"));
            } else {
                lore.add(Messages.tr("config.list.incomplete", "problem", arena.validate()));
            }
            lore.add(Messages.tr("config.list.click_edit"));
            inv.setItem(i, icon(AWMaterial.RED_BED, name, lore));
        }

        inv.setItem(53, icon(AWMaterial.PAPER, Messages.tr("config.list.create_name"),
                one(Messages.tr("config.list.create_lore"))));

        player.openInventory(inv);
    }

    public void openEdit(Player player, String arenaName) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.EDIT, arena.getName(), null);
        Inventory inv = Bukkit.createInventory(holder, EDIT_SIZE, Messages.tr("config.title.edit"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(E_BACK, icon(AWMaterial.ARROW, Messages.tr("config.edit.back"), null));

        String problem = arena.validate();
        List<String> infoLore = new ArrayList<String>();
        infoLore.add(problem == null
                ? Messages.tr("config.edit.info_ready")
                : Messages.tr("config.edit.info_incomplete", "problem", problem));
        inv.setItem(E_INFO, icon(AWMaterial.RED_BED,
                Messages.tr("config.edit.info_name", "arena", arena.getName()), infoLore));

        // Nombre d'equipes (2 a 4) : clic gauche +1, clic droit -1.
        inv.setItem(E_TEAMCOUNT, icon(AWMaterial.PAPER,
                Messages.tr("config.edit.teamcount_name", "value", arena.getTeamCount()),
                one(Messages.tr("config.edit.plus_minus"))));

        // Joueurs par equipe / min / max : + a gauche, - a droite.
        inv.setItem(E_PERTEAM, icon(AWMaterial.IRON_SWORD,
                Messages.tr("config.edit.perteam_name", "value", arena.getPlayersPerTeam()),
                one(Messages.tr("config.edit.plus_minus"))));
        inv.setItem(E_MIN, icon(AWMaterial.LEATHER_BOOTS,
                Messages.tr("config.edit.min_name", "value", arena.getMinPlayers()),
                one(Messages.tr("config.edit.plus_minus"))));
        inv.setItem(E_MAX, icon(AWMaterial.CHAINMAIL_CHESTPLATE,
                Messages.tr("config.edit.max_name", "value", arena.getMaxPlayers()),
                one(Messages.tr("config.edit.plus_minus"))));

        boolean lobbySet = arena.getLobby() != null;
        List<String> lobbyLore = new ArrayList<String>();
        lobbyLore.add(lobbySet ? Messages.tr("config.edit.lobby_set")
                : Messages.tr("config.edit.lobby_unset"));
        lobbyLore.add(Messages.tr("config.edit.lobby_click"));
        inv.setItem(E_LOBBY, icon(lobbySet ? AWMaterial.GREEN_STAINED_GLASS_PANE
                : AWMaterial.RED_STAINED_GLASS_PANE,
                Messages.tr("config.edit.lobby_name"), lobbyLore));

        List<String> npcLore = new ArrayList<String>();
        npcLore.add(Messages.tr("config.edit.npc_add"));
        npcLore.add(Messages.tr("config.edit.npc_clear"));
        inv.setItem(E_NPC, icon(AWMaterial.CHEST,
                Messages.tr("config.edit.npc_name", "count", arena.getShopNpcLocations().size()),
                npcLore));

        // Regeneration de la map (reglage global du serveur).
        boolean regen = main.isMapRegenerationEnabled();
        List<String> regenLore = new ArrayList<String>();
        regenLore.add(Messages.tr("config.edit.regen_lore"));
        regenLore.add(Messages.tr("config.edit.regen_click"));
        inv.setItem(E_REGEN, icon(AWMaterial.TNT, Messages.tr("config.edit.regen_name",
                "state", state(regen)), regenLore));

        // Temps des bordures, affiche en minutes (stocke en secondes).
        inv.setItem(E_BORDERTIME, icon(AWMaterial.CLOCK,
                Messages.tr("config.edit.bordertime_name", "value", arena.getBorderTime() / 60),
                one(Messages.tr("config.edit.plus_minus"))));

        // Spectateurs exterieurs (/aw spectate), autorises ou non sur cette arene.
        inv.setItem(E_SPECTATORS, icon(AWMaterial.COMPASS,
                Messages.tr("config.edit.spectators_name", "state", state(arena.isAllowSpectators())),
                one(Messages.tr("config.edit.spectators_lore"))));

        // Equipes : icone lit coloree si active, panneau gris sinon.
        for (int i = 0; i < E_TEAMS.length; i++) {
            String team = Arena.TEAM_NAMES[i];
            if (arena.isTeamActive(team)) {
                List<String> lore = new ArrayList<String>();
                lore.add(arena.getSpawnPoints().containsKey(team)
                        ? Messages.tr("config.edit.team_spawn_ok")
                        : Messages.tr("config.edit.team_spawn_no"));
                lore.add(arena.hasBed(team)
                        ? Messages.tr("config.edit.team_bed_ok")
                        : Messages.tr("config.edit.team_bed_no"));
                lore.add(Messages.tr("config.edit.team_click"));
                inv.setItem(E_TEAMS[i], icon(bedMaterial(team),
                        Arena.colorOf(team) + Messages.tr("config.edit.team_name", "team", team), lore));
            } else {
                inv.setItem(E_TEAMS[i], icon(AWMaterial.GRAY_STAINED_GLASS_PANE,
                        ChatColor.DARK_GRAY + team, one(Messages.tr("config.edit.team_inactive"))));
            }
        }

        inv.setItem(E_DELETE, icon(AWMaterial.BARRIER, Messages.tr("config.edit.delete_name"), null));
        inv.setItem(E_SAVE, icon(AWMaterial.NETHER_STAR, Messages.tr("config.edit.save_name"), null));

        player.openInventory(inv);
    }

    public void openTeam(Player player, String arenaName, String team) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.TEAM, arena.getName(), team);
        Inventory inv = Bukkit.createInventory(holder, SMALL_SIZE, Messages.tr("config.title.team"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(T_BACK, icon(AWMaterial.ARROW, Messages.tr("config.team.back"), null));
        inv.setItem(T_INFO, icon(bedMaterial(team),
                Arena.colorOf(team) + Messages.tr("config.team.info_name", "team", team), null));

        inv.setItem(T_SPAWN, icon(AWMaterial.NETHER_STAR, Messages.tr("config.team.spawn_name"),
                one(arena.getSpawnPoints().containsKey(team)
                        ? Messages.tr("config.edit.team_spawn_ok")
                        : Messages.tr("config.edit.team_spawn_no"))));

        List<String> bedLore = new ArrayList<String>();
        bedLore.add(arena.hasBed(team)
                ? Messages.tr("config.edit.team_bed_ok")
                : Messages.tr("config.edit.team_bed_no"));
        bedLore.add(Messages.tr("config.team.bed_hint"));
        inv.setItem(T_BED, icon(bedMaterial(team), Messages.tr("config.team.bed_name"), bedLore));

        player.openInventory(inv);
    }

    public void openDelete(Player player, String arenaName) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        ConfigHolder holder = new ConfigHolder(ConfigHolder.Type.DELETE, arena.getName(), null);
        Inventory inv = Bukkit.createInventory(holder, SMALL_SIZE, Messages.tr("config.title.delete"));
        holder.setInventory(inv);
        fill(inv);

        inv.setItem(D_INFO, icon(AWMaterial.BARRIER,
                Messages.tr("config.delete.info_name", "arena", arena.getName()),
                one(Messages.tr("config.delete.info_lore"))));
        inv.setItem(D_CONFIRM, icon(AWMaterial.GREEN_STAINED_GLASS_PANE,
                Messages.tr("config.delete.confirm"), null));
        inv.setItem(D_CANCEL, icon(AWMaterial.RED_STAINED_GLASS_PANE,
                Messages.tr("config.delete.cancel"), null));

        player.openInventory(inv);
    }

    // ================= Clics =================

    /**
     * Route un clic sur un menu de config. {@code rightClick} distingue le clic
     * droit (utilise pour decrementer les nombres et vider les NPC).
     */
    public void handleClick(Player player, ConfigHolder holder, int slot, boolean rightClick) {
        switch (holder.getType()) {
            case LIST:
                clickList(player, slot);
                break;
            case EDIT:
                clickEdit(player, holder.getArena(), slot, rightClick);
                break;
            case TEAM:
                clickTeam(player, holder.getArena(), holder.getTeam(), slot);
                break;
            case DELETE:
                clickDelete(player, holder.getArena(), slot);
                break;
            default:
                break;
        }
    }

    private void clickList(Player player, int slot) {
        if (slot == 53) {
            main.getArenaNaming().start(player);
            return;
        }
        List<Arena> arenas = arenas();
        if (slot >= 0 && slot < arenas.size() && slot < 45) {
            openEdit(player, arenas.get(slot).getName());
        }
    }

    private void clickEdit(Player player, String arenaName, int slot, boolean rightClick) {
        Arena arena = main.getArenaManager().getArena(arenaName);
        if (arena == null) {
            openList(player);
            return;
        }
        int delta = rightClick ? -1 : 1;
        switch (slot) {
            case E_BACK:
                openList(player);
                return;
            case E_TEAMCOUNT:
                arena.setTeamCount(clamp(arena.getTeamCount() + delta, 2, 4));
                normalize(arena);
                break;
            case E_PERTEAM:
                arena.setPlayersPerTeam(clamp(arena.getPlayersPerTeam() + delta, 1, 16));
                normalize(arena);
                break;
            case E_MIN:
                arena.setMinPlayers(clamp(arena.getMinPlayers() + delta, 2, arena.getMaxPlayers()));
                break;
            case E_MAX:
                arena.setMaxPlayers(clamp(arena.getMaxPlayers() + delta, 2,
                        arena.getTeamCount() * arena.getPlayersPerTeam()));
                normalize(arena);
                break;
            case E_LOBBY:
                main.getPlacements().start(player, new PlacementSession(arenaName,
                        PlacementSession.Kind.LOBBY, null));
                return;
            case E_NPC:
                if (rightClick) {
                    arena.clearShopNpcs();
                    main.getArenaManager().forgetPendingNpcs(arena.getName());
                    main.getArenaManager().save();
                    break;
                }
                main.getPlacements().start(player, new PlacementSession(arenaName,
                        PlacementSession.Kind.NPC, null));
                return;
            case E_REGEN:
                main.getConfig().set("map-regeneration", !main.isMapRegenerationEnabled());
                main.saveConfig();
                break;
            case E_BORDERTIME:
                // Reglage a la minute (stocke en secondes), borne 1 a 60 min.
                arena.setBorderTime(clamp(arena.getBorderTime() / 60 + delta, 1, 60) * 60);
                break;
            case E_SPECTATORS:
                arena.setAllowSpectators(!arena.isAllowSpectators());
                break;
            case E_DELETE:
                openDelete(player, arenaName);
                return;
            case E_SAVE:
                save(player, arena);
                openEdit(player, arenaName);
                return;
            default:
                int teamIndex = indexOf(E_TEAMS, slot);
                if (teamIndex >= 0) {
                    String team = Arena.TEAM_NAMES[teamIndex];
                    if (arena.isTeamActive(team)) {
                        openTeam(player, arenaName, team);
                    }
                }
                return;
        }
        main.getArenaManager().save();
        openEdit(player, arenaName);
    }

    private void clickTeam(Player player, String arenaName, String team, int slot) {
        if (slot == T_BACK) {
            openEdit(player, arenaName);
        } else if (slot == T_SPAWN) {
            main.getPlacements().start(player, new PlacementSession(arenaName,
                    PlacementSession.Kind.SPAWN, Arrays.asList(team)));
        } else if (slot == T_BED) {
            main.getPlacements().start(player, new PlacementSession(arenaName,
                    PlacementSession.Kind.BED, Arrays.asList(team)));
        }
    }

    private void clickDelete(Player player, String arenaName, int slot) {
        if (slot == D_CONFIRM) {
            main.getArenaManager().deleteArena(arenaName);
            player.sendMessage(Main.PREFIX
                    + Messages.tr("command.arena_deleted", "name", arenaName));
            openList(player);
        } else if (slot == D_CANCEL) {
            openEdit(player, arenaName);
        }
    }

    private void save(Player player, Arena arena) {
        String problem = arena.validate();
        if (problem != null) {
            player.sendMessage(Main.PREFIX
                    + Messages.tr("command.arena_incomplete", "problem", problem));
        } else {
            main.getArenaManager().save();
            player.sendMessage(Main.PREFIX
                    + Messages.tr("command.arena_saved", "arena", arena.getName()));
        }
    }

    // ================= Utilitaires =================

    private List<Arena> arenas() {
        return new ArrayList<Arena>(main.getArenaManager().getArenas());
    }

    /** Ramene min/max dans des bornes coherentes apres un changement de format. */
    private void normalize(Arena arena) {
        int capacity = arena.getTeamCount() * arena.getPlayersPerTeam();
        if (arena.getMaxPlayers() > capacity) {
            arena.setMaxPlayers(capacity);
        }
        if (arena.getMaxPlayers() < 2) {
            arena.setMaxPlayers(2);
        }
        if (arena.getMinPlayers() > arena.getMaxPlayers()) {
            arena.setMinPlayers(arena.getMaxPlayers());
        }
        if (arena.getMinPlayers() < 2) {
            arena.setMinPlayers(2);
        }
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /** Lit colore d'une equipe (rouge par defaut, un seul lit avant la 1.13). */
    private static AWMaterial bedMaterial(String team) {
        if ("Bleu".equals(team)) {
            return AWMaterial.BLUE_BED;
        }
        if ("Jaune".equals(team)) {
            return AWMaterial.YELLOW_BED;
        }
        if ("Vert".equals(team)) {
            return AWMaterial.GREEN_BED;
        }
        return AWMaterial.RED_BED;
    }

    private String state(boolean on) {
        return on ? Messages.tr("config.state.on") : Messages.tr("config.state.off");
    }

    private List<String> one(String line) {
        List<String> list = new ArrayList<String>();
        list.add(line);
        return list;
    }

    /** Remplit tout l'inventaire de panneaux gris (les cases vides restent grises). */
    private void fill(Inventory inv) {
        ItemStack pane = filler();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            inv.setItem(slot, pane.clone());
        }
    }

    private ItemStack filler() {
        ItemStack item = AWMaterial.GRAY_STAINED_GLASS_PANE.item();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Un espace comme nom : la case parait vide mais reste decorative.
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack icon(AWMaterial material, String name, List<String> lore) {
        ItemStack item = material.item();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
