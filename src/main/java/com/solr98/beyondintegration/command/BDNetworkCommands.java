package com.solr98.beyondintegration.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.command.network.*;
import com.solr98.beyondintegration.command.member.*;
import com.solr98.beyondintegration.command.util.*;

/**
 * 新的命令注册入口（模块化版本）
 * 展示模块分离的完整结构
 */
@Mod.EventBusSubscriber(modid = BeyondIntegration.MODID)
public final class BDNetworkCommands {
    
    /** 私有构造器：纯静态工具类，禁止实例化。 */
    private BDNetworkCommands() {}
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext context = event.getBuildContext();
        
        // 注册主命令 /bdtools（仅供OP使用的子命令自带权限检查）
        dispatcher.register(
            Commands.literal("bdtools")
                .then(buildNetworkCommands(context))
                .then(buildMemberCommands())
                .then(buildMyNetworksCommand())
                .then(buildOpenCommand(context))
                // OP专用命令：可以打开任何网络
                .then(NetworkOpenCommand.registerOpenAny())
                .then(EnchantSeparateCommand.register())
        );
    }

    /**
     * 构建网络管理命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildNetworkCommands(
            CommandBuildContext context) {
        
        return Commands.literal("network")
            // 网络列表命令
            .then(NetworkListCommand.register())
            // 网络信息命令
            .then(NetworkInfoCommand.register())
            // 网络插入命令
            .then(NetworkInsertCommand.register(context))
            // 工作台献祭激活管理命令（重置/查询）
            .then(NetworkWorkstationCommand.register())
            // 资源生成命令
            .then(NetworkGenerateResourcesCommand.register())
            // 给予终端命令
            .then(NetworkToolsCommand.registerGiveTerminal())
            // 给予附魔书命令
            .then(NetworkToolsCommand.registerGiveEnchantedBooks())
            // 批量创建网络命令
            .then(NetworkToolsCommand.registerBatchCreate())
            // 其他网络命令可以在这里添加...
            ;
    }
    
    /**
     * 构建成员管理命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildMemberCommands() {
        
        return Commands.literal("member")
            // 添加成员命令
            .then(MemberAddCommand.registerAddMembers())
            // 添加管理员命令
            .then(MemberAddCommand.registerAddManagers())
            // 移除玩家命令
            .then(MemberRemoveCommand.registerRemovePlayers())
            // 移除管理员命令
            .then(MemberRemoveCommand.registerRemoveManagers())
            // 其他成员命令可以在这里添加...
            ;
    }
    
    /**
     * 构建我的网络命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildMyNetworksCommand() {
        
        return NetworkMyNetworksCommand.register();
    }
    
    /**
     * 构建打开命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildOpenCommand(
            CommandBuildContext context) {
        
        return NetworkOpenCommand.register();
    }

}