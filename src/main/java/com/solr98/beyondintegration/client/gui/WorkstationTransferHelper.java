package com.solr98.beyondintegration.client.gui;

import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.util.UIDataHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;

// 统一使用 BD 的 UIDataHelper 保存界面切换上下文（翻页位置 + 鼠标物理坐标）
/**
 * 工作站切换上下文保存工具。
 * 通过 BD 的 UIDataHelper 记录当前网络菜单的翻页位置与鼠标坐标，
 * 使快捷键切换工作站后能无缝恢复界面状态。
 */
public final class WorkstationTransferHelper {
    /** 保存当前菜单上下文并标记为切换流程 */
    public static void save(DimensionsNetMenu menu) {
        UIDataHelper.currentPage = menu.lineData;
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(Minecraft.getInstance().getWindow().getWindow(), x, y);
        UIDataHelper.lastMousePos = new Vec2((float) x[0], (float) y[0]);
        UIDataHelper.isTransfer = true;
    }

    /** 清除切换标记 */
    public static void clearPending() {
        UIDataHelper.isTransfer = false;
    }

    private WorkstationTransferHelper() {}
}
