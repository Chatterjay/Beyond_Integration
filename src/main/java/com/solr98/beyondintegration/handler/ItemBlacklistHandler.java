package com.solr98.beyondintegration.handler;
import com.solr98.beyondintegration.CommandConfig;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * 物品黑名单处理器：物品进入维度网络统一存储前，按注册表 ID 匹配
 * 黑名单配置，命中则拒绝插入。
 */
public class ItemBlacklistHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        // 黑名单功能未开启则放行
        if (!CommandConfig.enableItemBlacklist()) return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        if (tryInsert.key() instanceof ItemStackKey itemKey) {
            // 取物品注册表 ID 并匹配黑名单
            var item = itemKey.getSource();
            var id = BuiltInRegistries.ITEM.getKey(item);
            // 命中黑名单：返回已处理标记（true）阻止写入
            if (id != null && CommandConfig.itemBlacklist().contains(id.toString()))
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, true);
        }
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }
}

