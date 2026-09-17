package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.init.DimensionsCutMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import java.util.List;

/**
 * 维度网络切石机工作站界面。
 * 在工作站面板中渲染配方网格与滚动条，支持鼠标点击选中配方、
 * 拖动/滚轮滚动配方列表，行为对齐原版切石机界面。
 */
public class DimensionsCutGUI extends DimensionsStorageGUI<DimensionsCutMenu> {
    private static final ResourceLocation BG = ResourceLocation.parse("beyond_integration:textures/gui/stonecutter.png"); // 面板底图
    private static final ResourceLocation BTN = ResourceLocation.parse("beyond_integration:textures/gui/stonecutter_button.png"); // 配方格按钮贴图
    private static final ResourceLocation TH = ResourceLocation.parse("beyond_integration:textures/gui/scroll_thumb.png"); // 滚动条滑块贴图
    private static final int GRID_X = 52, GRID_Y = 3, GRID_COLS = 4, GRID_ROWS = 3, CELL_W = 16, CELL_H = 18; // 配方网格布局参数
    private static final int SCROLL_X = 119, THUMB_W = 12, THUMB_H = 15; // 滚动条位置与滑块尺寸
    private static final int GRID_H = GRID_ROWS * CELL_H; // 网格总高
    private float scrollOffs; // 滚动偏移量（0~1）
    private boolean scrolling; // 是否正在拖动滚动条

    public DimensionsCutGUI(DimensionsCutMenu c, Inventory p, Component t) { super(c, p, t); }

    /** 计算最大可滚动行数（配方行数超出网格行数部分） */
    private int getMaxScrollRows() { int r = this.menu.getNumRecipes(); return Math.max(0, (r + GRID_COLS - 1) / GRID_COLS - GRID_ROWS); }
    // 原版：round 后乘 4（非 floor）
    /** 当前滚动位置对应的起始配方索引 */
    private int getStartIdx() { return (int)((double)(scrollOffs * (float)getMaxScrollRows()) + 0.5D) * GRID_COLS; }

    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY(); g.blit(BG, this.leftPos, gy, 0, 0, 176, 62, 176, 62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.cut"), this.leftPos + 6, gy - 7, 0x404040, false);
        // 渲染配方网格与滚动条
        List<StonecutterRecipe> recipes = this.menu.getRecipes();
        int num = recipes.size(), sel = this.menu.getSelectedRecipeIndex();
        if (sel < 0 || sel >= num) sel = -1;
        // 原版行为：无论是否有输入/配方都渲染滚动条（禁用态 u=12），行程 41 与原版 k=41*scrollOffs 一致
        boolean hasInput = !this.menu.getInput().isEmpty();
        boolean scrollActive = hasInput && num > GRID_ROWS * GRID_COLS;
        int ty = gy + GRID_Y + (scrollActive ? (int) (41.0F * scrollOffs) : 0);
        g.blit(TH, this.leftPos + SCROLL_X, ty, scrollActive ? 0 : 12, 0, THUMB_W, THUMB_H, 24, 15);
        if (!hasInput || num <= 0) return;
        int start = getStartIdx();
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int mx = (int)(Minecraft.getInstance().mouseHandler.xpos() / guiScale), my = (int)(Minecraft.getInstance().mouseHandler.ypos() / guiScale);
        for (int r = 0; r < GRID_ROWS; r++) for (int c = 0; c < GRID_COLS; c++) {
            int idx = start + r * GRID_COLS + c, bx = this.leftPos + GRID_X + c * CELL_W, by = gy + GRID_Y + r * CELL_H;
            boolean isSel = idx == sel, isHov = mx >= bx && mx < bx + CELL_W && my >= by && my < by + CELL_H;
            // 纹理三态：0=可用 18=已选择 36=悬浮高亮
            g.blit(BTN, bx, by, 0, isHov ? 36 : isSel ? 18 : 0, 16, 18, 16, 54);
            if (idx < num) {
                ItemStack output = recipes.get(idx).getResultItem(Minecraft.getInstance().level.registryAccess());
                g.renderFakeItem(output, bx, by + 1);
            }
        }
    }

    // 配方 tooltip 在 render 末尾渲染（对齐原版 StonecutterScreen），避免被后续渲染（BD 控件/面板 blit）覆盖
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (this.menu.getInput().isEmpty()) return;
        List<StonecutterRecipe> recipes = this.menu.getRecipes();
        int num = recipes.size();
        if (num <= 0) return;
        int gy = getGapY();
        int start = getStartIdx();
        for (int r = 0; r < GRID_ROWS; r++) for (int c = 0; c < GRID_COLS; c++) {
            int idx = start + r * GRID_COLS + c;
            if (idx >= num) continue;
            int bx = this.leftPos + GRID_X + c * CELL_W, by = gy + GRID_Y + r * CELL_H;
            if (mx >= bx && mx < bx + CELL_W && my >= by && my < by + CELL_H) {
                ItemStack output = recipes.get(idx).getResultItem(Minecraft.getInstance().level.registryAccess());
                g.renderTooltip(Minecraft.getInstance().font, output, mx, my);
                return;
            }
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int gy = getGapY();
        // 原版：滚动条点击区从轨道顶上方 6px 开始（StonecutterScreen: j = topPos + 9）
        if (this.menu.getNumRecipes() > GRID_ROWS * GRID_COLS) { int sx = this.leftPos + SCROLL_X, sy = gy + GRID_Y - 6; if (mx >= sx && mx < sx + THUMB_W && my >= sy && my < sy + GRID_H) { scrolling = true; return true; } }
        int rx = this.leftPos + GRID_X, ry = gy + GRID_Y;
        // 点击配方格：选中配方（本地立即刷新 + 服务端确认）
        if (mx >= rx && mx < rx + GRID_COLS * CELL_W && my >= ry && my < ry + GRID_ROWS * CELL_H) {
            int col = (int)((mx - rx) / CELL_W), row = (int)((my - ry) / CELL_H), idx = getStartIdx() + row * GRID_COLS + col;
            List<StonecutterRecipe> recipes = this.menu.getRecipes();
            if (idx < recipes.size()) {
                // 原版行为：先本地选中配方（立即刷新结果），再发服务端
                if (this.menu.clickMenuButton(this.minecraft.player, idx)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    if (Minecraft.getInstance().gameMode != null)
                        Minecraft.getInstance().gameMode.handleInventoryButtonClick(this.menu.containerId, idx);
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        // 拖动滚动条滑块：按鼠标 Y 更新滚动偏移
        if (scrolling && btn == 0 && getMaxScrollRows() > 0) { scrollOffs = Mth.clamp((float)(my - (getGapY() + GRID_Y + THUMB_H / 2.0F)) / (GRID_H - THUMB_H), 0.0F, 1.0F); return true; }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) { if (btn == 0) scrolling = false; return super.mouseReleased(mx, my, btn); } // 松开左键结束拖动

    @Override public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // 双滚动条聚焦：鼠标在配方网格/滚动条区域 → 滚配方；否则交给父类（BD 存储滚动条）
        int gy = getGapY();
        boolean inRecipeArea = mx >= this.leftPos + GRID_X && mx < this.leftPos + SCROLL_X + THUMB_W
                && my >= gy + GRID_Y - 6 && my < gy + GRID_Y + GRID_H;
        if (inRecipeArea && this.menu.getNumRecipes() > GRID_ROWS * GRID_COLS) {
            int max = getMaxScrollRows();
            if (max > 0) { scrollOffs = Mth.clamp(scrollOffs - (float)(scrollY / max), 0.0F, 1.0F); return true; }
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}


