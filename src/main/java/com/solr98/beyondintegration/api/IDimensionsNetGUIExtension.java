package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 维度网络 GUI 扩展接口：允许其他模组以钩子方式扩展维度网络界面的
 * 初始化、渲染与鼠标点击行为，按优先级（priority）依次调用。
 */
public interface IDimensionsNetGUIExtension {

    /** 界面初始化时回调 */
    default void onInit(DimensionsNetGUI<?> gui) {}

    /** 界面渲染时回调（每帧），mx/my 为鼠标坐标，pt 为部分渲染帧时间 */
    default void onRender(DimensionsNetGUI<?> gui, GuiGraphics g, int mx, int my, float pt) {}

    /** 鼠标点击回调，返回 true 表示已处理该次点击 */
    default boolean onMouseClicked(DimensionsNetGUI<?> gui, double mx, double my, int button) {
        return false;
    }

    /** 扩展优先级，数值越大越先执行（默认 50） */
    default int priority() { return 50; }
}
