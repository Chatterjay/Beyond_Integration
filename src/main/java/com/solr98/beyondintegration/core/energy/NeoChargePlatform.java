package com.solr98.beyondintegration.core.energy;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** NeoForge 平台充电能力实现（能量 capability / ModList / 属性遍历）。 */
public final class NeoChargePlatform implements ChargePlatform {

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public EnergyHandle energy(ItemStack stack) {
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null) return null;
        return new EnergyHandle() {
            @Override public int maxStored() { return storage.getMaxEnergyStored(); }

            @Override public int stored() { return storage.getEnergyStored(); }

            @Override public boolean canReceive() { return storage.canReceive(); }

            @Override public int receive(int amount) { return storage.receiveEnergy(amount, false); }
        };
    }

    @Override
    public boolean hasMainHandAttackDamage(ItemStack stack) {
        final boolean[] result = {false};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_DAMAGE.value()) {
                result[0] = true;
            }
        });
        return result[0];
    }
}
