package com.solr98.beyondintegration;

import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.VehicleInteractHandler;
import com.solr98.beyondintegration.handler.YwzjVehicleSyncHandler;
import org.slf4j.Logger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/**
 * 模组主入口类：负责模组初始化、注册事件监听器与配置文件，
 * 并根据已加载的联动模组（如 ywzj_vehicle、superbwarfare、tacz）按条件注册对应的处理逻辑。
 */
@Mod(BeyondIntegration.MODID)
public class BeyondIntegration {

    /** 模组 ID，用于注册与资源定位。 */
    public static final String MODID = "beyond_integration";
    /** 模组专用日志记录器。 */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数：注册 MOD 生命周期事件、菜单注册、命令注册监听器，
     * 并注册 COMMON 类型配置文件。
     */
    public BeyondIntegration(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Mod constructing");

        // 注册 FML 通用初始化阶段回调（FMLCommonSetupEvent）
        modEventBus.addListener(FMLCommonSetupEvent.class, this::commonSetup);

        com.solr98.beyondintegration.init.ModMenus.MENUS.register(modEventBus);

        // 配方序列化器（熔炉烧终端：terminal_smelt）
        com.solr98.beyondintegration.init.ModRecipes.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, this::onRegisterCommands);
        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommandConfig.SERVER_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.CLIENT_SPEC);

        // 配置文件注释统一：加载/重载缓存 COMMON 配置，加载完成后按需重写为英文注释
        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, this::onModConfigLoading);
        modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class, this::onModConfigReloading);
        modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent.class, this::onLoadComplete);
    }

    /**
     * 通用初始化阶段：根据已加载的模组按条件注册各联动处理器，
     * 包括维度网络插入拦截、载具交互、图腾自动使用、弹药轮询等。
     */
    public void commonSetup(final FMLCommonSetupEvent event) {
        // 注册充电平台实现（能量 capability / 模组判定 / 属性判定）
        com.solr98.beyondintegration.core.energy.ChargePlatform.set(
                new com.solr98.beyondintegration.core.energy.NeoChargePlatform());
        // 注册维度网络存储插入前拦截器：禁止黑名单物品进入网络
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.handler.ItemBlacklistHandler());
        // 附魔分离：多附魔书/附魔物品进网络时自动分离（网络级开关 + 配置双重控制）
        com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                .addHandler(new com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler());
        LOGGER.info("Registered ItemBlacklistHandler");

        // VehicleInteractHandler uses reflection to detect SW/ywzj vehicles,
        // so it is safe to register regardless of which mods are loaded.
        NeoForge.EVENT_BUS.register(new VehicleInteractHandler());
        LOGGER.info("Registered VehicleInteractHandler");

        // 注册自动使用图腾处理器（仅从维度网络取用图腾）
        NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.AutoTotemHandler());
        LOGGER.info("Registered AutoTotemHandler");

        // setHealth 复活实现（事件版 + Mixin 版）：配置关闭时不注册处理器（类不加载，零事件开销）
        if (CommandConfig.reviveEventEnabled() || CommandConfig.reviveMixinEnabled()) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.revive.ReviveFingerprintHandler());
            LOGGER.info("Registered ReviveFingerprintHandler");
        }
        if (CommandConfig.reviveEventEnabled()) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.revive.SetHealthReviveHandler());
            LOGGER.info("Registered SetHealthReviveHandler (event)");
        }

        // 若已加载 touhou_little_maid 模组：注册女仆版自动网络图腾
        if (ModList.get().isLoaded("touhou_little_maid")) {
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.feature.totem.MaidAutoTotemHandler());
            NeoForge.EVENT_BUS.register(new com.solr98.beyondintegration.maid.MaidEnergyChargeHandler());
            LOGGER.info("Registered MaidAutoTotemHandler");
        }

        // 若已加载 ywzj_vehicle 模组，则注册载具同步处理器
        if (ModList.get().isLoaded("ywzj_vehicle")) {
            NeoForge.EVENT_BUS.register(new YwzjVehicleSyncHandler());
            LOGGER.info("Registered YwzjVehicleSyncHandler");
        }

        // 若已加载 superbwarfare（SW）模组：注册弹药轮询服务、追踪清理与插入拦截
        if (ModList.get().isLoaded("superbwarfare")) {
            com.solr98.beyondintegration.handler.SwAmmoPollingService swPolling =
                    new com.solr98.beyondintegration.handler.SwAmmoPollingService();
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,
                    swPolling::onServerTick);
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent.class,
                    swPolling::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class,
                    swPolling::onPlayerTick);
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                    e -> {
                        if (e.getEntity() != null) {
                            com.solr98.beyondintegration.handler.SwAmmoPollingService
                                    .onPlayerLoggedOut(e.getEntity().getUUID());
                        }
                    });
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class,
                    e -> com.solr98.beyondintegration.handler.SwAmmoTracker.clear());
            NeoForge.EVENT_BUS.addListener(com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed.class,
                    e -> com.solr98.beyondintegration.handler.SwAmmoTracker.removeById(e.getDestroyedId()));
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.handler.SuperbAmmoInsertHandler());
            LOGGER.info("Registered SW handlers");
        }

        // 若已加载 tacz 模组：注册弹药盒提取拦截、弹药轮询与玩家网络用量跟踪
        if (ModList.get().isLoaded("tacz")) {
            com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler
                    .addHandler(new com.solr98.beyondintegration.handler.AmmoBoxExtractHandler());
            com.solr98.beyondintegration.handler.TaczAmmoPollingService polling =
                    new com.solr98.beyondintegration.handler.TaczAmmoPollingService();
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,
                    polling::onServerTick);
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class,
                    e -> {
                        com.solr98.beyondintegration.handler.TaczAmmoTracker.clear();
                        com.solr98.beyondintegration.handler.TaczAmmoPollingService.clear();
                        com.solr98.beyondintegration.handler.PlayerNetUsageTracker.clear();
                    });
            NeoForge.EVENT_BUS.addListener(com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed.class,
                    e -> {
                        com.solr98.beyondintegration.handler.TaczAmmoTracker.removeById(e.getDestroyedId());
                        com.solr98.beyondintegration.handler.NetworkAmmoData.get().remove(e.getDestroyedId());
                    });
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.class,
                    e -> com.solr98.beyondintegration.handler.PlayerNetUsageTracker.remove(e.getEntity().getUUID()));
            // TaCZ 拔枪/换弹监听移入独立类：避免主类被 EventBus.register 反射扫描时
            // 加载 TaCZ 事件类型（未装 TaCZ 时 NoClassDefFoundError，见 TaczEventHandler 类注释）
            com.solr98.beyondintegration.handler.TaczEventHandler.registerEvents();
            LOGGER.info("Registered TACZ handlers");
        }

        // 统一订阅中心全局清理：网络销毁 / 服务器停止时批量退订全部 BD 订阅
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class,
                e -> com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub.clearAll());
        // 服务器停止时显式落盘 beyond_integration_data.dat（NetworkAmmoData 持久化）
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppingEvent.class,
                e -> com.solr98.beyondintegration.handler.NetworkDataStore.saveNow());
        NeoForge.EVENT_BUS.addListener(com.wintercogs.beyonddimensions.api.event.dimensionnet.DimensionsNetEvent.Destroyed.class,
                e -> com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub.onNetDestroyed(e.getDestroyedId()));
        LOGGER.info("Registered BdSubscriptionHub cleanup");

        // 熔炉烧终端：取出终端产物时触发网络批量烧炼
        NeoForge.EVENT_BUS.addListener(
                com.solr98.beyondintegration.handler.FurnaceTerminalEventHandler::onItemSmelted);

        // 通用装备位网络充电（可充电盔甲 + 主副手工具/武器），独立于 SW 轮询按配置间隔执行
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,
                e -> com.solr98.beyondintegration.handler.EnergyAmmoChargeHandler
                        .tick(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()));
    }

    /** 缓存的 COMMON 配置对象（供加载完成后统一注释使用）。 */
    private ModConfig commonConfig;
    /** 缓存的 CLIENT 配置对象（供加载完成后统一注释使用）。 */
    private ModConfig clientConfig;

    /** 配置加载时记录 COMMON/CLIENT 配置实例。 */
    private void onModConfigLoading(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        trackConfig(event.getConfig());
    }

    /** 配置重载时记录 COMMON/CLIENT 配置实例。 */
    private void onModConfigReloading(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        trackConfig(event.getConfig());
    }

    private void trackConfig(ModConfig config) {
        if (!MODID.equals(config.getModId())) {
            return;
        }
        if (config.getType() == ModConfig.Type.COMMON) {
            this.commonConfig = config;
        } else if (config.getType() == ModConfig.Type.CLIENT) {
            this.clientConfig = config;
        }
    }

    /** 模组加载完成后：COMMON/CLIENT 配置注释语言与 command_language 不一致时重写（值保持不变）。 */
    private void onLoadComplete(net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent event) {
        com.solr98.beyondintegration.core.config.ConfigCommentLang.saveIfLanguageChanged(this.commonConfig);
        com.solr98.beyondintegration.core.config.ConfigCommentLang.saveIfLanguageChanged(this.clientConfig);
    }

    /**
     * 命令注册事件回调：将命令注册委托给 BDNetworkCommands。
     */
    public void onRegisterCommands(RegisterCommandsEvent event) {
        com.solr98.beyondintegration.command.BDNetworkCommands.onRegisterCommands(event);
    }

    /** 服务器启动事件：仅记录日志。 */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Beyond Integration server starting");
    }
}

