package io.ncbpfluffybear.fluffymachines.items.tools;

import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.collections.Pair;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.libraries.unirest.json.JSONObject;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.ncbpfluffybear.fluffymachines.FluffyMachines;
import io.ncbpfluffybear.fluffymachines.utils.Utils;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * used to quickly manipulate cargo nodes
 *
 * @author NCBPFluffyBear
 */
public class CargoManipulator extends SimpleSlimefunItem<ItemUseHandler> implements Listener {

    private static final int[] CARGO_SLOTS = {19, 20, 21, 28, 29, 30, 37, 38, 39};
    private Map<Player, Pair<JSONObject, ItemStack[]>> storedFilters = new HashMap<>();

    public CargoManipulator(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        Bukkit.getPluginManager().registerEvents(this, FluffyMachines.getInstance());
    }

    @Nonnull
    @Override
    public ItemUseHandler getItemHandler() {
        return e -> e.setUseBlock(Event.Result.DENY); // Prevent opening inventories
    }

    @EventHandler
    private void onCargoManipulatorUse(PlayerInteractEvent e) {

        ItemStack manipulator = e.getItem();

        // Check item is cargo manipulator
        if (manipulator == null || !this.isItem(manipulator)) {
            return;
        }

        e.setCancelled(true);

        Action act = e.getAction();
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();

        // Check if targeted block is cargo node
        if (getCargoNodeType(b) == null) {
            return;
        }

        if (act == Action.RIGHT_CLICK_BLOCK) {
            if (p.isSneaking()) {
                clearNode(b, p, getCargoNodeType(b));
            } else {
                copyNode(b, p, getCargoNodeType(b));
            }
        } else {
            pasteNode(b, p, getCargoNodeType(b));
        }
    }

    /**
     * Copy's a node's data into the manipulator. Cargo inventories stored in map.
     * Action: Right Click Block
     */
    private void copyNode(Block parent, Player p, SlimefunItemStack nodeType) {
        // Copy BlockStorage data
        JSONObject nodeData = new JSONObject(BlockStorage.getBlockInfoAsJson(parent));

        ItemStack[] filterItems = new ItemStack[9];
        if (nodeType != SlimefunItems.CARGO_OUTPUT_NODE) { // No inventory
            // Copy inventory into map
            BlockMenu parentInventory = BlockStorage.getInventory(parent);
            for (int i = 0; i < 9; i++) { // Iterate through all slots in cargo filter
                ItemStack menuItem = parentInventory.getItemInSlot(CARGO_SLOTS[i]);
                if (menuItem != null) {
                    filterItems[i] = new CustomItemStack(menuItem, 1);
                } else {
                    filterItems[i] = null;
                }
            }
        }

        storedFilters.put(p, new Pair<>(nodeData, filterItems)); // Save cargo slots into map

        Utils.send(p, "&aYour " + SlimefunItem.getById((String) nodeData.get("id")).getItemName() + " &ahas been copied.");
    }

