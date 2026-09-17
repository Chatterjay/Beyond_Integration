package com.solr98.beyondintegration.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.client.gui.WorkstationTransferHelper;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.OpenStorageMenuPayload;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

// 6 个工作站快捷键（默认未绑定，可在控制设置中分配）
/**
 * 工作站快捷键定义与触发处理。
 * 定义 6 个工作站打开快捷键（默认未绑定），
 * 按下时若处于 BD 网络界面先保存翻页状态，再向服务端发送打开对应工作站菜单的请求。
 */
public final class BDKeyBindings {
    public static final String CATEGORY = "key.categories.beyond_integration"; // 按键分类翻译键

    public static final KeyMapping OPEN_CRAFT = new KeyMapping("key.beyond_integration.open_craft", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_CUT = new KeyMapping("key.beyond_integration.open_cut", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_SMITH = new KeyMapping("key.beyond_integration.open_smith", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_GRIND = new KeyMapping("key.beyond_integration.open_grind", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_ANVIL = new KeyMapping("key.beyond_integration.open_anvil", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping OPEN_ENCHANT = new KeyMapping("key.beyond_integration.open_enchant", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

    private BDKeyBindings() {}

    /** 每 tick 检查各快捷键按下状态并触发对应工作站 */
    public static void handleTick() {
        check(OPEN_CRAFT, WorkstationModeConstants.Mode.CRAFT);
        check(OPEN_CUT, WorkstationModeConstants.Mode.CUT);
        check(OPEN_SMITH, WorkstationModeConstants.Mode.SMITH);
        check(OPEN_GRIND, WorkstationModeConstants.Mode.GRIND);
        check(OPEN_ANVIL, WorkstationModeConstants.Mode.ANVIL);
        check(OPEN_ENCHANT, WorkstationModeConstants.Mode.ENCHANT);
    }

    /** 检查单个按键：按下则保存当前 BD 菜单状态并发送打开请求 */
    private static void check(KeyMapping key, WorkstationModeConstants.Mode mode) {
        if (!key.consumeClick()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        // 服务端配置未启用该工作台：本地提示，不发送请求（服务端仍会兜底校验）
        String wsId = mode.name().toLowerCase(java.util.Locale.ROOT);
        if (!com.solr98.beyondintegration.CommandConfig.isWorkstationEnabled(wsId)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.beyond_integration.workstation.disabled",
                    net.minecraft.network.chat.Component.translatable("gui.beyond_integration.mode." + wsId)), true);
            return;
        }
        // 若当前处于 BD 网络界面，保存翻页/鼠标状态便于切换
        if (player.containerMenu instanceof DimensionsNetMenu menu)
            WorkstationTransferHelper.save(menu);
        PacketHandler.sendToServer(new OpenStorageMenuPayload(mode, com.solr98.beyondintegration.ClientConfig.enchantTableApothMode()));
    }
}
