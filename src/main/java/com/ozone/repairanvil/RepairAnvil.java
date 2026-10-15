package com.ozone.repairanvil;

import com.ozone.repairanvil.menu.RepairAnvilMenu;
import com.ozone.repairanvil.menu.RepairAnvilMenus;
import com.ozone.repairanvil.network.RepairHitPayload;
import com.ozone.repairanvil.network.RenameItemPayload;
import com.ozone.repairanvil.network.ToggleModePayload;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepairAnvil implements ModInitializer {
	public static final String MOD_ID = "repair-anvil";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Repair Anvil mod loaded!");

		RepairAnvilMenus.register();

		// --- Сеть: клик по точке ремонта ---
		PayloadTypeRegistry.playC2S().register(RepairHitPayload.TYPE, RepairHitPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RepairHitPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().containerMenu instanceof RepairAnvilMenu repairMenu) {
					boolean success = repairMenu.tryHitPoint(payload.pointIndex());
					if (success) {
						context.player().level().playSound(
								null,
								context.player().blockPosition(),
								SoundEvents.ANVIL_USE,
								SoundSource.BLOCKS,
								1.0f, 1.0f
						);
					}
				}
			});
		});

		// --- Сеть: ввод имени предмета ---
		PayloadTypeRegistry.playC2S().register(RenameItemPayload.TYPE, RenameItemPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(RenameItemPayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				if (context.player().containerMenu instanceof RepairAnvilMenu repairMenu) {
					repairMenu.setItemName(payload.name());
				}
			});
		});

		// --- Сеть: переключение режима мод/ваниль по клавише ---
		PayloadTypeRegistry.playC2S().register(ToggleModePayload.TYPE, ToggleModePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ToggleModePayload.TYPE, (payload, context) -> {
			context.server().execute(() -> {
				var player = context.player();
				boolean newState = RepairAnvilConfig.toggle(player.getUUID());
				player.displayClientMessage(
						Component.literal(
								newState ? "Repair Anvil включён" : "Repair Anvil выключен (ванильная наковальня)"
						),
						true
				);
			});
		});

		// --- Команды ---
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("repairanvil")
					.then(Commands.literal("toggle")
							.executes(ctx -> {
								var player = ctx.getSource().getPlayerOrException();
								boolean newState = RepairAnvilConfig.toggle(player.getUUID());
								ctx.getSource().sendSuccess(() -> Component.literal(
										newState
												? "Repair Anvil включён для вас"
												: "Repair Anvil выключен для вас (используется ванильная наковальня)"
								), false);
								return 1;
							}))
					.then(Commands.literal("status")
							.executes(ctx -> {
								var player = ctx.getSource().getPlayerOrException();
								boolean enabled = RepairAnvilConfig.isEnabledFor(player.getUUID());
								ctx.getSource().sendSuccess(() -> Component.literal(
										"Repair Anvil для вас сейчас: " + (enabled ? "включён" : "выключен")
								), false);
								return 1;
							})));
		});
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}