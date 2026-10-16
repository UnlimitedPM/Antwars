package fr.laugh.antwars.shop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import fr.laugh.antwars.Arena;
import fr.laugh.antwars.Main;
import fr.laugh.antwars.compat.AWMaterial;
import fr.laugh.antwars.game.TeamArmor;
import fr.laugh.antwars.lang.Messages;

/**
 * Boutique d'AntWars : on y achete son equipement en payant avec la terre
 * ramassee en minant.
 *
 * <p>Le catalogue est <b>entierement pilote par config.yml</b> (section
 * {@code shop:} pour les items, {@code potions:} pour les potions). Chaque
 * article y choisit son materiau, sa categorie (l'onglet), son emplacement
 * (0 a 20, grille 3x7), son prix, sa quantite ; un {@code name:} optionnel donne
 * un nom personnalise, sinon le nom traduit ({@code item.<id>}) est utilise. Un
 * prix a 0 retire l'article. Si la section est absente ou en ancien format, on
 * retombe sur un catalogue integre (memes articles par defaut) : la boutique
 * marche donc meme sans toucher la config.
 *
 * <p>Les potions restent a part : leur effet est code (voir {@link PotionKind}),
 * on ne regle que leur prix / onglet / emplacement.
 */
public class ShopMenu {

    private static final int SIZE = 45;

    /** Nombre d'emplacements par onglet : 3 rangees de 7 (indices 0 a 20). */
    private static final int MAX_SLOTS = 21;

    /** Emplacements par defaut des potions dans leur onglet (2e rangee). */
    private static final int[] DEFAULT_POTION_SLOTS = { 7, 8, 9, 10, 11, 12 };

    private final Main main;
    private final List<ShopItem> catalogue = new ArrayList<ShopItem>();

    public ShopMenu(Main main) {
        this.main = main;
        buildCatalogue();
    }

    // ================= Chargement du catalogue =================

    private void buildCatalogue() {
        catalogue.clear();
        if (!loadItemsFromConfig()) {
            main.getLogger().info("Boutique : section 'shop' absente ou en ancien format,"
                    + " utilisation des articles par defaut.");
            buildDefaultItems();
        }
        loadPotions();
    }

    /**
     * Lit les articles de la section {@code shop:} de config.yml.
     *
     * @return vrai si au moins un article valide a ete charge (sinon on repli
     *         sur les articles integres)
     */
    private boolean loadItemsFromConfig() {
        ConfigurationSection shop = main.getConfig().getConfigurationSection("shop");
        if (shop == null) {
            return false;
        }
        int added = 0;
        for (String id : shop.getKeys(false)) {
            ConfigurationSection section = shop.getConfigurationSection(id);
            if (section == null) {
                // Ancien format (la valeur etait un prix, pas une sous-section)
                // ou entree malformee : on l'ignore.
                continue;
            }
            ShopItem item = parseItem(id, section);
            if (item != null) {
                catalogue.add(item);
                added++;
            }
        }
        return added > 0;
    }

