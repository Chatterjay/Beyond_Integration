package com.solr98.beyondintegration.init;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.feature.furnace.TerminalSmeltRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 配方序列化器注册中心：注册熔炉烧终端定制配方（terminal_smelt）。
 * 复用原版 {@link SimpleCookingSerializer}（1.21.1 的 Factory 接口为 public）。
 */
public class ModRecipes {

    /** 配方序列化器注册表 */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, BeyondIntegration.MODID);

    /** 熔炉烧终端配方（数据包 JSON：beyond_integration:terminal_smelt） */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TerminalSmeltRecipe>> TERMINAL_SMELT =
            RECIPE_SERIALIZERS.register("terminal_smelt",
                    () -> new SimpleCookingSerializer<>(TerminalSmeltRecipe::new, 200));

    private ModRecipes() {}

    public static void register(IEventBus bus) {
        RECIPE_SERIALIZERS.register(bus);
    }
}
