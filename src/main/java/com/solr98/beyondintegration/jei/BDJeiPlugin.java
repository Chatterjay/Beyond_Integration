package com.solr98.beyondintegration.jei;
import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 插件入口：为合成（Crafting）与切石（Stonecutting）注册自定义配方转移处理器，
 * 使 JEI 点击配方可一键填充到对应的 BD 工作站菜单；
 * 并为 BD 终端 GUI 注册避让区域（模式按钮列 / 弹药面板 / 左侧溢出列）。
 */
@JeiPlugin
public class BDJeiPlugin implements IModPlugin {
    // 插件唯一 ID
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse(BeyondIntegration.MODID + ":jei_plugin"); }

    // 注册配方转移处理器：合成与切石
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftRecipeTransferHandler(), RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new CutRecipeTransferHandler(), RecipeTypes.STONECUTTING);
    }

    // 注册 GUI 避让：把本模组在 BD 终端 GUI 上额外绘制的区域告知 JEI
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGenericGuiContainerHandler(DimensionsNetGUI.class, new BDSidebarGuiHandler());
    }
}
