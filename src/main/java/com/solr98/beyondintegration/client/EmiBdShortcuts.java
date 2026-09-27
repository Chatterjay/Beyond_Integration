package com.solr98.beyondintegration.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.solr98.beyondintegration.compat.EmiBdCompat;
import com.solr98.beyondintegration.compat.bd.BdTerminalActions;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.payload.EmiBdActionPayload;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.AbstractStackTypedSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * BD 终端快捷操作（行为移植自 EmiLink 的 {@code BDShortcutHandler}）。
 *
 * <p>只实现四项 BD 终端自身的能力：</p>
 * <table border="1">
 *   <caption>快捷键</caption>
 *   <tr><th>操作</th><th>位置</th><th>效果</th></tr>
 *   <tr><td>Shift + 左键</td><td>网络存储槽</td><td>从网络提取一组该物品到背包</td></tr>
 *   <tr><td>Space + 左键</td><td>网络存储槽</td><td>从网络提取所有同类物品到背包</td></tr>
 *   <tr><td>Space + 左键</td><td>背包主区 / 快捷栏</td><td>该分区内同类物品批量存入网络</td></tr>
 *   <tr><td>Space + 左键</td><td>合成结果槽</td><td>连续批量合成（最多 {@link BdTerminalActions#MAX_BATCH_CRAFT} 次）</td></tr>
 *   <tr><td>Space + 左键</td><td>合成网格</td><td>清理合成网格并把内容返还网络</td></tr>
 * </table>
 *
 * <p><b>刻意不实现</b> "从物品管理器取物"（EMI 侧点击物品取物）：该路径不在本次同步范围内，
 * 由本模组既有的 JEI 集成承担。</p>
 *
 * <p>整个子系统由 {@link EmiBdCompat} 门控：EMI 不存在、或检测到 EmiLink（功能冲突）时，
 * 本类不注册任何监听器，一次点击也不会被拦截。</p>
 */
public final class EmiBdShortcuts {

    private EmiBdShortcuts() {}

    /** 注册 BD 终端快捷操作；门控不通过时静默跳过（原因由 EmiBdCompat 记录日志）。 */
    public static void register() {
        if (!EmiBdCompat.enabled()) return;
        NeoForge.EVENT_BUS.addListener(EmiBdShortcuts::onMousePressed);
        NeoForge.EVENT_BUS.addListener(EmiBdShortcuts::onKeyPressed);
    }

    /**
     * 鼠标左键按下（Pre）：在 BD 网络/合成终端（含本模组工作站界面）上把
     * Shift/Space+点击转成一项 BD 终端批量操作。
     */
    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (!(event.getScreen() instanceof DimensionsNetGUI<?> screen)) return;
        // 搜索框聚焦时让路：空格/Shift 应作为文本输入
        if (screen.getFocused() instanceof EditBox) return;

        DimensionsNetMenu menu = screen.getMenu();
        if (menu == null || !menu.getCarried().isEmpty()) return;

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean space = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE);
        boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (!space && !shift) return;

        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return;

        ItemStack clicked = slot.getItem();
        if (clicked.isEmpty()) return;

        int action = resolveAction(slot, menu, space, shift);
        if (action < 0) return;

        sendToServer(new EmiBdActionPayload(clicked.copy(), action));
        event.setCanceled(true);
    }

    /**
     * 空格键按下（Pre）：在 BD 终端上吞掉空格，避免 Space+点击时 BD 界面把空格
     * 当成按钮激活/翻页（对齐 EmiLink 的行为）。搜索框聚焦时不拦截。
     */
    private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() != GLFW.GLFW_KEY_SPACE) return;
        if (!(event.getScreen() instanceof DimensionsNetGUI<?> screen)) return;
        if (screen.getFocused() instanceof EditBox) return;
        event.setCanceled(true);
    }

    /**
     * 把"悬停槽位 + 修饰键"解析成一个动作码。
     *
     * @return 动作码；不需要本模组处理时返回 -1（交还 BD / 原版处理）
     */
    private static int resolveAction(Slot slot, DimensionsNetMenu menu, boolean space, boolean shift) {
        // 1) 合成结果槽：Space = 批量合成
        if (space && slot instanceof ResultSlot) {
            return EmiBdActionPayload.ACTION_BATCH_CRAFT;
        }

        // 2) 合成网格：Space = 清理网格并返还网络（本模组的合成格与 BD 原生的合成格
        //    都以 CraftingContainer 为容器，可统一识别）
        if (space && slot.container instanceof CraftingContainer) {
            return EmiBdActionPayload.ACTION_CLEAN_CRAFT_TO_NETWORK;
        }

        // 3) 网络存储槽：Space = 提取同类全部，Shift = 提取一组
        if (slot instanceof AbstractStackTypedSlot) {
            if (space) return EmiBdActionPayload.ACTION_TRANSFER_NET_TO_INV;
            if (shift) return EmiBdActionPayload.ACTION_EXTRACT_ONE;
            return -1;
        }

        // 4) 玩家背包槽：Space = 同类批量存入网络（快捷栏与主背包分区处理）
        if (space && slot.index >= menu.inventoryStartIndex && slot.index < menu.inventoryEndIndex) {
            int inventoryIndex = slot.getContainerSlot();
            return (inventoryIndex >= 0 && inventoryIndex < 9)
                    ? EmiBdActionPayload.ACTION_TRANSFER_HOTBAR_TO_NET
                    : EmiBdActionPayload.ACTION_TRANSFER_MAIN_TO_NET;
        }

        return -1;
    }

    /** 发包兜底：服务端未安装本模组时静默丢弃，避免客户端抛异常。 */
    private static void sendToServer(CustomPacketPayload packet) {
        try {
            PacketHandler.sendToServer(packet);
        } catch (Throwable ignored) {
        }
    }
}
