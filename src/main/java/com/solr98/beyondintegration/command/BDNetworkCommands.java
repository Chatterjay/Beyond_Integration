package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.command.member.MemberAddCommand;
import com.solr98.beyondintegration.command.member.MemberRemoveCommand;
import com.solr98.beyondintegration.command.network.*;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * BD 网络命令注册入口：负责注册主命令 /bdtools 及其全部子命令组
 * （network、member、transfer 占位、myNetworks、open、enchant 等）。
 */
public final class BDNetworkCommands {
    private BDNetworkCommands() {}

    /** 命令注册事件回调：挂载 /bdtools 根命令 */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var d = event.getDispatcher();
        var c = event.getBuildContext();
        d.register(Commands.literal("bdtools").requires(s -> s.hasPermission(2))
                .then(buildNetworkCommands(c))
                .then(buildMemberCommands())
                .then(buildTransferCommands())
                .then(buildMyNetworksCommand())
                .then(buildOpenCommand())
                .then(NetworkOpenCommand.registerOpenAny())
                .then(EnchantSeparateCommand.register())
        );
    }

    /** network 子命令组 */
    private static LiteralArgumentBuilder<CommandSourceStack> buildNetworkCommands(CommandBuildContext c) {
        return Commands.literal("network")
                .then(NetworkListCommand.register())
                .then(NetworkInfoCommand.register())
                .then(NetworkInsertCommand.register(c))
                .then(NetworkWorkstationCommand.register())
                .then(NetworkGenerateResourcesCommand.register())
                .then(NetworkToolsCommand.registerGiveTerminal())
                .then(NetworkToolsCommand.registerGiveEnchantedBooks())
                .then(NetworkToolsCommand.registerBatchCreate());
    }

    /** member 子命令组 */
    private static LiteralArgumentBuilder<CommandSourceStack> buildMemberCommands() {
        return Commands.literal("member")
                .then(MemberAddCommand.registerAddMembers())
                .then(MemberAddCommand.registerAddManagers())
                .then(MemberRemoveCommand.registerRemovePlayers())
                .then(MemberRemoveCommand.registerRemoveManagers());
    }
     /** transfer (已移除，留占 */
    private static LiteralArgumentBuilder<CommandSourceStack> buildTransferCommands() {
        return Commands.literal("transfer")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.feature_removed", "transfer")); return 0; });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMyNetworksCommand() {
        return NetworkMyNetworksCommand.register();
    }

    /** open 子命令组 */
    private static LiteralArgumentBuilder<CommandSourceStack> buildOpenCommand() {
        return NetworkOpenCommand.register();
    }
}

