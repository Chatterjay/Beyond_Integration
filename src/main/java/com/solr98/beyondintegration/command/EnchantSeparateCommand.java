package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 附魔分离命令：提供 /bdtools enchant separate，将玩家主要网络中的
 * 附魔书拆分为单独物品，执行结果由 EnchantmentBookSeparatorHandler 处理。
 */
public class EnchantSeparateCommand {

    /** 注册 enchant separate 命令节点 */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("enchant")
                .then(Commands.literal("separate")
                        .executes(ctx -> separate(ctx.getSource())));
    }

    /** 执行附魔分离：取玩家主要网络并调用分离处理器 */
    private static int separate(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) {
                src.sendFailure(CommandLang.component("error.no_primary_network"));
                return 0;
            }
            Component result = EnchantmentBookSeparatorHandler.separateAll(net);
            src.sendSuccess(() -> result, false);
        } catch (Exception e) {
            src.sendFailure(CommandLang.component("error.execute_failed", e.getMessage()));
        }
        return 1;
    }
}

