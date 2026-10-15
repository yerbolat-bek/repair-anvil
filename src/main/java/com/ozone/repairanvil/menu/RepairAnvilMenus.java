package com.ozone.repairanvil.menu;

import com.ozone.repairanvil.RepairAnvil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;


public class RepairAnvilMenus {

    public static MenuType<RepairAnvilMenu> REPAIR_ANVIL_MENU;

    public static void register() {
        REPAIR_ANVIL_MENU = net.minecraft.core.Registry.register(
                BuiltInRegistries.MENU,
                RepairAnvil.id("repair_anvil"),
                new MenuType<>(RepairAnvilMenu::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET)
        );
    }
}