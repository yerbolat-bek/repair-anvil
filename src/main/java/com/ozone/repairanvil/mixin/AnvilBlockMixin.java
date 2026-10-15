package com.ozone.repairanvil.mixin;

import com.ozone.repairanvil.menu.RepairAnvilMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.Blocks;

@Mixin(AnvilBlock.class)
public class AnvilBlockMixin {

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
    private void repairAnvil$onUseWithoutItem(BlockState state, Level level, BlockPos pos,
                                            Player player, BlockHitResult hit,
                                            CallbackInfoReturnable<InteractionResult> cir) {
        if (!com.ozone.repairanvil.RepairAnvilConfig.isEnabledFor(player.getUUID())) {
            return; // этот игрок выбрал ванильную наковальню — пропускаем нашу логику
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int stage = state.is(Blocks.DAMAGED_ANVIL) ? 2 : state.is(Blocks.CHIPPED_ANVIL) ? 1 : 0;
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (syncId, inventory, p) -> {
                        RepairAnvilMenu menu = new RepairAnvilMenu(syncId, inventory);
                        menu.setAnvilStage(stage);
                        return menu;
                    },
                    Component.literal("Repair Anvil")
            ));
        }
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}