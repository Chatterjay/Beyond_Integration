package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.furnace.TerminalSmeltRecipe;
import com.solr98.beyondintegration.feature.furnace.TerminalSmeltSerializer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 配方序列化器注册中心：注册熔炉烧终端定制配方（terminal_smelt）。
 */
public class ModRecipes {

    /** 配方序列化器注册表 */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, BeyondIntegration.MODID);

    /** 熔炉烧终端配方（数据包 JSON：beyond_integration:terminal_smelt） */
    public static final RegistryObject<RecipeSerializer<TerminalSmeltRecipe>> TERMINAL_SMELT =
            RECIPE_SERIALIZERS.register("terminal_smelt", TerminalSmeltSerializer::new);

    private ModRecipes() {}

    public static void register(IEventBus bus) {
        RECIPE_SERIALIZERS.register(bus);
    }
}
