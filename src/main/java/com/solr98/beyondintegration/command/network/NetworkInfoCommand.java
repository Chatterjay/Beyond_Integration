package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class NetworkInfoCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("info")
                // 无参数：自己的主要网络
                .executes(ctx -> exec(ctx, -1, null))
                // 网络 ID（可选再跟玩家：查看该玩家在该网络的权限信息）
                .then(Commands.argument("netId", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> exec(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "netId"), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> exec(ctx, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "netId"), EntityArgument.getPlayer(ctx, "player")))))
                // 玩家名：查看该玩家的主要网络信息（非本人需 OP）
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> execForPlayer(ctx, EntityArgument.getPlayer(ctx, "player"))));
    }

    /**
     * 按玩家名查询：显示该玩家主要网络的完整信息。
     * 查询他人需要 OP 权限；目标玩家没有网络时给出提示。
     */
    public static int execForPlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        var executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }

        // 查询他人需要 OP 权限
        if (!executor.getUUID().equals(targetPlayer.getUUID())) {
            if (!PermissionChecker.checkOpPermissionForOthers(source, targetPlayer)) return 0;
        }

        // 目标玩家的主要网络
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(targetPlayer);
        if (net == null) {
            source.sendFailure(OutputFormatter.createError("error.player_not_in_any_network",
                    targetPlayer.getName().getString()));
            return 0;
        }

        if (!PermissionChecker.checkNetworkAccessPermission(source, net, targetPlayer)) return 0;

        MutableComponent msg = buildMessage(net, targetPlayer, server);
        source.sendSuccess(() -> msg, false);
        return 1;
    }

    public static int exec(CommandContext<CommandSourceStack> ctx, int netId, ServerPlayer targetPlayer) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        var executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }

        if (netId == -1) {
            DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
            if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
            netId = primaryNet.getId();
        }

        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;

        ServerPlayer subject = targetPlayer != null ? targetPlayer : executor;
        if (targetPlayer != null && !executor.getUUID().equals(targetPlayer.getUUID())) {
            if (!PermissionChecker.checkOpPermissionForOthers(source, targetPlayer)) return 0;
        }
        if (!PermissionChecker.checkNetworkAccessPermission(source, net, subject)) return 0;

        MutableComponent msg = buildMessage(net, subject, server);
        source.sendSuccess(() -> msg, false);
        return 1;
    }

    public static MutableComponent buildMessage(DimensionsNet net, ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        MutableComponent msg = Component.empty();

        msg = msg.append(OutputFormatter.createTitle("network.info.title", net.getId())).append(Component.literal("\n"));

        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        if (!netName.isEmpty()) {
            msg = msg.append(Component.literal(CommandLang.get("network.info.name_label") + netName).withStyle(ChatFormatting.LIGHT_PURPLE)).append(Component.literal("\n"));
        }

        var stats = NetworkUtils.getNetworkStats(net);
        NetworkPermission permLevel = NetworkUtils.getPlayerPermissionLevel(player, net);
        String permDisplay = NetworkUtils.getPermissionLevelDisplay(permLevel);
        String ownerName = CommandUtils.getNetworkOwnerName(net, server);

        msg = msg.append(Component.literal(CommandLang.get("network.info.owner_label", ownerName)))
                .append(Component.literal(net.deleted ? CommandLang.get("network.info.status.deleted") : CommandLang.get("network.info.status.active"))
                        .withStyle(net.deleted ? ChatFormatting.RED : ChatFormatting.GREEN))
                .append(Component.literal(CommandLang.get("network.info.your_permission_label")))
                .append(Component.literal(permDisplay).withStyle(getPermissionColor(permLevel)))
                .append(Component.literal("\n"));

        int crystalTime = NetworkUtils.getCrystalRemainingTime(net);
        msg = msg.append(Component.literal(CommandLang.get("network.info.crystal_time")))
                .append(OutputFormatter.createHoverableTime(crystalTime))
                .append(Component.literal("\n"));

        long slotCap = net.getUnifiedStorage().slotCapacity;
        int slotMaxSize = net.getUnifiedStorage().slotMaxSize;
        msg = msg.append(Component.literal(CommandLang.get("network.info.slot_capacity_label")))
                .append(Component.literal(String.valueOf(slotCap)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(CommandLang.get("network.info.slot_count_label")))
                .append(Component.literal(String.valueOf(slotMaxSize)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("\n"));

        msg = msg.append(Component.literal(CommandLang.get("network.info.storage_stats") + "\n"));
        if (stats.itemTypes > 0) {
            msg = msg.append(Component.literal(CommandLang.get("network.info.items_label")))
                    .append(OutputFormatter.createHoverableResourceType(stats.itemTypes, CommandLang.get("network.info.items_label").trim()))
                    .append(Component.literal(CommandLang.get("network.info.types_suffix")))
                    .append(OutputFormatter.createHoverableItemCount(stats.itemTotal))
                    .append(Component.literal("\n"));
        }
        if (stats.fluidTypes > 0) {
            msg = msg.append(Component.literal(CommandLang.get("network.info.fluids_label")))
                    .append(OutputFormatter.createHoverableResourceType(stats.fluidTypes, CommandLang.get("network.info.fluids_label").trim()))
                    .append(Component.literal(CommandLang.get("network.info.types_suffix")))
                    .append(OutputFormatter.createHoverableFluid(stats.fluidTotal))
                    .append(Component.literal(" mB\n"));
        }
        if (stats.energyTypes > 0) {
            msg = msg.append(Component.literal(CommandLang.get("network.info.energy_label")))
                    .append(OutputFormatter.createHoverableResourceType(stats.energyTypes, CommandLang.get("network.info.energy_label").trim()))
                    .append(Component.literal(CommandLang.get("network.info.types_suffix")))
                    .append(OutputFormatter.createHoverableEnergy(stats.energyTotal))
                    .append(Component.literal(" FE\n"));
        }
        if (stats.getTotalTypes() == 0) {
            msg = msg.append(Component.literal(CommandLang.get("network.info.no_resources")).withStyle(ChatFormatting.GRAY)).append(Component.literal("\n"));
        }

        // 工作台献祭激活状态（可选平衡项开启时显示；重置见 /bdtools network workstation reset）
        if (CommandConfig.isWorkstationActivationEnabled()) {
            List<String> activatedWs = WorkstationActivation.activatedIds(net);
            msg = msg.append(Component.literal("\n"));
            if (activatedWs.isEmpty()) {
                msg = msg.append(Component.literal(CommandLang.get("network.info.workstation_none"))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                msg = msg.append(Component.literal(CommandLang.get("network.info.workstation",
                                String.join(", ", activatedWs)))
                        .withStyle(ChatFormatting.GREEN));
            }
            msg = msg.append(Component.literal("\n"));
        }

        msg = msg.append(Component.literal(CommandLang.get("network.info.player_count_label")))
                .append(OutputFormatter.createHoverableNumber(net.getPlayers().size(), CommandLang.get("network.info.player_count_label")))
                .append(Component.literal(CommandLang.get("network.info.manager_count_label")))
                .append(OutputFormatter.createHoverableNumber(net.getManagers().size(), CommandLang.get("network.info.manager_count_label")))
                .append(Component.literal("\n"));

        msg = msg.append(Component.literal(CommandLang.get("network.info.player_list_label")));
        var playerList = NetworkUtils.getNetworkPlayerList(net, server);
        msg = playerList.hasPlayers() ? msg.append(OutputFormatter.createPlayerList(playerList)) : msg.append(Component.literal(CommandLang.get("network.info.no_players")).withStyle(ChatFormatting.GRAY));

        return msg;
    }

    public static ChatFormatting getPermissionColor(NetworkPermission perm) {
        return perm.getColor();
    }
}

