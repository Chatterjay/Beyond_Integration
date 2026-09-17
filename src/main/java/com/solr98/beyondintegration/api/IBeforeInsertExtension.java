package com.solr98.beyondintegration.api;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 插入前拦截扩展接口：在物品/资源写入维度网络存储之前进行拦截处理，
 * 可用于实现自定义的插入校验、改写或分流逻辑。
 */
public interface IBeforeInsertExtension {

    /**
     * 插入前处理回调
     * @param originalInsert 原始待插入的数量键值
     * @param tryInsert      实际尝试插入的数量键值
     * @param net            目标维度网络，可能为 null
     * @return 处理结果（包含应插入与拒绝等返回信息）
     */
    @NotNull
    UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo onBeforeInsert(
            @NotNull KeyAmount originalInsert,
            @NotNull KeyAmount tryInsert,
            @Nullable DimensionsNet net);
}
