package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record CherryLightningPayload(Vec3 origin, List<Vec3> targets) implements CustomPacketPayload {
    public static final int MAX_TARGETS = 12;
    public CherryLightningPayload { targets = List.copyOf(targets); }

    public static final Type<CherryLightningPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AatuBow.MOD_ID, "cherry_lightning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CherryLightningPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                CherryRainPayload.writeVec(buffer, payload.origin());
                buffer.writeVarInt(payload.targets().size());
                for (Vec3 target : payload.targets()) CherryRainPayload.writeVec(buffer, target);
            },
            buffer -> {
                Vec3 origin = CherryRainPayload.readVec(buffer);
                int count = buffer.readVarInt();
                if (count < 0 || count > MAX_TARGETS) throw new IllegalArgumentException("Invalid lightning target count");
                List<Vec3> targets = new ArrayList<>(count);
                for (int index = 0; index < count; index++) targets.add(CherryRainPayload.readVec(buffer));
                return new CherryLightningPayload(origin, targets);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
