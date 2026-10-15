package com.ozone.repairanvil.client;

import com.ozone.repairanvil.RepairAnvil;
import com.ozone.repairanvil.menu.RepairAnvilMenus;
import com.ozone.repairanvil.network.ToggleModePayload;
import com.ozone.repairanvil.screen.RepairAnvilScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import org.lwjgl.glfw.GLFW;

public class RepairAnvilClient implements ClientModInitializer {

    private static final KeyMapping.Category REPAIR_ANVIL_CATEGORY =
            KeyMapping.Category.register(RepairAnvil.id("main"));

    private static KeyMapping toggleModeKey;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(RepairAnvilMenus.REPAIR_ANVIL_MENU, RepairAnvilScreen::new);

        toggleModeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.repair-anvil.toggle_mode",
                GLFW.GLFW_KEY_R,
                REPAIR_ANVIL_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleModeKey.consumeClick()) {
                ClientPlayNetworking.send(new ToggleModePayload());
            }
        });
    }
}