package com.ozone.repairanvil.mixin;

import com.ozone.repairanvil.RepairAnvil;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilScreenHandlerMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void repairAnvil$onCreate(CallbackInfo ci) {
        RepairAnvil.LOGGER.info("AnvilMenu Mixin loaded!");
    }
}