package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.config.ModConfigScreen;
import com.solr98.beyondintegration.client.gui.DimensionsAnvilGUI;
import com.solr98.beyondintegration.client.gui.DimensionsCraftGUI;
import com.solr98.beyondintegration.client.gui.DimensionsCutGUI;
import com.solr98.beyondintegration.client.gui.DimensionsEnchantApothGUI;
import com.solr98.beyondintegration.client.gui.DimensionsEnchantGUI;
import com.solr98.beyondintegration.client.gui.DimensionsGrindGUI;
import com.solr98.beyondintegration.client.gui.DimensionsSmithGUI;
import com.solr98.beyondintegration.handler.ItemTooltipHandler;
import com.solr98.beyondintegration.init.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = BeyondIntegration.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
/**
 * 客户端事件处理器（MOD 总线）。
 * 负责：注册配置界面与客户端监听、注册各工作站容器界面、
 * 注册快捷键映射，以及每 tick 处理换枪弹药现查/下车缓存清理。
 */
public class ClientEventHandler {

    @SubscribeEvent
    /** 通用初始化：注册配置界面工厂、客户端 tick 监听、TACZ 客户端注册器与 GUI 扩展 */
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        var mc = net.neoforged.fml.ModList.get().getModContainerById(BeyondIntegration.MODID);
        mc.ifPresent(c -> {
            ModContainer container = c;
            if (ModList.get().isLoaded("cloth_config")) {
                container.registerExtensionPoint(IConfigScreenFactory.class,
                        (screen, parent) -> ModConfigScreen.createScreen(parent));
            }
        });
        NeoForge.EVENT_BUS.register(new ItemTooltipHandler());
        // 悬停可存入网络的槽位左上角 "+" 角标（客户端渲染）
        NeoForge.EVENT_BUS.addListener(HoverStoreOverlay::onScreenRender);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> {
            BDKeyBindings.handleTick();
            handleClientTick();
        });
        com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry.ensureRegistered();
        if (ModList.get().isLoaded("tacz")) {
            // 通过反射注册 TACZ 客户端监听（避免强依赖导致崩溃）
            try {
                Class.forName("com.solr98.beyondintegration.client.TaczClientRegistrar")
                        .getMethod("register")
                        .invoke(null);
            } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    /** 注册各工作站的菜单界面（附魔台分原版/神化两个 GUI） */
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ANVIL.get(), DimensionsAnvilGUI::new);
        event.register(ModMenus.CUT.get(), DimensionsCutGUI::new);
        event.register(ModMenus.GRIND.get(), DimensionsGrindGUI::new);
        event.register(ModMenus.SMITH.get(), DimensionsSmithGUI::new);
        event.register(ModMenus.CRAFT.get(), DimensionsCraftGUI::new);
        event.register(ModMenus.ENCHANT.get(), DimensionsEnchantGUI::new);
        event.register(ModMenus.ENCHANT_APOTH.get(), DimensionsEnchantApothGUI::new);
    }

    @SubscribeEvent
    /** 注册全部工作站快捷键 */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(BDKeyBindings.OPEN_CRAFT);
        event.register(BDKeyBindings.OPEN_CUT);
        event.register(BDKeyBindings.OPEN_SMITH);
        event.register(BDKeyBindings.OPEN_GRIND);
        event.register(BDKeyBindings.OPEN_ANVIL);
        event.register(BDKeyBindings.OPEN_ENCHANT);
    }

    // ── SW：换枪现查 ITEM 弹药 / 下车清除载具缓存 ──
    private static net.minecraft.world.item.ItemStack lastMainHandItem = net.minecraft.world.item.ItemStack.EMPTY;
    private static net.minecraft.world.entity.Entity lastVehicle = null;

    /** 每 tick：检测下车（清载具缓存）与主手武器切换（ITEM 型弹药现查） */
    private static void handleClientTick() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            lastMainHandItem = net.minecraft.world.item.ItemStack.EMPTY;
            lastVehicle = null;
            return;
        }

        // 下车：清除载具网络缓存（仅客户端）
        net.minecraft.world.entity.Entity vehicle = player.getVehicle();
        if (lastVehicle != null && vehicle == null) {
            SuperbAmmoCache.INSTANCE.clearVehicleData();
        }
        lastVehicle = vehicle;

        if (!net.neoforged.fml.ModList.get().isLoaded("superbwarfare")) return;

        // 换枪：主手武器变化且为 ITEM 型弹药 → 现查
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.getItem() != lastMainHandItem.getItem()) {
            lastMainHandItem = held.copy();
            int netId = SuperbAmmoCache.INSTANCE.getNetId();
            if (netId < 0) return;
            try {
                if (held.getItem() instanceof com.atsuishio.superbwarfare.item.gun.GunItem) {
                    com.atsuishio.superbwarfare.data.gun.GunData data =
                            com.atsuishio.superbwarfare.data.gun.GunData.from(held);
                    if (data != null) {
                        com.atsuishio.superbwarfare.data.gun.AmmoConsumer consumer = data.selectedAmmoConsumer();
                        if (consumer != null
                                && consumer.getType() == com.atsuishio.superbwarfare.data.gun.AmmoConsumer.AmmoConsumeType.ITEM
                                && !consumer.stack().isEmpty()) {
                            var regKey = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(consumer.stack().getItem());
                            if (regKey != null) {
                                SuperbAmmoCache.INSTANCE.requestItems(netId,
                                        java.util.Collections.singletonList("ITEM:" + regKey), false);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
