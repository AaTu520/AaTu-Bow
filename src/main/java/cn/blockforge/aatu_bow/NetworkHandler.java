package cn.blockforge.aatu_bow;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class NetworkHandler {
    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("4").playToClient(
                SkillEffectPayload.TYPE,
                SkillEffectPayload.STREAM_CODEC,
                NetworkHandler::handleSkillEffect)
                .playToClient(LingeringEffectPayload.TYPE, LingeringEffectPayload.STREAM_CODEC,
                        NetworkHandler::handleLingeringEffect)
                .playToClient(CherryRainPayload.TYPE, CherryRainPayload.STREAM_CODEC,
                        NetworkHandler::handleCherryRain)
                .playToClient(CherryLightningPayload.TYPE, CherryLightningPayload.STREAM_CODEC,
                        NetworkHandler::handleCherryLightning);
    }

    private static void handleSkillEffect(CustomPacketPayload payload, IPayloadContext context) {
        SkillEffectPayload effect = (SkillEffectPayload) payload;
        context.enqueueWork(() -> SkillEffects.spawnFromPacket(
                context.player(),
                effect.entityId(),
                effect.weaponType(),
                effect.length(),
                effect.width(),
                effect.height(),
                effect.particleLifetimeTicks()));
    }

    private static void handleLingeringEffect(LingeringEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLingeringEffects.handle(context.player(), payload));
    }

    private static void handleCherryRain(CherryRainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCherryEffects.handleRain(context.player(), payload));
    }

    private static void handleCherryLightning(CherryLightningPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCherryEffects.handleLightning(context.player(), payload));
    }
}
