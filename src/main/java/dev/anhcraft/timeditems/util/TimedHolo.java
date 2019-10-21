package dev.anhcraft.timeditems.util;

import dev.anhcraft.craftkit.entity.ArmorStand;
import dev.anhcraft.craftkit.entity.TrackedEntity;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;

public class TimedHolo {
    private Item item;
    private TrackedEntity<ArmorStand> armorStand;

    public TimedHolo(@NotNull Item item, @NotNull TrackedEntity<ArmorStand> armorStand) {
        this.item = item;
        this.armorStand = armorStand;
    }

    @NotNull
    public Item getItem() {
        return item;
    }

    @NotNull
    public TrackedEntity<ArmorStand> getArmorStand() {
        return armorStand;
    }
}
