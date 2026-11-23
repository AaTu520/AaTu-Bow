package cn.blockforge.aatu_bow;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A start/refresh or stop notification, not a stream of server-calculated particles. */
public record LingeringEffectPayload(int targetId, UUID targetUuid, UUID sourceUuid,
                                     byte weaponType, int remainingTicks, int lineCount)
        implements CustomPacketPayload {
    public static final Type<LingeringEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AatuBow.MOD_ID, "lingering_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LingeringEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.targetId());
                buffer.writeUUID(payload.targetUuid());
                buffer.writeUUID(payload.sourceUuid());
                buffer.writeByte(payload.weaponType());
                buffer.writeVarInt(payload.remainingTicks());
                buffer.writeVarInt(payload.lineCount());
            },
            buffer -> new LingeringEffectPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(),
                    buffer.readByte(), buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
