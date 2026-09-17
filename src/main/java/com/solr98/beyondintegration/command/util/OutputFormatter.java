package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * 命令输出格式化工具类：统一构建各类聊天文本组件，
 * 包括标题、状态消息、悬停提示、分页导航、可点击按钮、玩家列表
 * 以及物品/流体/能量等资源数量显示。
 */
public class OutputFormatter {

    /** 千位分隔数字格式化器（美式区域设置）。 */
    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    // ========== Title ==========

    /** 创建金色加粗的标题文本。 */
    public static MutableComponent createTitle(String key, Object... args) {
        return Component.literal(CommandLang.get(key, args)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    /** 创建带页码信息的标题文本。 */
    public static MutableComponent createPagedTitle(String key, int page, Object... args) {
        String title = CommandLang.get(key, args) + " " + CommandLang.get("network.list.page", page);
        return Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }

    // ========== Status messages ==========

    /** 创建绿色成功消息。 */
    public static MutableComponent createSuccess(String key, Object... args) {
        return Component.literal(CommandLang.get(key, args)).withStyle(ChatFormatting.GREEN);
    }

    /** 创建红色错误消息。 */
    public static MutableComponent createError(String key, Object... args) {
        return Component.literal(CommandLang.get(key, args)).withStyle(ChatFormatting.RED);
    }

    /** 创建黄色警告消息。 */
    public static MutableComponent createWarning(String key, Object... args) {
        return Component.literal(CommandLang.get(key, args)).withStyle(ChatFormatting.YELLOW);
    }

    /** 创建青色提示消息。 */
    public static MutableComponent createInfo(String key, Object... args) {
        return Component.literal(CommandLang.get(key, args)).withStyle(ChatFormatting.AQUA);
    }

    // ========== Hoverable components ==========

    /** 创建带悬停提示的白色文本。 */
    public static Component createHoverableText(String text, String hoverText) {
        return Component.literal(text).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText))).withColor(ChatFormatting.WHITE));
    }

    /** 创建资源类型计数文本（大于 0 为绿色，否则红色），悬停显示详细说明。 */
    public static Component createHoverableResourceType(int count, String resourceType) {
        ChatFormatting color = count > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.literal(String.valueOf(count)).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.resource_type_count", resourceType, count)))).withColor(color));
    }

    /** 创建黄色物品总数文本（long），悬停显示总数详情。 */
    public static Component createHoverableItemCount(long count) {
        return Component.literal(NF.format(count)).withStyle(s -> s.withColor(ChatFormatting.YELLOW).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.total_items", NF.format(count))))));
    }

    /** 创建黄色物品总数文本（BigInteger，支持超大数格式）。 */
    public static Component createHoverableItemCount(BigInteger count) {
        String display = CommandUtils.formatBigNumber(count);
        return Component.literal(display).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.total_items", count)))).withColor(ChatFormatting.YELLOW));
    }

    /** 创建青色流体总量文本，悬停显示总量详情。 */
    public static Component createHoverableFluid(BigInteger amount) {
        String display = CommandUtils.formatBigNumber(amount);
        return Component.literal(display).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.fluid_total", amount)))).withColor(ChatFormatting.AQUA));
    }

    /** 创建淡紫色能量总量文本，悬停显示总量详情。 */
    public static Component createHoverableEnergy(BigInteger amount) {
        String display = CommandUtils.formatBigNumber(amount);
        return Component.literal(display).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.energy_total", amount)))).withColor(ChatFormatting.LIGHT_PURPLE));
    }

    /** 创建青色数字文本（int），悬停显示其描述与数值。 */
    public static Component createHoverableNumber(int number, String description) {
        String formatted = NF.format(number);
        return Component.literal(formatted).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(description + ": " + NF.format(number)))).withColor(ChatFormatting.AQUA));
    }

    /** 创建青色数字文本（long），悬停显示其描述与数值。 */
    public static Component createHoverableNumber(long number, String description) {
        String formatted = NF.format(number);
        return Component.literal(formatted).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(description + ": " + NF.format(number)))).withColor(ChatFormatting.AQUA));
    }

    /**
     * 创建剩余时间文本：将刻数换算为天/时/分/秒并分级着色
     * （0 金色、30 秒内绿色、300 秒内黄色、更久青色），悬停显示详情；
     * 负数表示功能关闭。
     */
    public static Component createHoverableTime(int ticks) {
        if (ticks < 0) {
            return Component.literal(CommandLang.get("display.disabled")).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.crystal_generation_disabled")))).withColor(ChatFormatting.GRAY));
        }
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int hours = minutes / 60;
        int days = hours / 24;

        String timeStr;
        if (days > 0) timeStr = days + "d " + (hours % 24) + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
        else if (hours > 0) timeStr = hours + "h " + (minutes % 60) + "m " + (seconds % 60) + "s";
        else if (minutes > 0) timeStr = minutes + "m " + (seconds % 60) + "s";
        else if (seconds > 0) timeStr = seconds + "s";
        else timeStr = "<1s";

        ChatFormatting color;
        if (ticks == 0) color = ChatFormatting.GOLD;
        else if (seconds < 30) color = ChatFormatting.GREEN;
        else if (seconds < 300) color = ChatFormatting.YELLOW;
        else color = ChatFormatting.AQUA;

        return Component.literal(timeStr).withStyle(s -> s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("display.crystal_remaining_time", timeStr)))).withColor(color));
    }

    // ========== Pagination ==========

    /** 创建分页导航组件：包含上一页/下一页可点击链接与当前页信息。 */
    public static MutableComponent createPagination(int currentPage, int totalPages, int totalItems, String commandPrefix) {
        MutableComponent nav = Component.empty();
        if (currentPage > 1) {
            nav = nav.append(Component.literal("[" + CommandLang.get("network.list.previous") + "]")
                    .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " " + (currentPage - 1)))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("pagination.click_to_page", currentPage - 1))))
                            .withColor(ChatFormatting.GREEN)))
                    .append(Component.literal(" "));
        }
        nav = nav.append(Component.literal("[" + CommandLang.get("network.list.page_with_total", currentPage, totalPages, totalItems) + "]")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)));
        if (currentPage < totalPages) {
            nav = nav.append(Component.literal(" ")).append(
                    Component.literal("[" + CommandLang.get("network.list.next") + "]")
                            .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, commandPrefix + " " + (currentPage + 1)))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("pagination.click_to_page", currentPage + 1))))
                                    .withColor(ChatFormatting.GREEN)));
        }
        return nav;
    }

    // ========== Buttons ==========

    /** 创建绿色"接受"按钮（点击执行 /bdtools transfer accept）。 */
    public static MutableComponent createAcceptButton() {
        return Component.literal(CommandLang.get("button.accept"))
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bdtools transfer accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("button.hover.accept")).withStyle(ChatFormatting.GREEN)))
                        .withColor(ChatFormatting.GREEN).withBold(true));
    }

    /** 创建红色"拒绝"按钮（点击执行 /bdtools transfer deny）。 */
    public static MutableComponent createDenyButton() {
        return Component.literal(CommandLang.get("button.deny"))
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bdtools transfer deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("button.hover.deny")).withStyle(ChatFormatting.RED)))
                        .withColor(ChatFormatting.RED).withBold(true));
    }

    /** 创建灰色"取消"按钮（点击执行 /bdtools transfer cancel）。 */
    public static MutableComponent createCancelButton() {
        return Component.literal(CommandLang.get("button.cancel"))
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bdtools transfer cancel"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandLang.get("button.hover.cancel")).withStyle(ChatFormatting.GRAY)))
                        .withColor(ChatFormatting.GRAY).withBold(true));
    }

    // ========== Player list ==========

    /** 创建旧版玩家列表文本：所有者红色、管理员蓝色、成员绿色。 */
    public static MutableComponent createPlayerListLegacy(NetworkUtils.PlayerList playerList) {
        MutableComponent msg = Component.empty();
        boolean first = true;
        if (!playerList.owner.isEmpty()) {
            msg = msg.append(Component.literal(playerList.owner).withStyle(ChatFormatting.RED));
            first = false;
        }
        for (String m : playerList.managers) {
            if (!first) msg = msg.append(Component.literal(", "));
            msg = msg.append(Component.literal(m).withStyle(ChatFormatting.BLUE));
            first = false;
        }
        for (String m : playerList.members) {
            if (!first) msg = msg.append(Component.literal(", "));
            msg = msg.append(Component.literal(m).withStyle(ChatFormatting.GREEN));
            first = false;
        }
        if (first) msg = msg.append(Component.literal(CommandLang.get("network.info.no_players")).withStyle(ChatFormatting.GRAY));
        return msg;
    }

    /** 创建多行玩家列表文本（支持多名所有者），按角色分色分行展示。 */
    public static MutableComponent createPlayerList(NetworkUtils.PlayerList list) {
        MutableComponent r = Component.empty();
        if (!list.owners.isEmpty()) r = r.append(Component.literal("\n  [Owner] ").withStyle(ChatFormatting.RED)).append(Component.literal(String.join(", ", list.owners)).withStyle(ChatFormatting.WHITE));
        if (!list.managers.isEmpty()) r = r.append(Component.literal("\n  [Manager] ").withStyle(ChatFormatting.BLUE)).append(Component.literal(String.join(", ", list.managers)).withStyle(ChatFormatting.WHITE));
        if (!list.members.isEmpty()) r = r.append(Component.literal("\n  [Member] ").withStyle(ChatFormatting.GREEN)).append(Component.literal(String.join(", ", list.members)).withStyle(ChatFormatting.WHITE));
        return r;
    }

    // ========== Item/Fluid/Energy display ==========

    /** 创建物品显示文本：物品名 + 灰色数量后缀。 */
    public static MutableComponent createItemDisplay(ItemStack itemStack, long amount) {
        ItemStack display = itemStack.copy();
        display.setCount((int) Math.min(amount, Integer.MAX_VALUE));
        return Component.literal("").append(display.getDisplayName()).append(Component.literal(" x" + amount).withStyle(ChatFormatting.GRAY));
    }

    /** 创建流体显示文本：流体注册 ID + 蓝色 mB 数量。 */
    public static MutableComponent createFluidDisplay(net.neoforged.neoforge.fluids.FluidStack fluidStack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        return Component.literal("").append(Component.literal(id != null ? id.toString() : "unknown")).append(Component.literal(" " + fluidStack.getAmount() + "mB").withStyle(ChatFormatting.BLUE));
    }

    /** 创建能量显示文本：能量类型金色 + 黄色 FE 数量。 */
    public static MutableComponent createEnergyDisplay(String energyType, long amount) {
        return Component.literal("").append(Component.literal(energyType).withStyle(ChatFormatting.GOLD)).append(Component.literal(" " + amount + "FE").withStyle(ChatFormatting.YELLOW));
    }

    /** 创建带缩进的列表项文本。 */
    public static MutableComponent createListItem(String text, int indentLevel) {
        return Component.literal("  ".repeat(indentLevel) + text).withStyle(ChatFormatting.WHITE);
    }

    /** 创建统计行文本：白色标签 + 指定颜色数值。 */
    public static MutableComponent createStatLine(String label, Object value, ChatFormatting valueColor) {
        return Component.literal(label).append(Component.literal(value.toString()).withStyle(valueColor)).withStyle(ChatFormatting.WHITE);
    }
}