    /**
     * Pastes stored node contents
     * Action: Left Click
     */
    private void pasteNode(Block child, Player p, SlimefunItemStack nodeType) {
        Pair<JSONObject, ItemStack[]> nodeSettings = storedFilters.getOrDefault(p, null);

        // No data saved yet
        if (nodeSettings == null) {
            Utils.send(p, "&cYou have not copied a cargo node yet.");
            return;
        }

        // Get saved data
        JSONObject jsonData = nodeSettings.getFirstValue();

        SlimefunItemStack savedNodeType = (SlimefunItemStack) SlimefunItem.getById((String) jsonData.get("id")).getItem();
        if (savedNodeType != nodeType) {
            Utils.send(p, "&cYou copied a " + savedNodeType.getDisplayName() +
                    " &cbut you are trying to modify a " + nodeType.getDisplayName() + "&c!");
            return;
        }

        // Set the data
        BlockStorage.setBlockInfo(child, jsonData.toString(), false);

        if (nodeType != SlimefunItems.CARGO_OUTPUT_NODE) {
            // Set the filter
            BlockMenu nodeMenu = BlockStorage.getInventory(child);
            ItemStack[] filterItems = nodeSettings.getSecondValue();
            Inventory playerInventory = p.getInventory();

            for (int i = 0; i < 9; i++) {

                // Check if item already exists in slot
                if (SlimefunUtils.isItemSimilar(filterItems[i], nodeMenu.getItemInSlot(CARGO_SLOTS[i]), true, false)) {
                    continue;
                }

                // Drop item in filter slot
                clearFilterSlot(nodeMenu, CARGO_SLOTS[i], p);

                // No need to insert new items in
                if (filterItems[i] == null) {
                    continue;
                }

                // Check if item not in inventory
                if (!SlimefunUtils.containsSimilarItem(playerInventory, filterItems[i], true)) {
                    Utils.send(p, "&cYou do not have " + Utils.getViewableName(filterItems[i]) + "&c. Skipping this item.");
                    continue;
                }

                // Consume item in player inventory
                for (ItemStack playerItem : playerInventory) {
                    if (SlimefunUtils.isItemSimilar(playerItem, filterItems[i], false, false)) {
                        playerItem.setAmount(playerItem.getAmount() - 1);

                        // Insert item into node menu
                        nodeMenu.replaceExistingItem(CARGO_SLOTS[i], new CustomItemStack(playerItem, 1));
                        break;
                    }
                }
            }
        }

        // Force menu update
        BlockStorage.getStorage(child.getWorld()).reloadInventory(child.getLocation());
        Utils.send(p, "&aYour " + savedNodeType.getDisplayName() + " &ahas been pasted.");

    }

    /**
     * Clears the data of a targeted node
     * Action: Sneak + Right Click Block
     */
    private void clearNode(Block node, Player p, SlimefunItemStack nodeType) {
        // Clear node settings
        BlockStorage.addBlockInfo(node, "owner", p.getUniqueId().toString());
        BlockStorage.addBlockInfo(node, "frequency", "0");

        // These settings are only for Input and Advanced Output nodes
        if (nodeType != SlimefunItems.CARGO_OUTPUT_NODE) {
            // AbstractFilterNode settings
            BlockStorage.addBlockInfo(node, "index", "0");
            BlockStorage.addBlockInfo(node, "filter-type", "whitelist");
            BlockStorage.addBlockInfo(node, "filter-lore", String.valueOf(true));
            BlockStorage.addBlockInfo(node, "filter-durability", String.valueOf(false));

            if (nodeType == SlimefunItems.CARGO_INPUT_NODE) {
                // CargoInputNode settings
                BlockStorage.addBlockInfo(node, "round-robin", String.valueOf(false));
                BlockStorage.addBlockInfo(node, "smart-fill", String.valueOf(false));
            }

            clearNodeFilter(node, p);

            // Force update
            BlockStorage.getStorage(node.getWorld()).reloadInventory(node.getLocation());

            Utils.send(p, "&aThe selected Cargo Node has been cleared");
        }
    }

    private void clearNodeFilter(Block node, Player p) {
        // Empty filter contents
        BlockMenu nodeMenu = BlockStorage.getInventory(node);
        for (int i = 0; i < 9; i++) {
            clearFilterSlot(nodeMenu, CARGO_SLOTS[i], p);
        }
    }

    private void clearFilterSlot(BlockMenu nodeMenu, int slot, Player p) {
        ItemStack filterItem = nodeMenu.getItemInSlot(slot);
        if (filterItem != null) {
            Utils.giveOrDropItem(p, filterItem); // Give player item in filter
            nodeMenu.replaceExistingItem(slot, null); // Clear item in filter
        }
    }

    /**
     * Get the SlimefunItemStack of the cargo node
     */
    private SlimefunItemStack getCargoNodeType(Block b) {
        String blockId = BlockStorage.checkID(b);

        if (blockId == null) {
            return null;
        }

        return (SlimefunItemStack) SlimefunItem.getById(blockId).getItem();
    }
}