    private ShopItem parseItem(String id, ConfigurationSection section) {
        String materialName = section.getString("material");
        if (materialName == null) {
            return warn(id, "materiau manquant");
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        if (AWMaterial.itemFromName(materialName, amount) == null) {
            return warn(id, "materiau introuvable sur cette version : " + materialName);
        }
        Category category = Category.fromName(section.getString("category", "MISC"));
        if (category == null || category == Category.QUICK) {
            return warn(id, "categorie invalide (utilise WEAPONS, TOOLS, ARMOR, FOOD ou MISC)");
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= MAX_SLOTS) {
            return warn(id, "slot hors bornes (0 a " + (MAX_SLOTS - 1) + ")");
        }
        int price = section.getInt("price", 0);
        boolean quick = section.getBoolean("quick", false);
        String name = section.getString("name", null);
        return new ShopItem(category, materialName, "item." + id, name, amount, price, quick, slot);
    }

    private ShopItem warn(String id, String reason) {
        main.getLogger().warning("Boutique : article '" + id + "' ignore (" + reason + ").");
        return null;
    }

    /** Potions : effet fixe (PotionKind), prix / onglet / emplacement reglables. */
    private void loadPotions() {
        ConfigurationSection potions = main.getConfig().getConfigurationSection("potions");
        int index = 0;
        for (PotionKind kind : PotionKind.values()) {
            int price = kind.getDefaultPrice();
            Category category = Category.MISC;
            int slot = DEFAULT_POTION_SLOTS[index % DEFAULT_POTION_SLOTS.length];

            if (potions != null) {
                ConfigurationSection section = potions.getConfigurationSection(kind.name());
                if (section != null) {
                    price = section.getInt("price", price);
                    Category configured = Category.fromName(section.getString("category", "MISC"));
                    if (configured != null && configured != Category.QUICK) {
                        category = configured;
                    }
                    slot = section.getInt("slot", slot);
                } else {
                    // Ancien format : "potions: HARMING_I: 64" (la valeur est le prix).
                    price = potions.getInt(kind.name(), price);
                }
            }
            if (slot < 0 || slot >= MAX_SLOTS) {
                slot = DEFAULT_POTION_SLOTS[index % DEFAULT_POTION_SLOTS.length];
            }
            catalogue.add(new ShopItem(category, kind, price, false, slot));
            index++;
        }
    }

    /**
     * Catalogue de repli, identique a celui livre dans config.yml. Sert quand la
     * config n'a pas de section {@code shop:} exploitable, pour que la boutique
     * fonctionne malgre tout.
     */
    private void buildDefaultItems() {
        def("wooden_sword", "WOODEN_SWORD", Category.WEAPONS, 0, 20, true);
        def("stone_sword", "STONE_SWORD", Category.WEAPONS, 1, 35, true);
        def("iron_sword", "IRON_SWORD", Category.WEAPONS, 2, 128, true);
        def("bow", "BOW", Category.WEAPONS, 7, 20, false);
        def("arrow", "ARROW", Category.WEAPONS, 8, 4, false);

        def("wooden_shovel", "WOODEN_SHOVEL", Category.TOOLS, 0, 10, true);
        def("stone_shovel", "STONE_SHOVEL", Category.TOOLS, 1, 45, true);
        def("iron_shovel", "IRON_SHOVEL", Category.TOOLS, 2, 64, false);

        // Cuir sur la rangee du haut, maille juste en dessous, alignes.
        def("leather_helmet", "LEATHER_HELMET", Category.ARMOR, 0, 10, false);
        def("leather_chestplate", "LEATHER_CHESTPLATE", Category.ARMOR, 1, 15, false);
        def("leather_leggings", "LEATHER_LEGGINGS", Category.ARMOR, 2, 10, false);
        def("leather_boots", "LEATHER_BOOTS", Category.ARMOR, 3, 10, false);
        def("chainmail_helmet", "CHAINMAIL_HELMET", Category.ARMOR, 7, 30, false);
        def("chainmail_chestplate", "CHAINMAIL_CHESTPLATE", Category.ARMOR, 8, 35, false);
        def("chainmail_leggings", "CHAINMAIL_LEGGINGS", Category.ARMOR, 9, 30, false);
        def("chainmail_boots", "CHAINMAIL_BOOTS", Category.ARMOR, 10, 30, false);

        def("cooked_beef", "COOKED_BEEF", Category.FOOD, 0, 5, true);
        def("golden_apple", "GOLDEN_APPLE", Category.FOOD, 1, 15, true);

        def("tnt", "TNT", Category.MISC, 0, 25, true);
        def("flint_and_steel", "FLINT_AND_STEEL", Category.MISC, 1, 15, true);
        def("end_stone", "END_STONE", Category.MISC, 2, 10, true);
        def("chest", "CHEST", Category.MISC, 3, 55, true);
        def("torch", "TORCH", Category.MISC, 4, 1, true);
        def("water_bucket", "WATER_BUCKET", Category.MISC, 5, 30, false);
    }

    private void def(String id, String material, Category category, int slot, int price, boolean quick) {
        if (AWMaterial.itemFromName(material, 1) != null) {
            catalogue.add(new ShopItem(category, material, "item." + id, null, 1, price, quick, slot));
        }
    }

    /**
     * Articles en vente dans un onglet donne. Pour l'Achat Rapide, {@code player}
     * determine la source : ses favoris personnels s'il en a defini
     * ({@link QuickBuyManager}), sinon la selection par defaut de l'admin (les
     * articles marques {@code quick: true}).
     */
    private List<ShopItem> itemsOf(Category category, Player player) {
        if (category == Category.QUICK) {
            return quickItemsFor(player);
        }
        return itemsOfCategory(category);
    }

    /** Articles en vente d'une categorie reelle (jamais {@link Category#QUICK}). */
    private List<ShopItem> itemsOfCategory(Category category) {
        List<ShopItem> items = new ArrayList<ShopItem>();
        for (ShopItem item : catalogue) {
            if (item.isSold() && item.getCategory() == category) {
                items.add(item);
            }
        }
        return items;
    }

    /** Articles de l'Achat Rapide pour ce joueur : ses favoris, sinon le defaut de l'admin. */
    private List<ShopItem> quickItemsFor(Player player) {
        Set<String> favorites = player == null
                ? Collections.<String>emptySet()
                : main.getQuickBuyManager().getFavorites(player);
        List<ShopItem> items = new ArrayList<ShopItem>();
        if (!favorites.isEmpty()) {
            for (ShopItem item : catalogue) {
                if (item.isSold() && favorites.contains(item.getLabel())) {
                    items.add(item);
                }
            }
            return items;
        }
        for (ShopItem item : catalogue) {
            if (item.isSold() && item.isQuick()) {
                items.add(item);
            }
        }
        return items;
    }

    // ================= Affichage =================

    public void open(Player player) {
        open(player, Category.QUICK);
    }

    public void open(Player player, Category category) {
        ShopHolder holder = new ShopHolder(category);
        // Le titre doit rester sous 32 caracteres : au-dela, les serveurs 1.8
        // refusent d'ouvrir l'inventaire.
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                Messages.tr("shop.title", "category", Messages.tr(category.getTitle())));
        holder.setInventory(inventory);

        for (Category tab : Category.values()) {
            boolean active = tab == category;
            String name = (active ? ChatColor.GREEN : ChatColor.AQUA) + Messages.tr(tab.getTitle());
            String lore = active
                    ? Messages.tr("shop.tab_active")
                    : Messages.tr("shop.tab_open");
            inventory.setItem(tab.getSlot(), icon(tab.getIcon(), name, lore));
        }

        ItemStack separator = icon(AWMaterial.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int slot = 9; slot <= 17; slot++) {
            inventory.setItem(slot, separator.clone());
        }

        // Articles de l'onglet. L'Achat Rapide range ses articles a la suite ;
        // les autres onglets respectent le slot configure de chaque article.
        List<ShopItem> items = itemsOf(category, player);
        if (category == Category.QUICK) {
            for (int i = 0; i < items.size() && i < MAX_SLOTS; i++) {
                place(inventory, i, items.get(i));
            }
        } else {
            for (ShopItem item : items) {
                place(inventory, item.getSlot(), item);
            }
        }

        player.openInventory(inventory);
    }

