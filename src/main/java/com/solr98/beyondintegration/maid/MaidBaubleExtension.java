package com.solr98.beyondintegration.maid;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import com.wintercogs.beyonddimensions.common.init.BDItems;

/**
 * 女仆饰品扩展：通过 LittleMaidExtension 注册便携网络终端为女仆可用饰品，
 * 使女仆饰品栏能够持有并绑定网络终端。
 */
@LittleMaidExtension
public class MaidBaubleExtension implements ILittleMaid {
    /** 将便携网络终端绑定为女仆饰品（空实现表示仅注册类型） */
    @Override
    public void bindMaidBauble(BaubleManager manager) {
        manager.bind(BDItems.NET_TERMINAL_ITEM.get(), new IMaidBauble() {});
    }
}

