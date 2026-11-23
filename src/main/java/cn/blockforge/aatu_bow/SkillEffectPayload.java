package cn.blockforge.aatu_bow;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;

public record SkillEffectPayload(int entityId, byte weaponType, int length, int width, int height,
                                 int particleLifetimeTicks)
        implements CustomPacketPayload {
    public static final Type<SkillEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AatuBow.MOD_ID, "skill_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId());
                buffer.writeByte(payload.weaponType());
                buffer.writeVarInt(payload.length());
                buffer.writeVarInt(payload.width());
                buffer.writeVarInt(payload.height());
                buffer.writeVarInt(payload.particleLifetimeTicks());
            },
            buffer -> new SkillEffectPayload(
                    buffer.readVarInt(),
                    buffer.readByte(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
