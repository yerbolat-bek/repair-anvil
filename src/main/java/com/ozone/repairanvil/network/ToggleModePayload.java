package com.ozone.repairanvil.network;

import com.ozone.repairanvil.RepairAnvil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ToggleModePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ToggleModePayload> TYPE =
            new CustomPacketPayload.Type<>(RepairAnvil.id("toggle_mode"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ToggleModePayload> CODEC =
            StreamCodec.unit(new ToggleModePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}