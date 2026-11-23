package cn.blockforge.aatu_bow;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record CherryRainPayload(Vec3 center, Vec3 direction, double radius, double height, int lifetimeTicks)
        implements CustomPacketPayload {
    public static final Type<CherryRainPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AatuBow.MOD_ID, "cherry_rain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CherryRainPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                writeVec(buffer, payload.center());
                writeVec(buffer, payload.direction());
                buffer.writeDouble(payload.radius());
                buffer.writeDouble(payload.height());
                buffer.writeVarInt(payload.lifetimeTicks());
            },
            buffer -> new CherryRainPayload(readVec(buffer), readVec(buffer), buffer.readDouble(),
                    buffer.readDouble(), buffer.readVarInt()));

    static void writeVec(RegistryFriendlyByteBuf buffer, Vec3 position) {
        buffer.writeDouble(position.x);
        buffer.writeDouble(position.y);
        buffer.writeDouble(position.z);
    }

    static Vec3 readVec(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
