package com.solr98.beyondintegration.feature.furnace;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * {@link TerminalSmeltRecipe} 的序列化器：解析/同步标准熔炉配方字段
 * （group/category/ingredient/result/experience/cookingtime）；
 * 配方类型固定为 {@link RecipeType#SMELTING}（普通熔炉）。
 */
public class TerminalSmeltSerializer implements RecipeSerializer<TerminalSmeltRecipe> {

    @Override
    public TerminalSmeltRecipe fromJson(ResourceLocation id, JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "");
        CookingBookCategory category = CookingBookCategory.CODEC.byName(
                GsonHelper.getAsString(json, "category", null), CookingBookCategory.MISC);
        JsonElement ingredientJson = GsonHelper.isArrayNode(json, "ingredient")
                ? GsonHelper.getAsJsonArray(json, "ingredient")
                : GsonHelper.getAsJsonObject(json, "ingredient");
        Ingredient ingredient = Ingredient.fromJson(ingredientJson, false);
        ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
        float experience = GsonHelper.getAsFloat(json, "experience", 0.0F);
        int cookingTime = GsonHelper.getAsInt(json, "cookingtime", 200);
        return new TerminalSmeltRecipe(id, group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public TerminalSmeltRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        String group = buf.readUtf();
        CookingBookCategory category = buf.readEnum(CookingBookCategory.class);
        Ingredient ingredient = Ingredient.fromNetwork(buf);
        ItemStack result = buf.readItem();
        float experience = buf.readFloat();
        int cookingTime = buf.readVarInt();
        return new TerminalSmeltRecipe(id, group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, TerminalSmeltRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());
        recipe.getIngredients().get(0).toNetwork(buf);
        buf.writeItem(recipe.getResultItem(RegistryAccess.EMPTY));
        buf.writeFloat(recipe.getExperience());
        buf.writeVarInt(recipe.getCookingTime());
    }
}
