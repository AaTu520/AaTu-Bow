package cn.blockforge.aatu_bow;

import cn.blockforge.aatu_bow.compat.SpellEngineCompat;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AatuBow.MOD_ID)
public final class AatuBow {
    public static final String MOD_ID = "aatu_bow";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<AatuBowItem> AATU_BOW = ITEMS.register(
            "aatu_bow",
            () -> new AatuBowItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(512)
                    .rarity(Rarity.RARE)));
    public static final DeferredItem<AatuSwordItem> AATU_SWORD = ITEMS.register(
            "aatu_sword",
            () -> new AatuSwordItem(Tiers.DIAMOND, new Item.Properties()
                    .stacksTo(1)
                    .durability(1561)
                    .rarity(Rarity.RARE)));

    public AatuBow(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        // Keep the established file name so existing server settings continue to work,
        // but register it as SERVER so NeoForge sends the authoritative values to clients.
        modContainer.registerConfig(ModConfig.Type.SERVER, AatuConfig.SPEC, "aatu_bow-common.toml");
        modEventBus.addListener(NetworkHandler::register);
        modEventBus.addListener(AatuBow::onCommonSetup);
        modEventBus.addListener(AatuBow::addCreativeTabContents);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(FusionEvents::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(FusionEvents::onArrowNock);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FusionEvents::onUseItemStop);
        NeoForge.EVENT_BUS.addListener(FusionEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CherryProjectileService::onArrowLoose);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CherryProjectileService::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CherryProjectileService::onImpact);
        NeoForge.EVENT_BUS.addListener(CherryProjectileService::onLevelTick);
        NeoForge.EVENT_BUS.addListener(CherryRainService::onLevelTick);
        NeoForge.EVENT_BUS.addListener(CherryProjectileService::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(CherryRainService::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(CherryProjectileService::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CherryRainService::onServerStopped);
        NeoForge.EVENT_BUS.addListener(LingeringSkillService::onLevelTick);
        NeoForge.EVENT_BUS.addListener(LingeringSkillService::onStartTracking);
        NeoForge.EVENT_BUS.addListener(LingeringSkillService::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(LingeringSkillService::onServerStopped);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("spell_engine")) {
            event.enqueueWork(SpellEngineCompat::initialize);
        }
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.COMBAT)) {
            event.accept(new ItemStack(AATU_BOW.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.accept(new ItemStack(AATU_SWORD.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
