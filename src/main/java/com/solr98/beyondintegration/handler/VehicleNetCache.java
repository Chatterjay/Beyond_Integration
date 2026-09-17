package com.solr98.beyondintegration.handler;

import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 载具实体级网络缓存：绑定网络 + 固定项（虚拟弹药 + FE + 名称 + 附魔开关）快照
 * + 可用弹药列表（固定，随 NBT 持久化）。
 * 独立订阅绑定网络的 storage delta（能量/物理物品即时脏标记）；
 * 虚拟弹药无事件源，由调用方（有乘客时的 tick）做对账差分。
 */
public class VehicleNetCache {

    /** 所属载具实体。 */
    private final VehicleEntity vehicle;
    /** 绑定网络 ID（-1 表示未绑定）。 */
    private int netId = -1;
    /** 可用弹药列表（虚拟弹药 key / "ITEM:<注册名>"，随 NBT 持久化）。 */
    private List<String> ammoList = new ArrayList<>();
    /** 上次快照的虚拟弹药映射，用于差分对比。 */
    private final Map<String, Long> lastAmmo = new HashMap<>();
    /** 上次快照能量（-1 表示无快照，触发全量输出）。 */
    private long lastEnergy = -1;
    /** 上次快照网络自定义名称。 */
    private String lastName = null;
    /** 上次快照附魔分离开关状态。 */
    private boolean lastEnchant = true;
    /** 脏标记：storage delta 或外部标记为脏时强制输出增量。 */
    private volatile boolean dirty = false;
    /** 网络 storage 的 delta 订阅句柄，用于即时脏标记。 */
    private AutoCloseable deltaSub;

    public VehicleNetCache(VehicleEntity vehicle) {
        this.vehicle = vehicle;
    }

    /** 获取绑定网络 ID（-1 表示未绑定）。 */
    public int getNetId() {
        return netId;
    }

    /** 获取绑定的维度网络（未绑定或不存在时返回 null）。 */
    public DimensionsNet getNet() {
        return netId >= 0 ? DimensionsNet.getNetFromId(netId) : null;
    }

    /** 获取可用弹药列表。 */
    public List<String> getAmmoList() {
        return ammoList;
    }

    /** 绑定网络并订阅 delta；ammoList 为 null 时自动从炮位收集 */
    public void attach(int netId, List<String> ammoList) {
        detach();
        this.netId = netId;
        this.ammoList = ammoList != null ? new ArrayList<>(ammoList) : collectAmmoList(vehicle);
        this.lastEnergy = -1;
        this.lastName = null;
        this.lastAmmo.clear();
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net != null) {
            deltaSub = BdSubscriptionHub.subscribe(net, this, (key, size, insert) -> dirty = true);
        }
    }

    /** 解绑网络：经统一订阅中心退订并重置快照状态。 */
    public void detach() {
        if (netId >= 0) BdSubscriptionHub.unsubscribe(netId, this);
        deltaSub = null;
        netId = -1;
        lastEnergy = -1;
        lastName = null;
        lastAmmo.clear();
    }

    /** 外部（如虚拟弹药写入）标记为脏，强制输出增量。 */
    public void markDirty() {
        dirty = true;
    }

    /** 强制下次 refresh 输出全量 */
    public void forceFull() {
        lastEnergy = -1;
    }

    /**
     * 差分刷新。返回 null 表示无变化。
     * full=true：ammo 为全量、energy 为绝对值；否则 ammo 为增量、energy 为 delta。
     */
    public PushData refresh() {
        DimensionsNet net = getNet();
        if (net == null) return null;
        if (!(net instanceof SuperbAmmoAccessor acc)) return null;

        Map<String, Long> current = new HashMap<>(acc.getSuperbAmmo());
        long energy = net.getUnifiedStorage().getStackByKey(EnergyStackKey.INSTANCE).amount();
        String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
        boolean enchantSep = net instanceof EnchantSeparationAccessor ea && ea.beyond$isEnchantSeparationEnabled();

        boolean metaChanged = !netName.equals(lastName == null ? "" : lastName) || enchantSep != lastEnchant;

        if (lastEnergy < 0 || metaChanged) {
            lastAmmo.clear();
            lastAmmo.putAll(current);
            lastEnergy = energy;
            lastName = netName;
            lastEnchant = enchantSep;
            dirty = false;
            return new PushData(true, new HashMap<>(current), energy, netName, enchantSep);
        }

        if (!dirty && current.equals(lastAmmo) && energy == lastEnergy) {
            return null;
        }

        Map<String, Long> deltas = new HashMap<>();
        for (var entry : current.entrySet()) {
            Long old = lastAmmo.get(entry.getKey());
            long diff = old == null ? entry.getValue() : entry.getValue() - old;
            if (diff != 0) deltas.put(entry.getKey(), diff);
        }
        for (var entry : lastAmmo.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                deltas.put(entry.getKey(), -entry.getValue());
            }
        }

        lastAmmo.clear();
        lastAmmo.putAll(current);
        long energyDelta = energy - lastEnergy;
        lastEnergy = energy;
        lastName = netName;
        lastEnchant = enchantSep;
        dirty = false;

        return new PushData(false, deltas, energyDelta, netName, enchantSep);
    }

    /**
     * 收集载具全部炮位引用的可用弹药列表：
     * PLAYER_AMMO → serializationName（虚拟弹药 key）；ITEM → "ITEM:<注册名>"。
     */
    public static List<String> collectAmmoList(VehicleEntity vehicle) {
        List<String> list = new ArrayList<>();
        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            GunData data = vehicle.getGunData(seat);
            if (data == null) continue;
            AmmoConsumer consumer = data.selectedAmmoConsumer();
            if (consumer == null) continue;
            if (consumer.getType() == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
                if (consumer.getPlayerAmmoType() != null) {
                    String key = consumer.getPlayerAmmoType().serializationName;
                    if (!list.contains(key)) list.add(key);
                }
            } else if (consumer.getType() == AmmoConsumer.AmmoConsumeType.ITEM) {
                String raw = null;
                if (!consumer.stack().isEmpty()) {
                    var regKey = BuiltInRegistries.ITEM.getKey(consumer.stack().getItem());
                    if (regKey != null) raw = regKey.toString();
                }
                if (raw == null) raw = consumer.getAmmo();
                if (raw == null || raw.isEmpty()) continue;
                raw = raw.strip();
                int space = raw.indexOf(' ');
                if (space > 0) raw = raw.substring(space + 1).strip();
                if (raw.startsWith("@") || raw.startsWith("#")) raw = raw.substring(1);
                ResourceLocation loc = ResourceLocation.tryParse(raw);
                if (loc == null) continue;
                ItemStack ref = new ItemStack(BuiltInRegistries.ITEM.get(loc));
                if (ref.isEmpty()) continue;
                String key = "ITEM:" + raw;
                if (!list.contains(key)) list.add(key);
            }
        }
        return list;
    }

    /** 推送结果：full 为 true 时 ammo 为全量、energy 为绝对值，否则均为增量。 */
    public record PushData(boolean full, Map<String, Long> ammo, long energy, String netName,
                           boolean enchantSeparation) {
    }
}
