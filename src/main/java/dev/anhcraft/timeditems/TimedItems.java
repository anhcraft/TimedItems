package dev.anhcraft.timeditems;

import co.aikar.commands.PaperCommandManager;
import dev.anhcraft.confighelper.ConfigHelper;
import dev.anhcraft.confighelper.ConfigSchema;
import dev.anhcraft.confighelper.exception.InvalidValueException;
import dev.anhcraft.craftkit.CraftExtension;
import dev.anhcraft.craftkit.chat.Chat;
import dev.anhcraft.craftkit.entity.ArmorStand;
import dev.anhcraft.craftkit.entity.TrackedEntity;
import dev.anhcraft.craftkit.helpers.TaskHelper;
import dev.anhcraft.craftkit.utils.ItemUtil;
import dev.anhcraft.craftkit.utils.ServerUtil;
import dev.anhcraft.jvmkit.utils.CollectionUtil;
import dev.anhcraft.jvmkit.utils.ReflectionUtil;
import dev.anhcraft.timeditems.api.TimedAPI;
import dev.anhcraft.timeditems.cmd.Command;
import dev.anhcraft.timeditems.util.Config;
import dev.anhcraft.timeditems.util.TimeUnit;
import dev.anhcraft.timeditems.util.TimedItemEntity;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class TimedItems extends JavaPlugin implements Listener {
    private final TaskHelper taskHelper = new TaskHelper(this);
    public final Chat chat = new Chat("&b&l[TI] &r&f");
    private final Map<Integer, TimedItemEntity> TIMED_ITEMS = new ConcurrentHashMap<>();
    private File configFile;
    public Config config;
    public TimedAPI api;
    public SimpleDateFormat dateFormat;
    private CraftExtension extension;
    private final Object LOCK = new Object();

    public void reload(){
        getDataFolder().mkdir();
        YamlConfiguration conf = new YamlConfiguration();
        config = new Config();
        if(configFile.exists()) {
            try {
                conf.load(configFile);
                ConfigHelper.readConfig(conf, ConfigSchema.of(Config.class), config);
            } catch (InvalidValueException | InvalidConfigurationException | IOException e) {
                e.printStackTrace();
            }
        } else {
            for(TimeUnit unit : TimeUnit.values()){
                config.getUnits().put(unit, unit.name().toLowerCase());
            }
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            ConfigHelper.writeConfig(conf, ConfigSchema.of(Config.class), config);
            try {
                conf.save(configFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dateFormat = new SimpleDateFormat(config.getDateFormat());
        if(config.isTimedHoloEnabled()){
            for(TimedItemEntity tte : TIMED_ITEMS.values()){
                setArmorStand(tte);
            }
        } else {
            for(TimedItemEntity tte : TIMED_ITEMS.values()){
                removeArmorStand(tte);
            }
        }
    }

    private void setArmorStand(TimedItemEntity tte){
        if(tte.getArmorStand() != null) return;
        synchronized (LOCK) {
            ArmorStand as = ArmorStand.spawn(tte.getItem().getLocation());
            as.setNameVisible(true);
            as.setVisible(false);
            TrackedEntity<ArmorStand> te = extension.trackEntity(as);
            te.setViewDistance(100);
            // due to some bug, dont use #setViewers
            for (Player p : Bukkit.getOnlinePlayers()){
                te.addViewer(p);
            }
            te.getEntity().sendUpdate();
            tte.setArmorStand(te);
        }
    }

    private void removeArmorStand(TimedItemEntity tte){
        if (tte.getArmorStand() == null) return;
        synchronized (LOCK) {
            extension.untrackEntity(tte.getArmorStand());
            tte.getArmorStand().kill();
            tte.setArmorStand(null);
        }
    }

    private void addItem(Item item){
        ItemStack itemStack = item.getItemStack();
        if(api.getExpiryDate(itemStack) == -1) return;
        TimedItemEntity tte = new TimedItemEntity(item);
        TIMED_ITEMS.put(item.getEntityId(), tte);
        if(config.isTimedHoloEnabled()){
            setArmorStand(tte);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event){
        addItem(event.getItemDrop());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event){
        Item item = event.getItem();
        TimedItemEntity th = TIMED_ITEMS.remove(item.getEntityId());
        if(th != null) {
            removeArmorStand(th);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event){
        for(TimedItemEntity t : TIMED_ITEMS.values()){
            if(t.getArmorStand() != null){
                t.getArmorStand().addViewer(event.getPlayer());
            }
        }
    }

    @Override
    public void onEnable() {
        extension = CraftExtension.of(TimedItems.class);
        configFile = new File(getDataFolder(), "config.yml");
        reload();

        getServer().getPluginManager().registerEvents(this, this);
        new Metrics(this);

        PaperCommandManager pcm = new PaperCommandManager(this);
        pcm.registerCommand(new Command(this));
        pcm.getCommandCompletions().registerStaticCompletion("unit", () -> Arrays.stream(TimeUnit.values()).map(e -> e.name().toLowerCase()).collect(Collectors.toList()));

        api = new TimedAPI();
        ReflectionUtil.setDeclaredField(TimedAPI.class, api, "plugin", this);
        ReflectionUtil.setDeclaredStaticField(TimedAPI.class, "instance", api);

        taskHelper.newAsyncTimerTask(() -> {
            for(Player player : getServer().getOnlinePlayers()){
                if (player.hasPermission("timeditems.bypass")) {
                    continue;
                }
                boolean needUpdate = false;
                boolean hasExpired = false;
                int rmvCounter = 0;
                List<ItemStack> newItems = new LinkedList<>();
                for (ItemStack item : player.getInventory().getContents()) {
                    if (!ItemUtil.isNull(item)) {
                        if (api.isTimed(item)) {
                            newItems.add(api.makeExpired(item));
                            needUpdate = true;
                            continue;
                        }
                        if (api.isExpired(item)) {
                            rmvCounter += item.getAmount();
                            needUpdate = true;
                            hasExpired = true;
                            continue;
                        }
                    }
                    newItems.add(item);
                }
                if (!needUpdate) return;

                int rm = rmvCounter;
                boolean u2 = hasExpired;
                taskHelper.newTask(() -> {
                    player.getInventory().setContents(CollectionUtil.toArray(newItems, ItemStack.class));
                    player.updateInventory();
                    if (u2) {
                        chat.message(player, String.format(config.getExpiredMessage(), rm));
                        if(config.isExpiredSound()){
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 3.0f, 1.0f);
                        }
                    }
                });
            }
        }, 0, 60);

        ServerUtil.getAllEntitiesByClass(Item.class, this::addItem);

        taskHelper.newAsyncTimerTask(() -> {
            for (Iterator<Map.Entry<Integer, TimedItemEntity>> it = TIMED_ITEMS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, TimedItemEntity> e = it.next();
                Item item = e.getValue().getItem();
                ItemStack itemStack = item.getItemStack();
                long expiryDate = api.getExpiryDate(itemStack);
                long delta = System.currentTimeMillis() - expiryDate;
                if(item.isDead() || delta >= 0){
                    removeArmorStand(e.getValue());
                    item.remove();
                    it.remove();
                    continue;
                }
                if(e.getValue().getArmorStand() != null) {
                    TrackedEntity<ArmorStand> tas = e.getValue().getArmorStand();
                    ArmorStand as = tas.getEntity();
                    StringBuilder s = new StringBuilder();
                    TreeMap<TimeUnit, Long> map = TimeUnit.format(TimeUnit.MILLISECOND, -delta, config.getTimedHoloUnits());
                    for (Map.Entry<TimeUnit, Long> ent : map.entrySet()) {
                        if (ent.getValue() == 0) continue;
                        s.append(ent.getValue()).append(" ").append(config.getUnit(ent.getKey())).append(" ");
                    }
                    as.setName(String.format(config.getTimedHoloMsg(), s.toString()));
                    as.sendUpdate();
                    Location loc = item.getLocation();
                    double dis = loc.distanceSquared(tas.getLocation());
                    if (dis >= 2) {
                        tas.teleport(loc.subtract(0, 1.4, 0));
                    }
                }
            }
        }, 0, 20);

        for (Item item : ServerUtil.getAllEntitiesByClass(Item.class)){
            addItem(item);
        }
    }

    @Override
    public void onDisable() {
        CraftExtension.unregister(TimedItems.class);
    }
}
