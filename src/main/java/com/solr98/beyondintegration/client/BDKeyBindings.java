package com.solr98.beyondintegration.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.client.gui.WorkstationTransferHelper;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 工作站快捷键管理（客户端）。
 * 定义 6 个可配置按键（合成/切割/锻造/磨石/铁砧/附魔台），默认未绑定，
 * 在客户端 tick 中监听触发，向服务端发送打开对应工作站的请求。
 */
// 6 个工作站快捷键（默认未绑定，可在控制设置中分配）
public final class BDKeyBindings {
    /** 按键所属分类（控制设置中显示） */
    public static final String CATEGORY = "key.categories.beyond_integration";

    /** 六个工作站打开快捷键：合成 / 切割 / 锻造 / 磨石 / 铁砧 / 附魔台（初始均为未绑定） */
    public static final KeyMapping OPEN_CRAFT = new KeyMapping("key.beyond_integration.open_craft", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_CUT = new KeyMapping("key.beyond_integration.open_cut", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_SMITH = new KeyMapping("key.beyond_integration.open_smith", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_GRIND = new KeyMapping("key.beyond_integration.open_grind", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_ANVIL = new KeyMapping("key.beyond_integration.open_anvil", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_ENCHANT = new KeyMapping("key.beyond_integration.open_enchant", InputConstants.UNKNOWN.getValue(), CATEGORY);

    private BDKeyBindings() {}

    /** 每客户端 tick 检测各快捷键是否被按下，触发对应工作站打开请求 */
    public static void handleTick() {
        check(OPEN_CRAFT, OpenStorageMenuPacket.Type.CRAFT);
        check(OPEN_CUT, OpenStorageMenuPacket.Type.CUT);
        check(OPEN_SMITH, OpenStorageMenuPacket.Type.SMITH);
        check(OPEN_GRIND, OpenStorageMenuPacket.Type.GRIND);
        check(OPEN_ANVIL, OpenStorageMenuPacket.Type.ANVIL);
        check(OPEN_ENCHANT, OpenStorageMenuPacket.Type.ENCHANT);
    }

    /** 单个按键检查：按下时若处于 BD 网络界面先保存切换上下文，再发打开请求 */
    private static void check(KeyMapping key, OpenStorageMenuPacket.Type type) {
        if (!key.consumeClick()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        // 服务端配置未启用该工作台：本地提示，不发送请求（服务端仍会兜底校验）
        if (!CommandConfig.isWorkstationEnabled(type.id())) {
            player.displayClientMessage(Component.translatable(
                    "message.beyond_integration.workstation.disabled",
                    Component.translatable("gui.beyond_integration.mode." + type.id())), true);
            return;
        }
        // 若当前处于 BD 网络界面，保存翻页/鼠标状态便于切换
        if (player.containerMenu instanceof DimensionsNetMenu menu)
            WorkstationTransferHelper.save(menu);
        PacketHandler.sendToServer(new OpenStorageMenuPacket(type));
    }
}
