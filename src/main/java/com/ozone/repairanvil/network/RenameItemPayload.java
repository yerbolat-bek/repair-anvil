package com.ozone.repairanvil.network;

import com.ozone.repairanvil.RepairAnvil;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RenameItemPayload(String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameItemPayload> TYPE =
            new CustomPacketPayload.Type<>(RepairAnvil.id("rename_item"));

    public static final StreamCodec<ByteBuf, RenameItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RenameItemPayload::name,
            RenameItemPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}