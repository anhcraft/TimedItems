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
import dev.anhcraft.timeditems.util.TimedHolo;
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
    private File configFile;
    public Config config;
    private final TaskHelper taskHelper = new TaskHelper(this);
    public final Chat chat = new Chat("&b&l[TI] &r&f");
    private final Map<Integer, TimedHolo> TIMED_HOLO = new ConcurrentHashMap<>();
    public SimpleDateFormat dateFormat;
    public TimedAPI api;
    private CraftExtension extension;

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
    }

    @Override
    public void onEnable() {
        extension = CraftExtension.of(TimedItems.class);
        configFile = new File(getDataFolder(), "config.yml");
        reload();

        getServer().getPluginManager().registerEvents(this, this);

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

        taskHelper.newAsyncTimerTask(() -> {
            if(!config.isTimedHoloEnabled()) return;
            for (Iterator<Map.Entry<Integer, TimedHolo>> it = TIMED_HOLO.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, TimedHolo> e = it.next();
                Item item = e.getValue().getItem();
                ItemStack itemStack = item.getItemStack();
                long expiryDate = api.getExpiryDate(itemStack);
                long delta = System.currentTimeMillis() - expiryDate;
                TrackedEntity<ArmorStand> tas = e.getValue().getArmorStand();
                if(item.isDead() || delta >= 0){
                    extension.untrackEntity(tas);
                    tas.kill();
                    it.remove();
                    item.remove();
                    continue;
                }
                ArmorStand as = e.getValue().getArmorStand().getEntity();
                StringBuilder s = new StringBuilder();
                TreeMap<TimeUnit, Long> map = TimeUnit.format(TimeUnit.MILLISECOND, -delta, config.getTimedHoloUnits());
                for(Map.Entry<TimeUnit, Long> ent : map.entrySet()){
                    if(ent.getValue() == 0) continue;
                    s.append(ent.getValue()).append(" ").append(config.getUnit(ent.getKey())).append(" ");
                }
                as.setName(String.format(config.getTimedHoloMsg(), s.toString()));
                as.sendUpdate();
                Location loc = item.getLocation();
                double dis = loc.distanceSquared(tas.getLocation());
                if(dis >= 2){
                    tas.teleport(loc.subtract(0, 1.4, 0));
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

    private void addItem(Item item){
        ItemStack itemStack = item.getItemStack();
        if(api.getExpiryDate(itemStack) == -1) return;
        ArmorStand as = ArmorStand.spawn(item.getLocation());
        as.setNameVisible(true);
        as.setVisible(false);
        TrackedEntity<ArmorStand> te = extension.trackEntity(as);
        te.setViewDistance(100);
        te.setViewers(new ArrayList<>(Bukkit.getOnlinePlayers()));
        te.getEntity().sendUpdate();
        TIMED_HOLO.put(item.getEntityId(), new TimedHolo(item, te));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event){
        if(!config.isTimedHoloEnabled()) return;
        addItem(event.getItemDrop());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event){
        if(!config.isTimedHoloEnabled()) return;
        Item item = event.getItem();
        TimedHolo th = TIMED_HOLO.remove(item.getEntityId());
        if(th != null){
            extension.untrackEntity(th.getArmorStand());
            th.getArmorStand().kill();
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event){
        if(!config.isTimedHoloEnabled()) return;
        for(TimedHolo t : TIMED_HOLO.values()){
            t.getArmorStand().addViewer(event.getPlayer());
        }
    }
}
