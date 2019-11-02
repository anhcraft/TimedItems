package dev.anhcraft.timeditems.api;

import com.google.common.base.Preconditions;
import dev.anhcraft.confighelper.utils.EnumUtil;
import dev.anhcraft.craftkit.cb_common.nbt.CompoundTag;
import dev.anhcraft.craftkit.cb_common.nbt.IntTag;
import dev.anhcraft.craftkit.cb_common.nbt.LongTag;
import dev.anhcraft.craftkit.cb_common.nbt.StringTag;
import dev.anhcraft.craftkit.helpers.ItemHelper;
import dev.anhcraft.timeditems.TimedItems;
import dev.anhcraft.timeditems.util.TimeUnit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;

public class TimedAPI {
    private static TimedAPI instance;
    private TimedItems plugin;

    @NotNull
    public static TimedAPI getInstance(){
        if(instance == null){
            throw new UnsupportedOperationException("API is not ready");
        }
        return instance;
    }

    @NotNull
    public ItemStack setTimed(@NotNull ItemStack item, @NotNull TimeUnit unit, int duration) {
        Preconditions.checkNotNull(item);
        Preconditions.checkNotNull(unit);
        ItemHelper im = ItemHelper.of(item);
        List<String> lore = im.getLore();
        lore.removeIf(l -> {
            return l.startsWith(plugin.config.getExpiryDateLorePrefix()) || l.startsWith(plugin.config.getExpiryDurationLorePrefix());
        });
        lore.add(plugin.config.getExpiryDurationLorePrefix() + duration + " " + plugin.config.getUnit(unit) + plugin.config.getExpiryDurationLoreSuffix());
        item = im.setLore(lore).save();

        CompoundTag root = CompoundTag.of(item);
        CompoundTag tag = root.getOrCreateDefault("tag", CompoundTag.class);
        CompoundTag expDurTag = new CompoundTag();
        expDurTag.put("unit", unit.toString());
        expDurTag.put("value", duration);
        tag.put("expiry_duration", expDurTag);
        tag.remove("expiry_date");
        root.put("tag", tag);
        return root.save(item);
    }

    @NotNull
    public ItemStack removeTimed(@NotNull ItemStack item) {
        Preconditions.checkNotNull(item);
        ItemHelper im = ItemHelper.of(item);
        List<String> lore = im.getLore();
        lore.removeIf(l -> {
            return l.startsWith(plugin.config.getExpiryDateLorePrefix()) || l.startsWith(plugin.config.getExpiryDurationLorePrefix());
        });
        im.setLore(lore);
        item = im.save();

        CompoundTag root = CompoundTag.of(item);
        CompoundTag tag = root.getOrCreateDefault("tag", CompoundTag.class);
        tag.remove("expiry_date");
        tag.remove("expiry_duration");
        root.put("tag", tag);
        return root.save(item);
    }

    public boolean isTimed(@NotNull ItemStack item) {
        Preconditions.checkNotNull(item);
        CompoundTag root = CompoundTag.of(item);
        CompoundTag tag = root.get("tag", CompoundTag.class);
        return tag != null && tag.has("expiry_duration");
    }

    public boolean isExpired(@NotNull ItemStack item) {
        Preconditions.checkNotNull(item);
        CompoundTag root = CompoundTag.of(item);
        CompoundTag tag = root.get("tag", CompoundTag.class);
        if(tag != null){
            LongTag exd = tag.get("expiry_date", LongTag.class);
            if(exd != null) {
                return System.currentTimeMillis() > exd.getValue();
            }
        }
        return false;
    }

    public long getExpiryDate(@NotNull ItemStack item) {
        Preconditions.checkNotNull(item);
        CompoundTag root = CompoundTag.of(item);
        CompoundTag tag = root.get("tag", CompoundTag.class);
        if(tag != null){
            LongTag exd = tag.get("expiry_date", LongTag.class);
            if(exd != null) {
                return exd.getValue();
            }
        }
        return -1;
    }

    @NotNull
    public ItemStack makeExpired(@NotNull ItemStack item) {
        Preconditions.checkNotNull(item);
        CompoundTag root = CompoundTag.of(item);
        CompoundTag tag = root.getOrCreateDefault("tag", CompoundTag.class);
        CompoundTag timedDate = tag.get("expiry_duration", CompoundTag.class);
        if (timedDate == null) return item;
        StringTag unitTag = timedDate.get("unit", StringTag.class);
        if(unitTag == null) return item;
        TimeUnit unit = (TimeUnit) EnumUtil.findEnum(TimeUnit.class, unitTag.getValue());
        int value = timedDate.getOrCreateDefault("value", IntTag.class).getValue();

        long expiryDate = System.currentTimeMillis() + unit.getMillis() * value;
        String date = plugin.dateFormat.format(new Date(expiryDate));
        tag.put("expiry_date", expiryDate);
        tag.remove("expiry_duration");
        root.put("tag", tag);
        item = root.save(item);

        ItemHelper im = ItemHelper.of(item);
        List<String> lore = im.getLore();
        lore.removeIf(l -> l.startsWith(plugin.config.getExpiryDurationLorePrefix()));
        lore.add(plugin.config.getExpiryDateLorePrefix() + date + plugin.config.getExpiryDateLoreSuffix());
        im.setLore(lore);
        return im.save();
    }
}
