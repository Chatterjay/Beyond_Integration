package com.solr98.beyondintegration.core.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;

public class ItemTokenUtil {
    private static final String TOKEN_KEY = "beyond$bindingToken";
    private static final String OWNER_KEY = "beyond$bindingOwner";

    public static UUID readToken(ItemStack stack) {
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            var tag = customData.copyTag();
            if (tag.contains(TOKEN_KEY)) {
                try { return UUID.fromString(tag.getString(TOKEN_KEY)); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static void clearBinding(ItemStack stack) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.remove("NetId");
            tag.remove(TOKEN_KEY);
            tag.remove(OWNER_KEY);
        }));
    }
}
