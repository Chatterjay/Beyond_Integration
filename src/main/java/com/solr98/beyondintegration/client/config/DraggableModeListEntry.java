package com.solr98.beyondintegration.client.config;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import me.shedaniel.clothconfig2.gui.entries.AbstractListListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 工作站切换顺序配置条目（Cloth Config）：可拖拽排序/显隐的只读列表。
 * 工作台模式数量固定（WorkstationModeConstants 定义），列表仅从固定集合中选取：
 *  - 每行左侧把手按住上下拖动调整顺序（实时重排）；
 *  - 顶部 + 按钮：追加一个尚未启用的模式（全部已启用/服务端禁用时禁用）；
 *  - 点击行获得焦点后，用列表标签行首的红叉删除该行（= 界面隐藏该模式）；
 *  - 保存值 = 自上而下的模式名列表。
 *
 * <p>服务端禁用的模式（CommandConfig workstations.enabled 未列出）不可选择、不显示；
 * 配置中已存在但被服务端禁用的模式会从列表隐藏，并由 {@link #hiddenByServer()}
 * 供分类标记说明。</p>
 */
public class DraggableModeListEntry extends AbstractListListEntry<String, DraggableModeListEntry.ModeCell, DraggableModeListEntry> {

    /** 拖动把手宽度（px） */
    public static final int HANDLE_W = 10;
    /** 行高（px，Cloth 默认 cell 高） */
    private static final int ROW_H = 20;

    private int lastRenderY;    // 本条目最近一次渲染的顶部 Y（屏幕坐标，鼠标换算行用）
    private ModeCell dragCell;  // 正在被拖拽的行（null = 未拖拽）

    public DraggableModeListEntry(Component fieldName, List<String> value, Supplier<Optional<Component[]>> tooltipSupplier,
                                  Consumer<List<String>> saveConsumer, Supplier<List<String>> defaultValue, Component resetButtonKey) {
        super(fieldName, value, true, tooltipSupplier, saveConsumer, defaultValue, resetButtonKey, false, true, false,
                (v, entry) -> new ModeCell(v, entry));
    }

    @Override public DraggableModeListEntry self() { return this; }

    @Override protected ModeCell getFromValue(String value) {
        return new ModeCell(value, this);
    }

    /** 模式名是否当前可用（服务端启用列表内） */
    private static boolean isAvailable(String name) {
        if (name == null || name.isEmpty()) return false;
        try {
            OpenStorageMenuPacket.Type t = OpenStorageMenuPacket.Type.valueOf(name.trim().toUpperCase(Locale.ROOT));
            if (t == OpenStorageMenuPacket.Type.STORAGE) return false;
            return CommandConfig.isWorkstationEnabled(t.id());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 当前已启用（且服务端仍可用）的模式（去重、保序）；服务端禁用/无效项不显示 */
    private List<String> usedModes() {
        List<String> used = new ArrayList<>();
        for (ModeCell c : cells) {
            String v = c.getValue();
            if (v == null || v.isEmpty() || used.contains(v)) continue;
            if (!isAvailable(v)) continue; // 不可用：不显示
            used.add(v);
        }
        return used;
    }

    /** 尚未启用且服务端可用的模式（按默认顺序）；不可用的不进入可选集 */
    private List<String> missingModes() {
        List<String> used = usedModes();
        List<String> missing = new ArrayList<>();
        for (OpenStorageMenuPacket.Type t : WorkstationModeConstants.MODES) {
            if (!used.contains(t.name()) && CommandConfig.isWorkstationEnabled(t.id())) missing.add(t.name());
        }
        return missing;
    }

    /** 配置中已存在但被服务端禁用（或无效）而从列表隐藏的模式名（供分类内标记说明） */
    public List<String> hiddenByServer() {
        List<String> hidden = new ArrayList<>();
        for (ModeCell c : cells) {
            String v = c.getValue();
            if (v == null || v.isEmpty() || hidden.contains(v)) continue;
            if (!isAvailable(v)) hidden.add(v);
        }
        return hidden;
    }

    /** 全部可用模式都已启用时禁用 +（无新项可加） */
    @Override public boolean isInsertButtonEnabled() {
        return super.isInsertButtonEnabled() && !missingModes().isEmpty();
    }

    /** 文本展示：合法模式名转本地化；未知残留项原样灰显 */
    private static Component displayText(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        try {
            OpenStorageMenuPacket.Type t = OpenStorageMenuPacket.Type.valueOf(v);
            if (t == OpenStorageMenuPacket.Type.STORAGE) {
                return Component.literal(raw).withStyle(ChatFormatting.GRAY);
            }
            return Component.translatable("gui.beyond_integration.mode." + t.id());
        } catch (IllegalArgumentException e) {
            return Component.literal(raw == null ? "" : raw).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
    }

    /** 重排：cell 移到目标下标（to 为移除后插入位置，0..size-1），并同步 widgets/narratables 中的 cells 区块 */
    private void moveCell(ModeCell cell, int to) {
        int from = cells.indexOf(cell);
        if (from < 0 || from == to) return;
        cells.remove(from);
        cells.add(to, cell);
        // widgets 头部（label/reset）数量固定为 widgets.size() - cells.size()（cells 恒在其后区块）
        syncBlockRefs(widgets, cell, from, to);
        syncBlockRefs(narratables, cell, from, to);
    }

    private void syncBlockRefs(List<?> refs, Object ref, int from, int to) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) refs;
        int idx = list.indexOf(ref);
        if (idx < 0) return; // 该列表未收录 cells（如 narratables 部分场景），无需同步
        int head = list.size() - cells.size(); // cells 区块之前的头部数量
        list.remove(idx);
        list.add(Math.min(head + to, list.size()), ref);
    }

    @Override public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isHovered, float delta) {
        this.lastRenderY = y;
        super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragCell != null && button == 0) {
            int size = cells.size();
            if (size > 1) {
                double dy = mouseY - (lastRenderY + 24); // 展开区从条目顶部 24px 起，每行 ROW_H
                int target = Mth.clamp((int) Math.floor(dy / ROW_H), 0, size - 1);
                moveCell(dragCell, target);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragCell != null) {
            dragCell = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 行单元：只读文本 + 左侧拖拽把手 */
    public static class ModeCell extends AbstractListListEntry.AbstractListCell<String, ModeCell, DraggableModeListEntry> {
        private String value;
        private int lastCellX;
        private int lastCellY; // 本行最近一次渲染的 Y（行命中判定用）

        public ModeCell(@Nullable String value, DraggableModeListEntry entry) {
            super(value, entry);
            // + 按钮创建（null 值）时自动取第一个未启用的可用模式
            if (value == null || value.isEmpty()) {
                List<String> missing = entry.missingModes();
                this.value = missing.isEmpty() ? "" : missing.get(0);
            } else {
                this.value = value;
            }
        }

        @Override public String getValue() { return value; }

        @Override public int getCellHeight() { return ROW_H; }

        @Override public List<? extends GuiEventListener> children() {
            return List.of(); // 只读行，无子控件
        }

        @Override public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isSelected, float delta) {
            this.lastCellX = x;
            this.lastCellY = y;
            boolean handleHover = mouseX >= x - 2 && mouseX < x + HANDLE_W + 2 && mouseY >= y && mouseY < y + ROW_H;
            if (listListEntry.dragCell == this) {
                g.fill(x - 2, y, x + entryWidth, y + ROW_H, 0x40FFFFFF); // 拖拽中行高亮
            }
            // 把手（两列点阵）
            int dotColor = (listListEntry.dragCell == this || handleHover) ? 0xFFAAAAAA : 0xFF555555;
            for (int row = 0; row < 3; row++) {
                g.fill(x + 1, y + 5 + row * 5, x + 3, y + 7 + row * 5, dotColor);
                g.fill(x + 5, y + 5 + row * 5, x + 7, y + 7 + row * 5, dotColor);
            }
            Component text = displayText(value);
            g.drawString(Minecraft.getInstance().font, text.getVisualOrderText(),
                    x + HANDLE_W + 3, y + (ROW_H - 9) / 2,
                    isSelected ? 0xffe6fe16 : getPreferredTextColor());
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 仅命中本行（Y 区间）才消费，否则放行给后续行，避免恒选第一行
            if (mouseY < lastCellY || mouseY >= lastCellY + ROW_H) {
                return false;
            }
            if (button == 0) {
                if (mouseX >= lastCellX - 2 && mouseX < lastCellX + HANDLE_W + 2) {
                    listListEntry.dragCell = this; // 按住把手开始拖拽（mouseReleased 结束）
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
            }
            return true; // 消费点击：让条目聚焦本行（供标签行红叉删除）
        }

        @Override public Optional<Component> getError() { return Optional.empty(); }

        @Override public net.minecraft.client.gui.narration.NarratableEntry.NarrationPriority narrationPriority() {
            return NarrationPriority.NONE;
        }

        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
        }
    }
}
