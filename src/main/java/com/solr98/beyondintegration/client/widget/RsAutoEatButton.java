package com.solr98.beyondintegration.client.widget;

import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * rs_integration（RI）自动进食功能替代按钮（16×16，BD slot_button 纹理 + 物品图标）。
 * RI 原生的 EAT/SELECT/MODE（及折叠 MENU）按钮已被本模组屏蔽（不渲染、不可点），
 * 由本按钮以 BD 侧栏按钮形式反射调用 RI 对应入口实现同样功能：
 * <ul>
 *   <li>EAT：发送 AutoEatPacket 触发网络进食；</li>
 *   <li>SELECT：打开 RI 的自动进食选择/黑名单界面；</li>
 *   <li>MODE：循环切换进食模式，tooltip 显示当前模式。</li>
 * </ul>
 */
public class RsAutoEatButton extends Button {

    /** 按钮角色（对应 RI 的三个功能入口） */
    public enum Role { EAT, SELECT, MODE }

    private static final ResourceLocation SLOT = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    private static final ResourceLocation SLOT_HOVERED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");

    private final Role role;
    /** MODE 按钮缓存的模式显示名，变化时刷新 tooltip */
    private String lastModeName;

    public RsAutoEatButton(Role role, int x, int y) {
        super(x, y, 16, 16, Component.empty(), b -> RsAutoEatButton.press(role), DEFAULT_NARRATION);
        this.role = role;
        updateTooltip();
    }

    /** 点击分发到 RI 对应入口（反射，RI 缺失时静默） */
    private static void press(Role role) {
        switch (role) {
            case EAT -> RsIntegrationCompat.sendAutoEat();
            case SELECT -> RsIntegrationCompat.openAutoEatScreen();
            case MODE -> RsIntegrationCompat.cycleAutoEatMode();
        }
    }

    @Override
    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        // MODE 按钮：模式可能在 RI 界面内被修改，渲染时检测变化并刷新 tooltip
        if (role == Role.MODE) refreshModeTooltip();
        g.blit(isHovered ? SLOT_HOVERED : SLOT, getX(), getY(), 0, 0, 16, 16, 16, 16);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(getX() + 1, getY() + 1, 1);
        pose.scale(0.85f, 0.85f, 1);
        g.renderFakeItem(icon(), 0, 0);
        pose.popPose();
    }

    /** 角色对应图标 */
    private ItemStack icon() {
        return switch (role) {
            case EAT -> new ItemStack(Items.GOLDEN_CARROT);
            case SELECT -> new ItemStack(Items.BOOK);
            case MODE -> new ItemStack(Items.COMPARATOR);
        };
    }

    /** MODE 模式名变化时刷新 tooltip */
    private void refreshModeTooltip() {
        var name = RsIntegrationCompat.autoEatModeName();
        String text = name == null ? "" : name.getString();
        if (!text.equals(lastModeName)) {
            lastModeName = text;
            updateTooltip();
        }
    }

    /** 按角色与当前模式刷新 tooltip 文本 */
    public void updateTooltip() {
        switch (role) {
            case EAT -> setTooltip(Tooltip.create(Component.translatable("gui.beyond_integration.rs_autoeat_eat")));
            case SELECT -> setTooltip(Tooltip.create(Component.translatable("gui.beyond_integration.rs_autoeat_select")));
            case MODE -> {
                var name = RsIntegrationCompat.autoEatModeName();
                setTooltip(Tooltip.create(Component.translatable("gui.beyond_integration.rs_autoeat_mode",
                        name == null ? Component.empty() : name)));
            }
        }
    }
}
