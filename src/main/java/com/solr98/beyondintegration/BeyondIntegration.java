package com.solr98.beyondintegration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.feature.ammo.sw.SwAmmoPollingService;
import com.solr98.beyondintegration.feature.vehicle.VehicleInteractHandler;
import com.wintercogs.beyonddimensions.common.item.NetedItem;
import org.slf4j.Logger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

import com.solr98.beyondintegration.client.ClientRegistrar;
import com.solr98.beyondintegration.init.ModMenus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Beyond Integration 模组主类（Forge 1.20.1）。
 * 负责模组初始化：注册菜单、通用配置、事件总线监听，
 * 以及按依赖模组（tacz / superbwarfare / beyonddimensions）条件注册
 * 各功能处理器（物品黑名单、弹药箱提取、SW 弹药轮询、载具绑定、哨戒炮绑定等）。
 */
// The value here should match an entry in the META-INF/mods.toml file
@Mod(BeyondIntegration.MODID)
public class BeyondIntegration {

    // Define mod id in a common place for everything to reference
    public static final String MODID = "beyond_integration";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 模组构造器：注册 mod 总线事件、菜单、Forge 事件总线与通用配置 */
    @SuppressWarnings("removal")
    public BeyondIntegration() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register menu types
        ModMenus.register(modEventBus);

