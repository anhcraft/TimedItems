package dev.anhcraft.timeditems.util;

import dev.anhcraft.craftkit.entity.ArmorStand;
import dev.anhcraft.craftkit.entity.TrackedEntity;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TimedItemEntity {
    private Item item;
    private TrackedEntity<ArmorStand> armorStand;

    public TimedItemEntity(@NotNull Item item) {
        this.item = item;
    }

    @NotNull
    public Item getItem() {
        return item;
    }

    @Nullable
    public TrackedEntity<ArmorStand> getArmorStand() {
        return armorStand;
    }

    public void setArmorStand(@Nullable TrackedEntity<ArmorStand> armorStand) {
        this.armorStand = armorStand;
    }
}
