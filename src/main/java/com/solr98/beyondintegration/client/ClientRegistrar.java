package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.client.config.ModConfigScreen;
import com.solr98.beyondintegration.client.gui.*;
import com.solr98.beyondintegration.feature.crafting.*;
import com.solr98.beyondintegration.init.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;

/**
 * 客户端注册中心：负责注册工作站菜单界面、快捷键映射与 Cloth Config 配置界面，
 * 并监听客户端 tick 与实体加入等事件，维护弹药缓存（换枪现查/下车清除）。
 */
public class ClientRegistrar {

    /** 注册菜单界面工厂、快捷键、配置界面及 tick/实体加入等客户端事件 */
    @OnlyIn(Dist.CLIENT) @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,()->new ConfigScreenHandler.ConfigScreenFactory((mc,parent)->{if(ModList.get().isLoaded("cloth_config")){try{return ModConfigScreen.createScreen(parent);}catch(NoClassDefFoundError e){}}return parent;}));
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e)->{e.enqueueWork(()->{
            MenuScreens.<DimensionsStorageMenu,DimensionsStorageGUI<DimensionsStorageMenu>>register(ModMenus.STORAGE.get(),(a,b,c)->new DimensionsStorageGUI<>(a,b,c));
            MenuScreens.register(ModMenus.ANVIL.get(),DimensionsAnvilGUI::new);
            MenuScreens.register(ModMenus.CUT.get(),DimensionsCutGUI::new);
            MenuScreens.register(ModMenus.GRIND.get(),DimensionsGrindGUI::new);
            MenuScreens.register(ModMenus.SMITH.get(),DimensionsSmithGUI::new);
            MenuScreens.register(ModMenus.CRAFT.get(),DimensionsCraftGUI::new);
        MenuScreens.register(ModMenus.ENCHANT.get(),DimensionsEnchantGUI::new);
        });});
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e)->{
            e.register(BDKeyBindings.OPEN_CRAFT);
            e.register(BDKeyBindings.OPEN_CUT);
            e.register(BDKeyBindings.OPEN_SMITH);
            e.register(BDKeyBindings.OPEN_GRIND);
            e.register(BDKeyBindings.OPEN_ANVIL);
            e.register(BDKeyBindings.OPEN_ENCHANT);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e)->{
            if (e.phase == TickEvent.Phase.END) {
                BDKeyBindings.handleTick();
                handleClientTick();
            }
        });
        // SW / 工作台缓存：进服与登出时清空，防止旧网络快照残留（netId 复用显示旧数据 / 内存增长）
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent ev) -> {
            if (ev.getLevel().isClientSide() && ev.getEntity() == net.minecraft.client.Minecraft.getInstance().player) {
                SuperbAmmoCache.clear();
                NetworkItemCache.clear();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(
            (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut ev) -> {
                SuperbAmmoCache.clear();
                NetworkItemCache.clear();
            }
        );
        if(ModList.get().isLoaded("tacz")){
            MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent ev)->{
                if(ev.getLevel().isClientSide() && ev.getEntity()==net.minecraft.client.Minecraft.getInstance().player){
                    TaczAmmoCache.clear();
                    TaczAmmoCache.requestQuick(null);
                }
            });
            MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut ev) ->
                    TaczAmmoCache.clear()
            );
        }
    }

    // ── SW：换枪现查 ITEM 弹药 / 下车清除载具缓存 ──
    /** 上一次主手物品（用于检测换枪） */
    private static ItemStack lastMainHandItem = ItemStack.EMPTY;
    /** 上一次乘骑的载具实体（用于检测下车） */
    private static Entity lastVehicle = null;

    /** 客户端每 tick：检测下车清除载具缓存；主手换枪且为 ITEM 型弹药时现查计数 */
    @OnlyIn(Dist.CLIENT)
    private static void handleClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            lastMainHandItem = ItemStack.EMPTY;
            lastVehicle = null;
            return;
        }

        // 下车：清除载具网络缓存（仅客户端）
        Entity vehicle = player.getVehicle();
        if (lastVehicle != null && vehicle == null) {
            SuperbAmmoCache.clearVehicleData();
        }
        lastVehicle = vehicle;

        if (!ModList.get().isLoaded("superbwarfare")) return;

        // 换枪：主手武器变化且为 ITEM 型弹药 → 现查
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != lastMainHandItem.getItem()) {
            lastMainHandItem = held.copy();
            int netId = SuperbAmmoCache.getNetId();
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
                            var regKey = ForgeRegistries.ITEMS.getKey(consumer.stack().getItem());
                            if (regKey != null) {
                                SuperbAmmoCache.requestItems(netId,
                                        Collections.singletonList("ITEM:" + regKey), false);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