        // Register recipe serializers (furnace terminal smelt-all)
        com.solr98.beyondintegration.init.ModRecipes.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register common config (synced to client)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommandConfig.SERVER_SPEC);
        // Register client-only config (GUI preferences, e.g. TACZ smith table network mode)
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_SPEC);

        // Register Cloth Config screen (client-only)
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientRegistrar::register);

        // 配置文件注释统一：监听配置加载/重载缓存 COMMON 配置，加载完成后按需重写
        modEventBus.addListener(this::onModConfigLoading);
        modEventBus.addListener(this::onModConfigReloading);
        modEventBus.addListener(this::onLoadComplete);
    }

    /** common 阶段初始化：按依赖条件注册全部功能处理器与网络包 */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册充电平台实现（能量 capability / 模组判定 / 属性判定）
        com.solr98.beyondintegration.core.energy.ChargePlatform.set(
                new com.solr98.beyondintegration.core.energy.ForgeChargePlatform());
        registerItemBlacklistHandler();
        registerEnchantmentBookSeparator();
        registerAmmoBoxExtractHandler();
        registerSuperbAmmoInsertHandler();
        registerSwAmmoPollingService();
        // 通用装备位网络充电（可充电盔甲 + 主副手工具/武器），独立于 SW 轮询按配置间隔执行
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent tickEvent) -> {
            if (tickEvent.phase != TickEvent.Phase.END) return;
            com.solr98.beyondintegration.feature.ammo.sw.EnergyAmmoChargeHandler
                    .tick(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        });
        registerVehicleInteractHandler();
        MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.AutoTotemHandler());
        // setHealth 复活实现（事件版 + Mixin 版）：配置关闭时不注册处理器（类不加载，零事件开销）
        if (CommandConfig.reviveEventEnabled() || CommandConfig.reviveMixinEnabled()) {
            MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.revive.ReviveFingerprintHandler());
            LOGGER.info("Registered ReviveFingerprintHandler");
        }
        if (CommandConfig.reviveEventEnabled()) {
            MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.revive.SetHealthReviveHandler());
            LOGGER.info("Registered SetHealthReviveHandler (event)");
        }
        if (ModList.get().isLoaded("touhou_little_maid")) {
            MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.MaidAutoTotemHandler());
            MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.maid.MaidEnergyChargeHandler());
            LOGGER.info("Registered MaidAutoTotemHandler");
        }
        com.solr98.beyondintegration.network.PacketHandler.register();
        registerTaczTrackerDrain();
        registerSubscriptionHubCleanup();
    }

    /** 缓存的 COMMON 配置对象（供加载完成后统一注释使用）。 */
    private net.minecraftforge.fml.config.ModConfig commonConfig;
    /** 缓存的 CLIENT 配置对象（供加载完成后统一注释使用）。 */
    private net.minecraftforge.fml.config.ModConfig clientConfig;

    /** 配置加载时记录 COMMON/CLIENT 配置实例。 */
    private void onModConfigLoading(net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) {
        trackConfig(event.getConfig());
    }

    /** 配置重载时记录 COMMON/CLIENT 配置实例。 */
    private void onModConfigReloading(net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
        trackConfig(event.getConfig());
    }

    private void trackConfig(net.minecraftforge.fml.config.ModConfig config) {
        if (!MODID.equals(config.getModId())) {
            return;
        }
        if (config.getType() == net.minecraftforge.fml.config.ModConfig.Type.COMMON) {
            this.commonConfig = config;
        } else if (config.getType() == net.minecraftforge.fml.config.ModConfig.Type.CLIENT) {
            this.clientConfig = config;
        }
    }

    /** 模组加载完成后：COMMON/CLIENT 配置注释语言与 command_language 不一致时重写（值保持不变）。 */
    private void onLoadComplete(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        com.solr98.beyondintegration.core.config.ConfigCommentLang.saveIfLanguageChanged(this.commonConfig);
        com.solr98.beyondintegration.core.config.ConfigCommentLang.saveIfLanguageChanged(this.clientConfig);
    }

    /** 注册统一订阅中心的全局清理（网络销毁 / 服务器停止时批量退订全部 BD 订阅） */
    private void registerSubscriptionHubCleanup() {
        MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.event.server.ServerStoppingEvent event) -> {
                com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub.clearAll();
            }
        );
        // 服务器停止时显式落盘 beyond_integration_attachments.dat（NetworkAmmoData 持久化）
        MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.event.server.ServerStoppingEvent event) -> {
                com.solr98.beyondintegration.handler.NetworkDataStore.saveNow();
            }
        );
        MinecraftForge.EVENT_BUS.addListener(
            (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) -> {
                com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub.onNetDestroyed(event.getDestroyedId());
            }
        );
        LOGGER.info("Registered BdSubscriptionHub cleanup");
    }

    /** 注册附魔分离插入拦截器（多附魔书/附魔物品进网络时自动分离） */
    private void registerEnchantmentBookSeparator() {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler());
        LOGGER.info("Registered EnchantmentBookSeparatorHandler");
    }

    /** 注册物品黑名单插入拦截器 */
    private void registerItemBlacklistHandler() {
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.feature.blacklist.ItemBlacklistHandler());
        LOGGER.info("Registered ItemBlacklistHandler");
    }

    /** 注册 TACZ 弹药箱自动提取拦截器（仅在 tacz 加载时） */
    private void registerAmmoBoxExtractHandler() {
        if (ModList.get().isLoaded("tacz")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.feature.ammo.tacz.AmmoBoxExtractHandler());
            LOGGER.info("Registered AmmoBoxExtractHandler");
        }
    }

    /** 注册 SW 弹药插入拦截器（仅在 superbwarfare 加载时） */
    private void registerSuperbAmmoInsertHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.feature.ammo.sw.SuperbAmmoInsertHandler());
            LOGGER.info("Registered SuperbAmmoInsertHandler");
        }
    }

    /**
     * 注册 SW 虚拟弹药轮询服务：每间隔推送差异，并在
     * 服务器停止 / 网络销毁时清理弹药追踪数据。
     */
    private void registerSwAmmoPollingService() {
        if (ModList.get().isLoaded("superbwarfare")) {
            MinecraftForge.EVENT_BUS.register(new SwAmmoPollingService());
            MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppingEvent event) -> {
                    com.solr98.beyondintegration.feature.ammo.sw.SwAmmoTracker.clear();
                }
            );
            MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() != null) {
                        com.solr98.beyondintegration.feature.ammo.sw.SwAmmoPollingService
                                .onPlayerLoggedOut(event.getEntity().getUUID());
                    }
                }
            );
            MinecraftForge.EVENT_BUS.addListener(
                (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) -> {
                    com.solr98.beyondintegration.feature.ammo.sw.SwAmmoTracker.removeById(event.getDestroyedId());
                }
            );
            LOGGER.info("Registered SwAmmoPollingService");
        }
    }

    /** 注册载具右键绑定处理器（仅在 superbwarfare 加载时） */
    private void registerVehicleInteractHandler() {
        if (ModList.get().isLoaded("superbwarfare")) {
            MinecraftForge.EVENT_BUS.register(new VehicleInteractHandler());
            LOGGER.info("Registered VehicleInteractHandler");
        }
    }

    /**
     * 注册 TACZ 弹药追踪服务：周期性扫描网络弹药并推送，
     * 并在服务器停止 / 网络销毁 / 玩家登出时清理对应追踪数据。
     */
    private void registerTaczTrackerDrain() {
        if (ModList.get().isLoaded("tacz")) {
            MinecraftForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoPollingService());
            MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.server.ServerStoppingEvent event) -> {
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoTracker.clear();
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoPollingService.clear();
                    com.solr98.beyondintegration.feature.ammo.tacz.PlayerNetUsageTracker.clear();
                }
            );
            MinecraftForge.EVENT_BUS.addListener(
                (com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed event) -> {
                    com.solr98.beyondintegration.feature.ammo.tacz.TaczAmmoTracker.removeById(event.getDestroyedId());
                    NetworkAmmoData.remove(event.getDestroyedId());
                }
            );
            MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) -> {
                    if (event.getEntity() != null) {
                        com.solr98.beyondintegration.feature.ammo.tacz.PlayerNetUsageTracker.remove(event.getEntity().getUUID());
                    }
                }
            );
            LOGGER.info("Registered TaczAmmoPollingService");
        }
    }

    /**
     * 左键点击哨戒机械臂（sentrymechanicalarm）事件：
     * 手持已接入网络的物品时，将网络 ID 绑定到哨戒炮
     * （反射调用其 getHeldItem / addAmmoBox），成功后扣除玩家物品。
     */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null || !"sentrymechanicalarm".equals(blockId.getNamespace())) return;

        Player player = event.getEntity();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.isEmpty() || NetedItem.getNetId(stack) < 0) return;

        event.setCanceled(true);

        if (event.getSide().isClient()) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        if (be instanceof SentryNetIdAccessor accessor) {
            accessor.setSentryNetId(NetedItem.getNetId(stack));
        }

        try {
            Method getHeld = be.getClass().getMethod("getHeldItem");
            ItemStack held = (ItemStack) getHeld.invoke(be);
            if (held.isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_no_gun"), true);
                return;
            }

            Method addBox = be.getClass().getMethod("addAmmoBox", ItemStack.class);
            boolean ok = (boolean) addBox.invoke(be, stack);
            if (ok) {
                if (!player.isCreative()) stack.shrink(1);
                int boundNetId = NetedItem.getNetId(stack);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.beyond_integration.sentry_net_bound", boundNetId),
                        true);
            } else {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("sentry.tooltip.ammobox_1"), true);
            }
        } catch (Exception ignored) {}
    }

    /** 主世界加载时初始化网络弹药持久化数据（先兼容迁移旧存档，再按统一键名加载） */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            com.solr98.beyondintegration.handler.NetworkDataStore.migrateLegacy(serverLevel.getServer());
            NetworkAmmoData.initialize(serverLevel);
        }
    }

    /** 主世界保存时标记网络弹药数据为待保存 */
    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.dimension() == ServerLevel.OVERWORLD) {
            NetworkAmmoData.markDirty();
        }
    }

}