    // ================= Personnalisation de l'Achat Rapide =================

    /** Ouvre le menu de personnalisation, sur la premiere categorie reelle. */
    public void openCustomize(Player player) {
        openCustomize(player, Category.WEAPONS);
    }

    /**
     * Ouvre le menu de personnalisation de l'Achat Rapide sur un onglet precis :
     * cliquer un article l'ajoute ou le retire des favoris du joueur (jamais un
     * achat). {@link Category#QUICK} n'a pas de sens ici (c'est justement ce
     * qu'on est en train de definir) : on retombe sur les armes.
     */
    public void openCustomize(Player player, Category category) {
        Category shown = category == Category.QUICK ? Category.WEAPONS : category;

        ShopHolder holder = new ShopHolder(shown, true);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Messages.tr("shop.customize_title"));
        holder.setInventory(inventory);

        // A la place de l'onglet Achat Rapide (sans objet ici) : un rappel du fonctionnement.
        inventory.setItem(Category.QUICK.getSlot(), icon(AWMaterial.NETHER_STAR,
                Messages.tr("shop.customize_info_name"),
                Messages.tr("shop.customize_info_lore")));

        for (Category tab : Category.values()) {
            if (tab == Category.QUICK) {
                continue;
            }
            boolean active = tab == shown;
            String name = (active ? ChatColor.GREEN : ChatColor.AQUA) + Messages.tr(tab.getTitle());
            String lore = active ? Messages.tr("shop.tab_active") : Messages.tr("shop.tab_open");
            inventory.setItem(tab.getSlot(), icon(tab.getIcon(), name, lore));
        }

