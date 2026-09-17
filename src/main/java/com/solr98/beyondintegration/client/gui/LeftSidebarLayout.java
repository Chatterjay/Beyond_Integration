package com.solr98.beyondintegration.client.gui;

import com.wintercogs.beyonddimensions.client.gui.widget.LeftButtonSidebar;
import net.minecraft.client.gui.components.AbstractButton;

import java.util.ArrayList;
import java.util.List;

/**
 * BD 终端左侧按钮栏接管布局器。
 * <p>
 * 在 BD 自身 init 完成、本模组按钮全部加入侧栏后调用：
 * <ol>
 *   <li>按“BD 原有按钮始终在最前（保持添加原序）、本模组按钮排后”重排；</li>
 *   <li>主列自侧栏顶部向下堆叠（间距 2），放不下的部分（会超过 GUI 底边）
 *       移到左侧新增一列（右缘对齐主列左缘 - 2），实现向左侧扩展一列按钮空位。</li>
 * </ol>
 * 坐标直接 setX/setY，不调用 arrangeElements()，避免 BD 布局类
 * （1.20.1 GridLayout / 1.21.1 LinearLayout）按原顺序回排。
 */
public final class LeftSidebarLayout {

    /** 按钮纵向间距（与 BD 侧栏一致） */
    private static final int GAP = 2;

    private LeftSidebarLayout() {}

    /**
     * 应用接管布局。
     *
     * @param sidebar    BD 左侧按钮栏（位置由 BD 设置为 guiLeft-18, guiTop+6）
     * @param biButtons  本模组接管的按钮（置顶，按传入顺序）
     * @param maxBottom  可用区域底边（guiTop + ySize，即 GUI 图像底部）
     */
    public static void apply(LeftButtonSidebar sidebar, List<? extends AbstractButton> biButtons, int maxBottom) {
        if (sidebar == null) return;
        List<AbstractButton> tracked = ((BeyondSidebarAccess) sidebar).beyond$trackedButtons();

        // 重排：BD 原有按钮始终在最前（保持添加原序），本模组接管的按钮排在其后
        List<AbstractButton> ordered = new ArrayList<>(tracked.size());
        for (AbstractButton b : tracked) {
            if (!biButtons.contains(b)) ordered.add(b);
        }
        for (AbstractButton b : biButtons) {
            if (b != null && tracked.contains(b) && !ordered.contains(b)) ordered.add(b);
        }

        int x = sidebar.getX();
        int y = sidebar.getY();
        int available = maxBottom - y;

        // 主列：自顶部向下堆叠，第一个放不下的按钮及其后全部进入左侧溢出列
        List<AbstractButton> main = new ArrayList<>();
        List<AbstractButton> overflow = new ArrayList<>();
        int used = 0;
        boolean spill = false;
        for (AbstractButton b : ordered) {
            int h = b.getHeight();
            if (!spill && used + h <= available) {
                main.add(b);
                if (h > 0) used += h + GAP;
            } else {
                spill = true;
                overflow.add(b);
            }
        }

        int cy = y;
        for (AbstractButton b : main) {
            b.setX(x);
            b.setY(cy);
            if (b.getHeight() > 0) cy += b.getHeight() + GAP;
        }

        // 溢出列：右缘对齐主列左缘 - 2（16px 按钮即 x = 主列 - 18，向左侧扩展一列）
        int rightEdge = x - GAP;
        cy = y;
        for (AbstractButton b : overflow) {
            b.setX(rightEdge - b.getWidth());
            b.setY(cy);
            if (b.getHeight() > 0) cy += b.getHeight() + GAP;
        }
    }
}
