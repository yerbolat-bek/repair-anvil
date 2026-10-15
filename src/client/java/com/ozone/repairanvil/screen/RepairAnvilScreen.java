package com.ozone.repairanvil.screen;

import com.ozone.repairanvil.RepairAnvil;
import com.ozone.repairanvil.menu.RepairAnvilMenu;
import com.ozone.repairanvil.network.RenameItemPayload;
import com.ozone.repairanvil.network.RepairHitPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Optional;

public class RepairAnvilScreen extends AbstractContainerScreen<RepairAnvilMenu> {

    // anvil / chipped / damaged, with the painted name tag
    private static final ResourceLocation[] WITH_TAG = {
            RepairAnvil.id("textures/gui/repair_anvil_0.png"),
            RepairAnvil.id("textures/gui/repair_anvil_1.png"),
            RepairAnvil.id("textures/gui/repair_anvil_2.png")
    };
    // same stages, without the tag (shown until a Name Tag is inserted)
    private static final ResourceLocation[] NO_TAG = {
            RepairAnvil.id("textures/gui/repair_anvil_0_notag.png"),
            RepairAnvil.id("textures/gui/repair_anvil_1_notag.png"),
            RepairAnvil.id("textures/gui/repair_anvil_2_notag.png")
    };
    private static final int TEX_W = 1200;
    private static final int TEX_H = 1230;

    // enlarged item: sits on the left part of the anvil face (over the item socket),
    // so the material and result sockets stay free
    private static final int BIG_X = 38;
    private static final int BIG_Y = 39;
    private static final int BIG_SCALE = 3;
    private static final int BIG_BOX = 16 * BIG_SCALE;

    private static final double SPOT_HIT_RADIUS = 6.0;

    // progress pips in the dark groove
    private static final int PIPS_CENTER_X = 100;
    private static final int PIPS_Y = 110;
    private static final int PIP_HALF = 3;
    private static final int PIP_SPACING = 12;

    // tag slot cell (slot is at 46,22; the cell has a 1 unit border)
    private static final int TAG_CELL_X = 45;
    private static final int TAG_CELL_Y = 21;

    // name field on the wooden plank
    private static final int NAME_X = 66;
    private static final int NAME_Y = 27;
    private static final int NAME_W = 52;
    private static final int NAME_H = 10;

    private EditBox nameField;
    private long lastHitMillis = 0;

    public RepairAnvilScreen(RepairAnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 200;
        this.imageHeight = 205;
    }

    // ------------------------------------------------------------------ setup / tick

    @Override
    protected void init() {
        super.init();
        this.nameField = new EditBox(this.font, leftPos + NAME_X, topPos + NAME_Y, NAME_W, NAME_H, Component.empty());
        this.nameField.setBordered(false);
        this.nameField.setMaxLength(35);
        this.nameField.setTextColor(0xFF3B2410);   // dark brown, reads like ink on wood
        this.nameField.setResponder(this::onNameChanged);
        this.nameField.visible = false;
        this.addWidget(this.nameField);
    }

    private void onNameChanged(String text) {
        this.menu.setItemName(text);                                  // client prediction
        ClientPlayNetworking.send(new RenameItemPayload(text));       // server is the authority
    }

    private boolean hasTag() {
        return this.menu.getSlot(RepairAnvilMenu.TAG_SLOT).hasItem();
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        boolean tag = hasTag();
        if (this.nameField.visible != tag) {
            this.nameField.visible = tag;
            if (tag) {
                this.setFocused(this.nameField);
            } else {
                this.setFocused(null);
                if (!this.nameField.getValue().isEmpty()) {
                    this.nameField.setValue("");
                }
            }
        }

        // empty item slot (e.g. after taking the result) -> forget the typed name
        boolean hasItem = this.menu.getSlot(RepairAnvilMenu.INPUT_ITEM_SLOT).hasItem();
        if (!hasItem && !this.nameField.getValue().isEmpty()) {
            this.nameField.setValue("");
        }
    }

