package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.client.gui.BeyondSidebarAccess;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.client.gui.widget.LeftButtonSidebar;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI 避让：把本模组在 BD 终端 GUI 上额外绘制的区域告知 JEI，避免 JEI 物品列表覆盖这些元素。
 * <p>
 * 覆盖范围：
 * <ol>
 *   <li>右侧工作站模式按钮列（本模组自绘）；</li>
 *   <li>右侧 Superb Warfare 弹药面板（本模组自绘）；</li>
 *   <li>左侧接管布局的溢出列（BD 自身只避让侧栏布局区域，不含被本模组移到左列的按钮）。</li>
 * </ol>
 * BD 自身的 {@code JeiContainerHandler} 与本 handler 可同时注册，JEI 会合并两者的避让区域。
 */
public class BDSidebarGuiHandler implements IGuiContainerHandler<DimensionsNetGUI<?>> {

    /** 弹药面板格子尺寸与间距（与 DimensionsNetGUIMixin 保持一致） */
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 2;

    @Override
    public List<Rect2i> getGuiExtraAreas(DimensionsNetGUI<?> gui) {
        List<Rect2i> areas = new ArrayList<>();

        // 1) 右侧工作站模式按钮列（按当前可用模式数量计算实际高度）
        int modeCount = WorkstationModeConstants.availableModes().size();
        if (modeCount > 0) {
            int gy = gui.getGuiTop() + 24 + 18 + (menuLines(gui) - 2) * 18 + 26;
            int bx = gui.getGuiLeft() + WorkstationModeConstants.xFor(0);
            int bottom = gy + WorkstationModeConstants.yFor(modeCount - 1) + 16;
            areas.add(new Rect2i(bx, gy, 16, bottom - gy));
        }

        // 2) 右侧弹药面板（仅 SW 加载且缓存有效时绘制）
        if (ModList.get().isLoaded("superbwarfare")
                && SuperbAmmoCache.INSTANCE.hasData() && SuperbAmmoCache.INSTANCE.getNetId() >= 0) {
            int panelX = gui.getGuiLeft() + gui.getXSize() + 4;
            int panelY = gui.getGuiTop() + 8;
            int panelH = 5 * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
            areas.add(new Rect2i(panelX, panelY, SLOT_SIZE, panelH));
        }

        // 3) 左侧接管布局的溢出列
        Rect2i overflow = overflowArea(gui);
        if (overflow != null) areas.add(overflow);

        return areas;
    }

    /** 当前菜单行数（读取失败时按默认 6 行估算） */
    private static int menuLines(DimensionsNetGUI<?> gui) {
        try {
            DimensionsNetMenu menu = gui.getMenu();
            if (menu != null) return menu.getLines();
        } catch (Throwable ignored) {}
        return 6;
    }

    /** 左侧溢出列区域（本模组布局器把放不下的按钮移到主列左侧，BD 自身不覆盖该区域） */
    private static Rect2i overflowArea(DimensionsNetGUI<?> gui) {
        LeftButtonSidebar sidebar = sidebarOf(gui);
        if (sidebar == null) return null;
        List<AbstractButton> tracked = ((BeyondSidebarAccess) sidebar).beyond$trackedButtons();

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (AbstractButton b : tracked) {
            if (!b.visible) continue;
            // 主列按钮由 BD 自身避让，仅统计被移到左侧的溢出按钮
            if (b.getX() >= sidebar.getX()) continue;
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            maxX = Math.max(maxX, b.getX() + b.getWidth());
            maxY = Math.max(maxY, b.getY() + b.getHeight());
        }
        return minX == Integer.MAX_VALUE ? null : new Rect2i(minX, minY, maxX - minX, maxY - minY);
    }

    /** 反射读取 BD GUI 的左侧按钮栏字段（BD 模组类字段名不混淆） */
    private static LeftButtonSidebar sidebarOf(DimensionsNetGUI<?> gui) {
        try {
            var field = DimensionsNetGUI.class.getDeclaredField("leftButtonSidebar");
            field.setAccessible(true);
            return (LeftButtonSidebar) field.get(gui);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