        ItemStack separator = icon(AWMaterial.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int slot = 9; slot <= 17; slot++) {
            inventory.setItem(slot, separator.clone());
        }

        Set<String> favorites = main.getQuickBuyManager().getFavorites(player);
        for (ShopItem item : itemsOfCategory(shown)) {
            placeCustomize(inventory, item.getSlot(), item, favorites.contains(item.getLabel()));
        }

        player.openInventory(inventory);
    }

    public void handleCustomizeClick(Player player, int slot, Category category) {
        Category tab = Category.fromSlot(slot);
        if (tab != null) {
            if (tab != Category.QUICK && tab != category) {
                openCustomize(player, tab);
            }
            return;
        }

        int grid = slotToGrid(slot);
        if (grid < 0) {
            return;
        }
        ShopItem clicked = null;
        for (ShopItem item : itemsOfCategory(category)) {
            if (item.getSlot() == grid) {
                clicked = item;
                break;
            }
        }
        if (clicked == null) {
            return;
        }

        boolean added = main.getQuickBuyManager().toggle(player, clicked.getLabel());
        String itemName = clicked.getName() != null
                ? ChatColor.translateAlternateColorCodes('&', clicked.getName())
                : Messages.tr(clicked.getLabel());
        player.sendMessage(Main.PREFIX + Messages.tr(
                added ? "shop.customize_added" : "shop.customize_removed", "item", itemName));
        openCustomize(player, category);
    }

    private void placeCustomize(Inventory inventory, int grid, ShopItem item, boolean favorite) {
        if (grid < 0 || grid >= MAX_SLOTS) {
            return;
        }
        ItemStack stack = customizeDisplay(item, favorite);
        if (stack != null) {
            inventory.setItem(gridToSlot(grid), stack);
        }
    }

    /** Article tel qu'il apparait en mode personnalisation : etat favori, pas de prix. */
    private ItemStack customizeDisplay(ShopItem item, boolean favorite) {
        ItemStack stack = item.buildStack();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = item.getName() != null
                    ? ChatColor.translateAlternateColorCodes('&', item.getName())
                    : ChatColor.RED + Messages.tr(item.getLabel());
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<String>();
            lore.add(favorite ? Messages.tr("shop.customize_in") : Messages.tr("shop.customize_out"));
            lore.add(Messages.tr("shop.customize_toggle"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** Pose un article dans la grille (indice 0 a 20) si son item est valide. */
    private void place(Inventory inventory, int grid, ShopItem item) {
        if (grid < 0 || grid >= MAX_SLOTS) {
            return;
        }
        ItemStack display = display(item);
        if (display != null) {
            inventory.setItem(gridToSlot(grid), display);
        }
    }

    private ItemStack display(ShopItem item) {
        ItemStack stack = item.buildStack();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = item.getName() != null
                    ? ChatColor.translateAlternateColorCodes('&', item.getName())
                    : ChatColor.RED + Messages.tr(item.getLabel());
            if (item.getAmount() > 1) {
                name = name + Messages.tr("shop.amount_suffix", "amount", item.getAmount());
            }
            meta.setDisplayName(name);

            List<String> lore = new ArrayList<String>();
            lore.add(Messages.tr("shop.price_lore", "price", item.getPrice()));
            if (item.isArmor()) {
                lore.add(Messages.tr("shop.armor_auto"));
            }
            lore.add(Messages.tr("shop.click_buy"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack icon(AWMaterial material, String name, String lore) {
        ItemStack item = material.item();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // ================= Grille <-> emplacements d'inventaire =================

    /** Emplacement d'inventaire d'un indice de grille (0-20 -> 3 rangees de 7). */
    private int gridToSlot(int grid) {
        return 19 + (grid / 7) * 9 + (grid % 7);
    }

    /** Indice de grille d'un emplacement d'inventaire, ou -1 s'il est hors grille. */
    private int slotToGrid(int slot) {
        int[] rowStart = { 19, 28, 37 };
        for (int row = 0; row < rowStart.length; row++) {
            if (slot >= rowStart[row] && slot <= rowStart[row] + 6) {
                return row * 7 + (slot - rowStart[row]);
            }
        }
        return -1;
    }

    // ================= Clics =================

    /**
     * Traite un clic dans la boutique : changement d'onglet sur la premiere
     * ligne, achat sur les rayons.
     *
     * @param slot     emplacement clique dans l'inventaire du haut
     * @param category onglet actuellement ouvert
     */
    public void handleClick(Player player, int slot, Category category) {
        Category tab = Category.fromSlot(slot);
        if (tab != null) {
            if (tab != category) {
                open(player, tab);
            }
            return;
        }

        int grid = slotToGrid(slot);
        if (grid < 0) {
            return;
        }
        List<ShopItem> items = itemsOf(category, player);
        ShopItem clicked = null;
        if (category == Category.QUICK) {
            // L'Achat Rapide range ses articles a la suite : l'indice de grille
            // est directement la position dans la liste.
            if (grid < items.size()) {
                clicked = items.get(grid);
            }
        } else {
            for (ShopItem item : items) {
                if (item.getSlot() == grid) {
                    clicked = item;
                    break;
                }
            }
        }
        if (clicked != null) {
            buy(player, clicked);
        }
    }

    // ================= Achat =================

    private void buy(Player player, ShopItem item) {
        int price = item.getPrice();
        if (countDirt(player) < price) {
            player.sendMessage(Main.PREFIX + Messages.tr("shop.not_enough", "price", price));
            return;
        }

        ItemStack bought = item.buildStack();
        if (bought == null) {
            return;
        }
        removeDirt(player, price);

        // Le cuir vendu prend la couleur de l'equipe de l'acheteur (rien n'est
        // donne gratuitement : c'est l'armure de la boutique qui est coloree).
        if (item.isArmor() && main.isTeamArmorEnabled()) {
            Arena arena = main.getArenaManager().getArenaOf(player);
            String team = arena == null ? null : arena.getTeam(player);
            if (team != null) {
                TeamArmor.dye(bought, Arena.teamColorOf(team));
            }
        }

        if (!item.isArmor() || !equip(player, item, bought)) {
            // addItem renvoie ce qu'il n'a pas pu ranger ; on le pose au sol
            // plutot que de le perdre silencieusement.
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(bought);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        refresh(player);
        player.sendMessage(Main.PREFIX + Messages.tr("shop.bought",
                "item", item.getName() != null
                        ? ChatColor.translateAlternateColorCodes('&', item.getName())
                        : Messages.tr(item.getLabel()),
                "price", price));
    }

    /**
     * Equipe directement une piece d'armure si son emplacement est libre.
     *
     * <p>On identifie l'emplacement par le suffixe du nom du materiau, qui
     * n'a pas change entre la 1.8 et la 1.21.
     *
     * @return vrai si la piece a ete equipee, faux s'il faut la ranger dans
     *         l'inventaire parce que l'emplacement est deja pris
     */
    private boolean equip(Player player, ShopItem item, ItemStack piece) {
        PlayerInventory inventory = player.getInventory();
        String name = item.getMaterialName();
        if (name == null) {
            return false;
        }
        name = name.toUpperCase(java.util.Locale.ROOT);

        if (name.endsWith("_HELMET") && inventory.getHelmet() == null) {
            inventory.setHelmet(piece);
            return true;
        }
        if (name.endsWith("_CHESTPLATE") && inventory.getChestplate() == null) {
            inventory.setChestplate(piece);
            return true;
        }
        if (name.endsWith("_LEGGINGS") && inventory.getLeggings() == null) {
            inventory.setLeggings(piece);
            return true;
        }
        if (name.endsWith("_BOOTS") && inventory.getBoots() == null) {
            inventory.setBoots(piece);
            return true;
        }
        return false;
    }

    public static int countDirt(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && AWMaterial.DIRT.is(item.getType())) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Retire exactement {@code amount} blocs de terre. Pas de
     * {@code inventory.remove(item)} : il supprime TOUS les lots identiques et
     * facturerait bien plus que le prix affiche.
     */
    public static void removeDirt(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || !AWMaterial.DIRT.is(item.getType())) {
                continue;
            }
            int inSlot = item.getAmount();
            if (inSlot > remaining) {
                item.setAmount(inSlot - remaining);
                remaining = 0;
            } else {
                remaining -= inSlot;
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * Force la mise a jour de l'inventaire cote client.
     *
     * <p>Depreciee de longue date mais toujours presente en 1.21, et
     * necessaire apres une modification faite pendant un evenement de clic.
     */
    @SuppressWarnings("deprecation")
    private void refresh(Player player) {
        player.updateInventory();
    }
}
