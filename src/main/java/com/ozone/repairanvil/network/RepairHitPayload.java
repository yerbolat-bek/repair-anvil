package com.ozone.repairanvil.network;

import com.ozone.repairanvil.RepairAnvil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RepairHitPayload(int pointIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RepairHitPayload> TYPE =
            new CustomPacketPayload.Type<>(RepairAnvil.id("repair_hit"));

    public static final StreamCodec<ByteBuf, RepairHitPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RepairHitPayload::pointIndex,
            RepairHitPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}