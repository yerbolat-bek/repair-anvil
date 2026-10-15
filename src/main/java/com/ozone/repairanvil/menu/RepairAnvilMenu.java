package com.ozone.repairanvil.menu;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RepairAnvilMenu extends AbstractContainerMenu {

    public static final int INPUT_ITEM_SLOT = 0;
    public static final int INPUT_MATERIAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    public static final int INVENTORY_START = 3;
    public static final int INVENTORY_END = 39;   // exclusive: slots 3..38 are the player's inventory + hotbar
    public static final int TAG_SLOT = 39;        // added last so the indices above stay unchanged

    private static final int MATERIAL_COST_PER_HIT = 1;
    private static final int MERGE_XP_COST = 2;
    private static final int RENAME_XP_COST = 1;

    private final Container inputContainer = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            RepairAnvilMenu.this.slotsChanged(this);
        }
    };
    private final Container tagContainer = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            RepairAnvilMenu.this.slotsChanged(this);
        }
    };
    private final Container resultContainer = new SimpleContainer(1);

    private final Level level;
    private final Player player;

    private final DataSlot repairedPointsData = DataSlot.standalone();
    private final DataSlot repairedMaskData = DataSlot.standalone();   // bit i = spot i is repaired
    private final DataSlot totalPointsData = DataSlot.standalone();
    // 0 = anvil, 1 = chipped anvil, 2 = damaged anvil
    private final DataSlot anvilStageData = DataSlot.standalone();
    // XP levels needed to take the current result (synced so the screen can show it)
    private final DataSlot xpCostData = DataSlot.standalone();

    private boolean sessionActive = false;
    private boolean repairedResultReady = false;   // result slot holds a finished repair
    private int sessionInitialDamage = 0;
    private String pendingItemName = "";
    private boolean pendingConsumesTag = false;
    private boolean pendingConsumesMaterial = false;

    public RepairAnvilMenu(int syncId, Inventory playerInventory) {
        super(RepairAnvilMenus.REPAIR_ANVIL_MENU, syncId);
        this.level = playerInventory.player.level();
        this.player = playerInventory.player;

        // item slot
        this.addSlot(new Slot(inputContainer, 0, 56, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !repairedResultReady;
            }
        });

        // material / second item / enchanted book
        this.addSlot(new Slot(inputContainer, 1, 92, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                ItemStack target = inputContainer.getItem(INPUT_ITEM_SLOT);
                if (target.isEmpty() || repairedResultReady) {
                    return false;
                }
                if (isSameItemMerge(target, stack)) {
                    return true;
                }
                if (isValidRepairMaterial(target, stack)) {
                    return true;
                }
                if (isEnchantedBook(stack)) {
                    return applyBook(target.copy(), stack);   // only if at least one enchantment fits
                }
                return false;
            }
        });

        // result
        this.addSlot(new Slot(resultContainer, 0, 128, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                if (sessionActive) {
                    return false;
                }
                return player.getAbilities().instabuild || player.experienceLevel >= xpCostData.get();
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                int cost = xpCostData.get();
                boolean consumeTag = pendingConsumesTag;
                boolean consumeMaterial = pendingConsumesMaterial;
                boolean fromRepair = repairedResultReady;

                pendingConsumesTag = false;
                pendingConsumesMaterial = false;
                repairedResultReady = false;

                if (player instanceof ServerPlayer) {
                    if (cost > 0 && !player.getAbilities().instabuild) {
                        player.giveExperienceLevels(-cost);
                    }
                    if (consumeTag) {
                        ItemStack tag = tagContainer.getItem(0);
                        if (!tag.isEmpty()) {
                            tag.shrink(1);
                            tagContainer.setItem(0, tag.isEmpty() ? ItemStack.EMPTY : tag);
                        }
                    }
                }

                if (!fromRepair) {
                    if (consumeMaterial) {
                        inputContainer.setItem(INPUT_MATERIAL_SLOT, ItemStack.EMPTY);
                    }
                    inputContainer.setItem(INPUT_ITEM_SLOT, ItemStack.EMPTY);
                } else {
                    // repair finished: item slot is already empty, re-evaluate what is left (unused material)
                    slotsChanged(inputContainer);
                }
                super.onTake(player, stack);
            }
        });

        // player inventory: 3 rows, pitch 18 (matches the cells in the texture)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 20 + col * 18, 119 + row * 18));
            }
        }
        // hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 20 + col * 18, 173));
        }

        // name tag slot (index 39), sits on the ring of the painted tag
        this.addSlot(new Slot(tagContainer, 0, 46, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.NAME_TAG);
            }
        });

        this.addDataSlot(totalPointsData);
        this.addDataSlot(repairedPointsData);
        this.addDataSlot(repairedMaskData);
        this.addDataSlot(anvilStageData);
        this.addDataSlot(xpCostData);
    }

    // ------------------------------------------------------------------ small helpers

    private boolean isServerSide() {
        return player instanceof ServerPlayer;
    }

    private boolean isValidRepairMaterial(ItemStack repairTarget, ItemStack material) {
        Repairable repairable = repairTarget.get(DataComponents.REPAIRABLE);
        if (repairable == null) {
            return false;
        }
        return repairable.isValidRepairItem(material);
    }

    private boolean isEnchantedBook(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK);
    }

    private boolean isSameItemMerge(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty()
                && a.getItem() == b.getItem()
                && a.isDamageableItem() && b.isDamageableItem();
    }

    private int calculatePointsFromDamage(ItemStack stack) {
        float remainingRatio = 1f - ((float) stack.getDamageValue() / (float) stack.getMaxDamage());
        float remainingPercent = remainingRatio * 100f;

        if (remainingPercent >= 90f) return 1;
        if (remainingPercent >= 70f) return 2;
        if (remainingPercent >= 50f) return 3;
        if (remainingPercent >= 30f) return 4;
        if (remainingPercent >= 10f) return 5;
        return 6;
    }

    // ------------------------------------------------------------------ synced values for the screen

    /** 0 = anvil, 1 = chipped, 2 = damaged */
    public int getAnvilStage() {
        return anvilStageData.get();
    }

    public void setAnvilStage(int stage) {
        anvilStageData.set(stage);
    }

    public int getXpCost() {
        return xpCostData.get();
    }

    public int getTotalPoints() {
        return totalPointsData.get();
    }

    public int getRepairedPoints() {
        return repairedPointsData.get();
    }

    /** bit i set = spot i is already repaired */
    public int getRepairedMask() {
        return repairedMaskData.get();
    }

    // ------------------------------------------------------------------ enchanting (book)

    private boolean isEnchantmentSupported(Holder<Enchantment> holder, ItemStack stack) {
        return holder.value().definition().supportedItems().contains(stack.getItemHolder());
    }

    private boolean conflictsWithExisting(Holder<Enchantment> holder, Set<Holder<Enchantment>> existing) {
        for (Holder<Enchantment> other : existing) {
            if (other.equals(holder)) {
                continue;
            }
            if (holder.value().exclusiveSet().contains(other) || other.value().exclusiveSet().contains(holder)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies the enchantments of the book that FIT the target (supported item, no conflicts).
     * Returns true when at least one enchantment was applied.
     */
    private boolean applyBook(ItemStack target, ItemStack book) {
        ItemEnchantments bookEnchants = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        Map<Holder<Enchantment>, Integer> current = new HashMap<>();
        target.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                .entrySet().forEach(e -> current.put(e.getKey(), e.getIntValue()));

        boolean applied = false;
        for (var entry : bookEnchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int bookLevel = entry.getIntValue();

            if (!isEnchantmentSupported(holder, target)) {
                continue;
            }
            if (conflictsWithExisting(holder, current.keySet())) {
                continue;
            }

            int currentLevel = current.getOrDefault(holder, 0);
            int newLevel = (currentLevel == bookLevel) ? bookLevel + 1 : Math.max(currentLevel, bookLevel);
            newLevel = Math.min(newLevel, holder.value().getMaxLevel());

            if (newLevel > currentLevel) {
                current.put(holder, newLevel);
                applied = true;
            }
        }

        if (!applied) {
            return false;
        }

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        current.forEach(mutable::set);
        target.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return true;
    }

    // ------------------------------------------------------------------ renaming (name tag)

    /** Called by the server (and by the client for prediction) when the player types a name */
    public void setItemName(String name) {
        String sanitized = name == null ? "" : name.trim();
        if (sanitized.length() > 35) {
            sanitized = sanitized.substring(0, 35);
        }
        this.pendingItemName = sanitized;
        if (!sessionActive && !repairedResultReady) {
            slotsChanged(inputContainer);
        }
    }

    private boolean canRename(ItemStack item) {
        if (item.isEmpty()) {
            return false;
        }
        ItemStack tag = tagContainer.getItem(0);
        if (tag.isEmpty() || !tag.is(Items.NAME_TAG)) {
            return false;
        }
        if (pendingItemName.isEmpty()) {
            return false;
        }
        return !pendingItemName.equals(item.getHoverName().getString());
    }

    // ------------------------------------------------------------------ results

    private void publishResult(ItemStack result, int xpCost, boolean consumesTag, boolean consumesMaterial) {
        resultContainer.setItem(0, result);
        boolean has = !result.isEmpty();
        pendingConsumesTag = has && consumesTag;
        pendingConsumesMaterial = has && consumesMaterial;
        if (isServerSide()) {
            xpCostData.set(has ? xpCost : 0);
        }
    }

    /** Undamaged item: enchant with a book and/or rename with a name tag */
    private void refreshInstantResult() {
        ItemStack item = inputContainer.getItem(INPUT_ITEM_SLOT);
        ItemStack book = inputContainer.getItem(INPUT_MATERIAL_SLOT);

        if (item.isEmpty()) {
            publishResult(ItemStack.EMPTY, 0, false, false);
            return;
        }

        ItemStack output = item.copy();
        boolean bookApplied = false;
        if (isEnchantedBook(book)) {
            bookApplied = applyBook(output, book);
        }

        boolean rename = canRename(item);
        if (rename) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(pendingItemName));
        }

        boolean changed = bookApplied || rename;
        publishResult(changed ? output : ItemStack.EMPTY, rename ? RENAME_XP_COST : 0, rename, bookApplied);
    }

    /** Two identical items: vanilla durability formula (+5% bonus), optional rename */
    private void refreshMergeResult(ItemStack a, ItemStack b) {
        int aDurability = a.getMaxDamage() - a.getDamageValue();
        int bDurability = b.getMaxDamage() - b.getDamageValue();
        int bonus = a.getMaxDamage() * 5 / 100;
        int combinedDurability = aDurability + bDurability + bonus;
        int newDamage = Math.max(a.getMaxDamage() - combinedDurability, 0);

        ItemStack result = a.copy();
        result.setDamageValue(newDamage);

        boolean rename = canRename(a);
        if (rename) {
            result.set(DataComponents.CUSTOM_NAME, Component.literal(pendingItemName));
        }

        publishResult(result, MERGE_XP_COST + (rename ? RENAME_XP_COST : 0), rename, true);
    }

    // ------------------------------------------------------------------ slot change handling

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);

        // a finished repair sits in the result slot until it is taken - never touch it
        if (repairedResultReady) {
            return;
        }

        ItemStack item = inputContainer.getItem(INPUT_ITEM_SLOT);
        ItemStack material = inputContainer.getItem(INPUT_MATERIAL_SLOT);

        if (item.isEmpty()) {
            if (sessionActive) {
                endSession();
            }
            publishResult(ItemStack.EMPTY, 0, false, false);
            return;
        }

        if (isSameItemMerge(item, material)) {
            if (sessionActive) {
                endSession();
            }
            refreshMergeResult(item, material);
            return;
        }

        if (!sessionActive && item.isDamageableItem() && item.getDamageValue() > 0
                && isValidRepairMaterial(item, material)) {
            sessionActive = true;
            sessionInitialDamage = item.getDamageValue();
            totalPointsData.set(calculatePointsFromDamage(item));
            repairedPointsData.set(0);
            repairedMaskData.set(0);
            publishResult(ItemStack.EMPTY, 0, false, false);
            return;
        }

        if (!sessionActive) {
            refreshInstantResult();
        }
    }

    private void endSession() {
        sessionActive = false;
        sessionInitialDamage = 0;
        totalPointsData.set(0);
        repairedPointsData.set(0);
        repairedMaskData.set(0);
    }

    // ------------------------------------------------------------------ click-to-repair

    /** Any unrepaired spot can be hit, in any order. The server validates every click. */
    public boolean tryHitPoint(int pointIndex) {
        if (!sessionActive) {
            return false;
        }

        int totalPoints = totalPointsData.get();
        if (totalPoints == 0 || pointIndex < 0 || pointIndex >= totalPoints) {
            return false;
        }

        int mask = repairedMaskData.get();
        if ((mask & (1 << pointIndex)) != 0) {
            return false;   // already repaired
        }

        ItemStack material = inputContainer.getItem(INPUT_MATERIAL_SLOT);
        if (material.getCount() < MATERIAL_COST_PER_HIT) {
            return false;
        }

        material.shrink(MATERIAL_COST_PER_HIT);
        inputContainer.setItem(INPUT_MATERIAL_SLOT, material);

        ItemStack item = inputContainer.getItem(INPUT_ITEM_SLOT);

        mask |= (1 << pointIndex);
        int repairedPoints = Integer.bitCount(mask);
        repairedMaskData.set(mask);
        repairedPointsData.set(repairedPoints);

        if (repairedPoints >= totalPoints) {
            item.setDamageValue(0);
            ItemStack repaired = item.copy();

            sessionActive = false;
            sessionInitialDamage = 0;
            totalPointsData.set(0);
            repairedPointsData.set(0);
            repairedMaskData.set(0);

            // lock BEFORE clearing the slot so slotsChanged() leaves the result alone
            repairedResultReady = true;
            inputContainer.setItem(INPUT_ITEM_SLOT, ItemStack.EMPTY);
            publishResult(repaired, 0, false, false);
        } else {
            int restorePerHit = sessionInitialDamage / totalPoints;
            int newDamage = Math.max(0, item.getDamageValue() - restorePerHit);
            item.setDamageValue(newDamage);
            inputContainer.setItem(INPUT_ITEM_SLOT, item);
        }

        return true;
    }

    // ------------------------------------------------------------------ shift-click

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack originalStack = slot.getItem();
            newStack = originalStack.copy();

            if (index == RESULT_SLOT) {
                if (!slot.mayPickup(player)) {
                    return ItemStack.EMPTY;
                }
                if (!this.moveItemStackTo(originalStack, INVENTORY_START, INVENTORY_END, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onTake(player, originalStack);
            } else if (index == INPUT_ITEM_SLOT || index == INPUT_MATERIAL_SLOT || index == TAG_SLOT) {
                if (!this.moveItemStackTo(originalStack, INVENTORY_START, INVENTORY_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // from the player's inventory
                if (originalStack.is(Items.NAME_TAG)) {
                    if (!this.moveItemStackTo(originalStack, TAG_SLOT, TAG_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(originalStack, INPUT_ITEM_SLOT, INPUT_MATERIAL_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (originalStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return newStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer) {
            // only a finished repair is a real item; every other "result" is just a preview
            if (repairedResultReady) {
                this.clearContainer(player, this.resultContainer);
            } else {
                this.resultContainer.clearContent();
            }
            this.clearContainer(player, this.inputContainer);
            this.clearContainer(player, this.tagContainer);
        }
    }
}