    // ------------------------------------------------------------------ background

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int stage = Mth.clamp(this.menu.getAnvilStage(), 0, WITH_TAG.length - 1);
        boolean tag = hasTag();
        ResourceLocation texture = tag ? WITH_TAG[stage] : NO_TAG[stage];

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX_W, TEX_H, TEX_W, TEX_H);

        if (!tag) {
            renderTagHint(guiGraphics);
        }
        renderProgressPips(guiGraphics);
    }

    // the artwork already contains the title area, so skip the default text labels
    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderTagHint(GuiGraphics guiGraphics) {
        int x0 = leftPos + TAG_CELL_X;
        int y0 = topPos + TAG_CELL_Y;
        int x1 = x0 + 18;
        int y1 = y0 + 18;
        int c = 0xAAE8C58A;
        guiGraphics.fill(x0, y0, x1, y1, 0x33000000);
        guiGraphics.fill(x0, y0, x1, y0 + 1, c);
        guiGraphics.fill(x0, y1 - 1, x1, y1, c);
        guiGraphics.fill(x0, y0, x0 + 1, y1, c);
        guiGraphics.fill(x1 - 1, y0, x1, y1, c);
    }

    private void renderProgressPips(GuiGraphics guiGraphics) {
        int total = this.menu.getTotalPoints();
        if (total <= 0) {
            return;
        }
        int mask = this.menu.getRepairedMask();
        int cy = topPos + PIPS_Y;
        double startX = leftPos + PIPS_CENTER_X - (total - 1) * PIP_SPACING / 2.0;

        for (int i = 0; i < total; i++) {
            int cx = (int) Math.round(startX + i * PIP_SPACING);
            boolean done = (mask & (1 << i)) != 0;
            int fill = done ? 0xFF3CB043 : 0xFFCC3333;
            guiGraphics.fill(cx - PIP_HALF - 1, cy - PIP_HALF - 1, cx + PIP_HALF + 1, cy + PIP_HALF + 1, 0xFF14110F);
            guiGraphics.fill(cx - PIP_HALF, cy - PIP_HALF, cx + PIP_HALF, cy + PIP_HALF, fill);
        }
    }

    // ------------------------------------------------------------------ enlarged item + spots

    private int bigLeft() {
        return leftPos + BIG_X;
    }

    /** a tiny downward jolt for ~90 ms after a hit */
    private int bigTop() {
        int jolt = (System.currentTimeMillis() - lastHitMillis) < 90 ? 1 : 0;
        return topPos + BIG_Y + jolt;
    }

    private void renderBigItem(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Slot itemSlot = this.menu.getSlot(RepairAnvilMenu.INPUT_ITEM_SLOT);
        if (!itemSlot.hasItem()) {
            return;
        }
        ItemStack stack = itemSlot.getItem();
        int left = bigLeft();
        int top = bigTop();

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) left, (float) top);
        guiGraphics.pose().scale((float) BIG_SCALE, (float) BIG_SCALE);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popMatrix();

        renderSpots(guiGraphics, stack, left, top, mouseX, mouseY);
        renderBarAndCount(guiGraphics, stack, left, top);
    }

    private void renderSpots(GuiGraphics guiGraphics, ItemStack stack, int left, int top, int mouseX, int mouseY) {
        int total = this.menu.getTotalPoints();
        if (total <= 0) {
            return;
        }
        List<ItemSpotLayout.Spot> spots = ItemSpotLayout.get(stack, total);
        int mask = this.menu.getRepairedMask();
        long time = System.currentTimeMillis();

        for (int i = 0; i < spots.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                continue;   // repaired spots disappear
            }
            ItemSpotLayout.Spot spot = spots.get(i);

            double dx = mouseX - (left + (spot.px() + 0.5) * BIG_SCALE);
            double dy = mouseY - (top + (spot.py() + 0.5) * BIG_SCALE);
            boolean hovered = dx * dx + dy * dy <= SPOT_HIT_RADIUS * SPOT_HIT_RADIUS;

            float pulse = 0.5f + 0.5f * (float) Math.sin(time / 220.0 + i);
            int red = hovered ? 255 : (int) (190 + 55 * pulse);
            int green = hovered ? 140 : 30;
            int edgeColor = 0xD0000000 | (red << 16) | (green << 8) | 30;
            int centreColor = 0xE8000000 | (255 << 16) | ((hovered ? 210 : 110) << 8) | 90;

            for (int[] pixel : spot.pixels()) {
                int x = left + pixel[0] * BIG_SCALE;
                int y = top + pixel[1] * BIG_SCALE;
                boolean centre = pixel[0] == spot.px() && pixel[1] == spot.py();
                guiGraphics.fill(x, y, x + BIG_SCALE, y + BIG_SCALE, centre ? centreColor : edgeColor);
            }
        }
    }

    private void renderBarAndCount(GuiGraphics guiGraphics, ItemStack stack, int left, int top) {
        if (stack.isBarVisible()) {
            int width = Math.round(BIG_BOX * stack.getBarWidth() / 13f);
            int y = top + BIG_BOX + 1;
            guiGraphics.fill(left - 1, y - 1, left + BIG_BOX + 1, y + 3, 0xFF000000);
            guiGraphics.fill(left, y, left + width, y + 2, 0xFF000000 | stack.getBarColor());
        }
        if (stack.getCount() > 1) {
            String count = String.valueOf(stack.getCount());
            guiGraphics.drawString(this.font, count,
                    left + BIG_BOX - this.font.width(count), top + BIG_BOX - 8, 0xFFFFFFFF, true);
        }
    }

    // ------------------------------------------------------------------ cost / tooltips

    private void renderCost(GuiGraphics guiGraphics) {
        int cost = this.menu.getXpCost();
        if (cost <= 0 || !this.menu.getSlot(RepairAnvilMenu.RESULT_SLOT).hasItem()) {
            return;
        }
        boolean canAfford = this.minecraft.player.getAbilities().instabuild
                || this.minecraft.player.experienceLevel >= cost;
        String text = "Cost: " + cost;
        int x = leftPos + 136 - this.font.width(text) / 2;
        int y = topPos + 68;
        guiGraphics.drawString(this.font, text, x, y, canAfford ? 0xFF7CFC00 : 0xFFFF5555, true);
    }

    private void renderTagTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Slot hovered = this.hoveredSlot;
        if (hovered != null && hovered.index == RepairAnvilMenu.TAG_SLOT
                && !hovered.hasItem() && this.menu.getCarried().isEmpty()) {
            guiGraphics.setTooltipForNextFrame(this.font,
                    List.of(Component.literal("Put a Name Tag here to rename an item")),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderBigItem(guiGraphics, mouseX, mouseY);
        this.nameField.render(guiGraphics, mouseX, mouseY, partialTick);
        renderCost(guiGraphics);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        renderTagTooltip(guiGraphics, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            return super.keyPressed(event);   // Esc still closes the screen
        }
        if (this.nameField.visible && this.nameField.isFocused()) {
            // typing: keep letters like E or digits from closing the screen / swapping hotbar slots
            this.nameField.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Slot itemSlot = this.menu.getSlot(RepairAnvilMenu.INPUT_ITEM_SLOT);

        if (itemSlot.hasItem()) {
            ItemStack stack = itemSlot.getItem();
            int left = bigLeft();
            int top = bigTop();
            int total = this.menu.getTotalPoints();

            // 1) a red spot?
            if (total > 0) {
                List<ItemSpotLayout.Spot> spots = ItemSpotLayout.get(stack, total);
                int mask = this.menu.getRepairedMask();
                for (int i = 0; i < spots.size(); i++) {
                    if ((mask & (1 << i)) != 0) {
                        continue;
                    }
                    ItemSpotLayout.Spot spot = spots.get(i);
                    double dx = event.x() - (left + (spot.px() + 0.5) * BIG_SCALE);
                    double dy = event.y() - (top + (spot.py() + 0.5) * BIG_SCALE);
                    if (dx * dx + dy * dy <= SPOT_HIT_RADIUS * SPOT_HIT_RADIUS) {
                        lastHitMillis = System.currentTimeMillis();
                        ClientPlayNetworking.send(new RepairHitPayload(i));
                        return true;
                    }
                }
            }

            // 2) the picture itself acts like the item slot (take / put down / swap)
            if (event.x() >= left && event.x() < left + BIG_BOX
                    && event.y() >= top && event.y() < top + BIG_BOX) {
                this.slotClicked(itemSlot, itemSlot.index, 0, ClickType.PICKUP);
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }
}