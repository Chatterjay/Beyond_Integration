package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.client.gui.NetMenuType;
import com.wintercogs.beyonddimensions.common.menu.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.NetControlMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class NetworkOpenCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("open")
                .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_MENU, null, true))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_MENU, null, true))
                        .then(Commands.literal("terminal").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_TERMINAL, null, true)))
                        .then(Commands.literal("permission").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "permission", true)))
                        .then(Commands.literal("control").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "control", true)))
                        .then(Commands.literal("craft").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_MENU, null, true))))
                .then(Commands.literal("terminal").executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_TERMINAL, null, true)))
                .then(Commands.literal("permission").executes(ctx -> executeOpen(ctx, -1, null, "permission", true)))
                .then(Commands.literal("control").executes(ctx -> executeOpen(ctx, -1, null, "control", true)))
                .then(Commands.literal("craft").executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_MENU, null, true)));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerOpenAny() {
        return Commands.literal("openAny").requires(s -> CommandUtils.hasOpPermission(s))
                .executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_MENU, null, false))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_MENU, null, false))
                        .then(Commands.literal("terminal").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_TERMINAL, null, false)))
                        .then(Commands.literal("permission").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "permission", false)))
                        .then(Commands.literal("control").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null, "control", false)))
                        .then(Commands.literal("craft").executes(ctx -> executeOpen(ctx, IntegerArgumentType.getInteger(ctx, "netId"), NetMenuType.NET_CRAFT_MENU, null, false))))
                .then(Commands.literal("terminal").executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_TERMINAL, null, false)))
                .then(Commands.literal("permission").executes(ctx -> executeOpen(ctx, -1, null, "permission", false)))
                .then(Commands.literal("control").executes(ctx -> executeOpen(ctx, -1, null, "control", false)))
                .then(Commands.literal("craft").executes(ctx -> executeOpen(ctx, -1, NetMenuType.NET_CRAFT_MENU, null, false)));
    }

    private static int executeOpen(CommandContext<CommandSourceStack> ctx, int netId, NetMenuType menuType, String specialMenu, boolean checkPermission) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }

        int actualNetId = netId;
        if (actualNetId == -1) {
            DimensionsNet tempNet = DimensionsNet.getNetFromPlayer(executor);
            if (tempNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
            actualNetId = tempNet.getId();
        }

        DimensionsNet net = PermissionChecker.checkNetworkExists(source, actualNetId);
        if (net == null) return 0;

        if (checkPermission && !PermissionChecker.checkNetworkAccessPermission(source, net, executor)) return 0;

        try {
            boolean success = openGui(executor, net, menuType, specialMenu);
            if (success) {
                String menuName = getMenuName(menuType, specialMenu);
                int fni = actualNetId;
                source.sendSuccess(() -> Component.literal(CommandLang.get("network.open.success", executor.getGameProfile().getName(), fni, menuName)).withStyle(ChatFormatting.GREEN), false);
                return 1;
            } else {
                source.sendFailure(OutputFormatter.createError("network.open.error.general", CommandLang.get("network.open.error.failed")));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(OutputFormatter.createError("network.open.error.general", e.getMessage()));
            return 0;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean openGui(ServerPlayer player, DimensionsNet net, NetMenuType menuType, String specialMenu) {
        try {
            if (specialMenu != null && (specialMenu.equals("permission") || specialMenu.equals("control"))) {
                if (!net.isOwner(player.getUUID()) && !net.isManager(player.getUUID())) return false;
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new NetControlMenu(id, inv),
                        Component.literal(CommandLang.get("network.open.menu.permission"))));
                return true;
            }
            if (menuType != null) {
                var menu = DimensionsNetMenu.Dimensions_Net_Menu;
                switch (menuType) {
                    case NET_MENU, NET_CRAFT_TERMINAL -> player.openMenu(new SimpleMenuProvider((id, inv, p) ->
                            new DimensionsNetMenu(menu.get(), id, inv, net.getUnifiedStorage()),
                            Component.literal(menuType == NetMenuType.NET_MENU ? CommandLang.get("network.open.menu.storage") : CommandLang.get("network.open.menu.terminal"))));
                    case NET_CRAFT_MENU -> player.openMenu(new SimpleMenuProvider((id, inv, p) ->
                            new DimensionsCraftMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer())),
                            Component.literal(CommandLang.get("network.open.menu.crafting"))));
                    default -> { return false; }
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getMenuName(NetMenuType menuType, String specialMenu) {
        if (specialMenu != null && (specialMenu.equals("permission") || specialMenu.equals("control")))
            return CommandLang.get("network.open.menu.permission");
        if (menuType != null) {
            return switch (menuType) {
                case NET_MENU -> CommandLang.get("network.open.menu.storage");
                case NET_CRAFT_MENU -> CommandLang.get("network.open.menu.crafting");
                case NET_CRAFT_TERMINAL -> CommandLang.get("network.open.menu.terminal");
            };
        }
        return "Unknown";
    }
}

