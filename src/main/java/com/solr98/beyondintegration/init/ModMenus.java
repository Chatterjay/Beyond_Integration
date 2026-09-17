package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/**
 * 菜单类型注册表：集中注册六个工作站菜单（存储/铁砧/合成/切石/磨石/锻造），
 * 通过 IMenuTypeExtension.create 将各菜单构造器绑定到 MenuType 供网络包重建菜单。
 */
public class ModMenus {
    // 菜单类型的延迟注册表（需在 Mod 构造器中 register 到事件总线）
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, BeyondIntegration.MODID);

    // 七个工作站菜单：STORAGE 存储 / ANVIL 铁砧 / CRAFT 合成 / CUT 切石 / GRIND 磨石 / SMITH 锻造 / ENCHANT 附魔台
    public static final Supplier<MenuType<DimensionsStorageMenu>> STORAGE = MENUS.register("storage",
            () -> IMenuTypeExtension.create(DimensionsStorageMenu::new));
    public static final Supplier<MenuType<DimensionsAnvilMenu>> ANVIL = MENUS.register("anvil",
            () -> IMenuTypeExtension.create(DimensionsAnvilMenu::new));
    public static final Supplier<MenuType<DimensionsCraftMenu>> CRAFT = MENUS.register("craft",
            () -> IMenuTypeExtension.create(DimensionsCraftMenu::new));
    public static final Supplier<MenuType<DimensionsCutMenu>> CUT = MENUS.register("cut",
            () -> IMenuTypeExtension.create(DimensionsCutMenu::new));
    public static final Supplier<MenuType<DimensionsGrindMenu>> GRIND = MENUS.register("grind",
            () -> IMenuTypeExtension.create(DimensionsGrindMenu::new));
    public static final Supplier<MenuType<DimensionsSmithMenu>> SMITH = MENUS.register("smith",
            () -> IMenuTypeExtension.create(DimensionsSmithMenu::new));
    public static final Supplier<MenuType<DimensionsEnchantMenu>> ENCHANT = MENUS.register("enchant",
            () -> IMenuTypeExtension.create(DimensionsEnchantMenu::new));
    public static final Supplier<MenuType<DimensionsApothEnchantMenu>> ENCHANT_APOTH = MENUS.register("enchant_apoth",
            () -> IMenuTypeExtension.create(DimensionsApothEnchantMenu::new));
}
