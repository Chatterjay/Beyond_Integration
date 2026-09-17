package com.solr98.beyondintegration.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.solr98.beyondintegration.feature.enchant.EnchantmentBookSeparatorHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 附魔分离命令。
 * 注册 /bdtools enchant separate 子命令：将执行者主要网络中
 * 带附魔的书籍/物品进行附魔分离处理。
 */
public class EnchantSeparateCommand {

    /**
     * 注册 enchant separate 子命令。
     * @return 命令构造器，挂载到 bdtools 主命令下
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("enchant")
                .then(Commands.literal("separate")
                        .executes(ctx -> separate(ctx.getSource())));
    }

    /**
     * 执行附魔分离逻辑：获取玩家与其主要网络，调用分离处理器并反馈结果。
     * @param src 命令源
     * @return 命令执行结果（成功返回 1）
     */
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
