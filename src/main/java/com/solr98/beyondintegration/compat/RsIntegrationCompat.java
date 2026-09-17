package com.solr98.beyondintegration.compat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * rs_integration（RI）主动兼容助手。
 * RI 会在 BD 网络界面自行添加 3 个 64×20 的自动进食按钮，并把它们固定到界面左侧外下方
 * （每帧 setX/setY 覆盖 BD 侧栏布局），与本模组左侧栏按钮重叠。
 * 本类与 RsIntegrationAutoEatMixin 配合：屏蔽 RI 自身的安装与定位，
 * 改由本模组把 RI 按钮统一加入 BD 左侧按钮栏（见 DimensionsNetGUIMixin）。
 */
public final class RsIntegrationCompat {

    /** RI 按钮创建入口方法名（AutoEatClientEvents#installControlsAfterNativeInit(Screen, Consumer)） */
    private static final String INSTALL_METHOD = "installControlsAfterNativeInit";

    /** 本模组发起安装的线程标记：mixin 仅放行带此标记的安装调用 */
    private static final ThreadLocal<Boolean> INSTALLING = ThreadLocal.withInitial(() -> false);

    /** 已由本模组接管的 RI 按钮：位置由 BD 侧栏布局决定，屏蔽 RI 的坐标覆盖 */
    private static final java.util.Set<Button> MANAGED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private RsIntegrationCompat() {}

    /** 登记由本模组接管的按钮 */
    public static void manage(Button button) {
        if (button != null) MANAGED.add(button);
    }

    /** 该按钮是否由本模组接管（接管后跳过 RI 的 setX/setY 覆盖） */
    public static boolean isManaged(Button button) {
        return button != null && MANAGED.contains(button);
    }

    /** 反射调用 RI 机器中心面板切换（接管其手动渲染按钮的点击） */
    public static void toggleMachineCenter() {
        try {
            Class<?> cls = Class.forName("com.huanghuang.rsintegration.sidepanel.client.MachineTabHandler");
            cls.getMethod("toggleMachineCenter").invoke(null);
        } catch (Throwable ignored) {}
    }

    /** 反射调用 RI 维度共振盘背包切换（接管其手动渲染按钮的点击） */
    public static void toggleResonanceBackpack() {
        try {
            Class<?> cls = Class.forName("com.huanghuang.rsintegration.sidepanel.client.MachineTabHandler");
            cls.getMethod("toggleResonanceBackpack", boolean.class).invoke(null, true);
        } catch (Throwable ignored) {}
    }

    /** 反射触发 RI 自动进食（等同其 EAT 按钮） */
    public static void sendAutoEat() {
        try {
            Class<?> state = Class.forName("com.huanghuang.rsintegration.autoeat.client.ClientState");
            Object mode = state.getField("currentMode").get(null);
            Object selected = state.getField("selectedItems").get(null);
            Class<?> modeCls = Class.forName("com.huanghuang.rsintegration.autoeat.AutoEatMode");
            Object packet = Class.forName("com.huanghuang.rsintegration.autoeat.network.AutoEatPacket")
                    .getConstructor(modeCls, java.util.Collection.class)
                    .newInstance(mode, selected);
            sendToServer(packet);
        } catch (Throwable ignored) {}
    }

    /** 反射打开 RI 自动进食选择界面（等同其 SELECT 按钮） */
    public static void openAutoEatScreen() {
        try {
            Class<?> state = Class.forName("com.huanghuang.rsintegration.autoeat.client.ClientState");
            Object mode = state.getField("currentMode").get(null);
            Class<?> modeCls = Class.forName("com.huanghuang.rsintegration.autoeat.AutoEatMode");
            Object screen = Class.forName("com.huanghuang.rsintegration.autoeat.client.AutoEatScreen")
                    .getConstructor(modeCls)
                    .newInstance(mode);
            net.minecraft.client.Minecraft.getInstance().setScreen((net.minecraft.client.gui.screens.Screen) screen);
        } catch (Throwable ignored) {}
    }

    /** 反射循环切换 RI 自动进食模式（等同其 MODE 按钮） */
    public static void cycleAutoEatMode() {
        try {
            Class.forName("com.huanghuang.rsintegration.autoeat.client.ClientState")
                    .getMethod("cycleMode").invoke(null);
        } catch (Throwable ignored) {}
    }

    /** 当前 RI 自动进食模式显示名（RI 未加载或反射失败返回 null） */
    public static net.minecraft.network.chat.Component autoEatModeName() {
        try {
            Object mode = Class.forName("com.huanghuang.rsintegration.autoeat.client.ClientState")
                    .getField("currentMode").get(null);
            if (mode == null) return null;
            Object name = mode.getClass().getMethod("displayName").invoke(mode);
            return name instanceof net.minecraft.network.chat.Component c ? c : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 反射经 RI 网络通道发送数据包 */
    private static void sendToServer(Object packet) {
        try {
            Object channel = Class.forName("com.huanghuang.rsintegration.network.packet.NetworkHandler")
                    .getField("CHANNEL").get(null);
            channel.getClass().getMethod("sendToServer", Object.class).invoke(channel, packet);
        } catch (Throwable ignored) {}
    }

    /** 是否加载了 rs_integration */
    public static boolean isLoaded() {
        return net.minecraftforge.fml.ModList.get().isLoaded("rs_integration");
    }

    /** 当前调用是否由本模组发起（RsIntegrationAutoEatMixin 据此放行/拦截） */
    public static boolean isInstalling() {
        return INSTALLING.get();
    }

    /**
     * 反射调用 RI 的按钮创建入口（由本模组发起）。
     * RI 的按钮会通过 adder 交给调用方（本模组将其加入 BD 左侧按钮栏并注册 widget）。
     */
    public static void installControls(Screen screen, Consumer<Button> adder) {
        if (!isLoaded() || screen == null || adder == null) return;
        try {
            Class<?> cls = Class.forName("com.huanghuang.rsintegration.autoeat.client.AutoEatClientEvents");
            Method method = cls.getMethod(INSTALL_METHOD, Screen.class, Consumer.class);
            INSTALLING.set(true);
            method.invoke(null, screen, adder);
        } catch (Throwable ignored) {
            // RI 版本差异时静默跳过（不影响本模组其他功能）
        } finally {
            INSTALLING.set(false);
        }
    }
}
