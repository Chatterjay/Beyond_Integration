package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.crafting.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 菜单类型注册中心：通过 DeferredRegister 注册全部网络工作站菜单
 * （storage 纯存储 / anvil 铁砧 / cut 切石 / grind 砂轮 / smith 锻造 / craft 合成）。
 */
public class ModMenus {
    // 菜单类型注册表（注册到 Forge 事件总线）
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, BeyondIntegration.MODID);
    // 纯网络存储菜单
    public static final RegistryObject<MenuType<DimensionsStorageMenu>> STORAGE = MENU_TYPES.register("storage",()->IForgeMenuType.create(DimensionsStorageMenu::new));
    // 铁砧菜单（网络 XP 扣费）
    public static final RegistryObject<MenuType<DimensionsAnvilMenu>> ANVIL = MENU_TYPES.register("anvil",()->IForgeMenuType.create(DimensionsAnvilMenu::new));
    // 切石机菜单（自动补料）
    public static final RegistryObject<MenuType<DimensionsCutMenu>> CUT = MENU_TYPES.register("cut",()->IForgeMenuType.create(DimensionsCutMenu::new));
    // 砂轮菜单
    public static final RegistryObject<MenuType<DimensionsGrindMenu>> GRIND = MENU_TYPES.register("grind",()->IForgeMenuType.create(DimensionsGrindMenu::new));
    // 锻造台菜单（自动补料）
    public static final RegistryObject<MenuType<DimensionsSmithMenu>> SMITH = MENU_TYPES.register("smith",()->IForgeMenuType.create(DimensionsSmithMenu::new));
    // 合成台菜单
    public static final RegistryObject<MenuType<DimensionsCraftMenu>> CRAFT = MENU_TYPES.register("craft",()->IForgeMenuType.create(DimensionsCraftMenu::new));
    // 附魔台菜单（原版增强：功率配置/升级覆盖/预览刷新）
    public static final RegistryObject<MenuType<DimensionsEnchantMenu>> ENCHANT = MENU_TYPES.register("enchant",()->IForgeMenuType.create(DimensionsEnchantMenu::new));
    // 注册到事件总线
    public static void register(IEventBus b){MENU_TYPES.register(b);}
}
