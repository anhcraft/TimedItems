package dev.anhcraft.timeditems.util;

import com.google.common.collect.ImmutableList;
import dev.anhcraft.confighelper.ConfigSchema;
import dev.anhcraft.confighelper.annotation.*;
import dev.anhcraft.confighelper.utils.EnumUtil;
import dev.anhcraft.craftkit.common.utils.ChatUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Schema
public class Config {
    @Key("expired_message")
    @IgnoreValue(ifNull = true)
    private String expiredMessage = "&f&l%s&c items have been expired!";

    @Key("expired_sound")
    private boolean expiredSound = true;

    @Key("time_unit")
    @IgnoreValue(ifNull = true)
    private Map<TimeUnit, String> units = new HashMap<>();

    @Key("date_format")
    @IgnoreValue(ifNull = true)
    private String dateFormat = "dd-MM-yyyy HH:mm:ss";

    @Key("expiry_duration_lore.prefix")
    @IgnoreValue(ifNull = true)
    private String expiryDurationLorePrefix = "&f&l[&e&lExpired after: &b&l";

    @Key("expiry_duration_lore.suffix")
    @IgnoreValue(ifNull = true)
    private String expiryDurationLoreSuffix = "&f&l]";

    @Key("expiry_date_lore.prefix")
    @IgnoreValue(ifNull = true)
    private String expiryDateLorePrefix = "&f&l[&e&lExpiry date: &b&l";

    @Key("expiry_date_lore.suffix")
    @IgnoreValue(ifNull = true)
    private String expiryDateLoreSuffix = "&f&l]";

    @Key("timed_holo.message")
    @IgnoreValue(ifNull = true)
    private String timedHoloMsg = "&a&l%s";

    @Key("timed_holo.enabled")
    private boolean timedHoloEnabled;

    @Key("timed_holo.formatted_units")
    @IgnoreValue(ifNull = true)
    @PrettyEnum
    private List<TimeUnit> timedHoloUnits = ImmutableList.of(TimeUnit.DAY, TimeUnit.HOUR, TimeUnit.MINUTE, TimeUnit.SECOND);

    @NotNull
    public String getExpiredMessage() {
        return expiredMessage;
    }

    public boolean isExpiredSound() {
        return expiredSound;
    }

    @Middleware(Middleware.Direction.CONFIG_TO_SCHEMA)
    protected @Nullable Object conf2schema(ConfigSchema.Entry entry, @Nullable Object value) {
        if(value != null && entry.getKey().equals("time_unit")){
            ConfigurationSection cs = (ConfigurationSection) value;
            Map<TimeUnit, String> x = new HashMap<>();
            for(String s : cs.getKeys(false)){
                TimeUnit t = (TimeUnit) EnumUtil.findEnum(TimeUnit.class, s);
                if(t != null){
                    x.put(t, cs.getString(s));
                }
            }
            return x;
        }
        return value;
    }

    @Middleware(Middleware.Direction.SCHEMA_TO_CONFIG)
    protected @Nullable Object schema2conf(ConfigSchema.Entry entry, @Nullable Object value) {
        if(value != null && entry.getKey().equals("time_unit")){
            ConfigurationSection parent = new YamlConfiguration();
            for(Map.Entry<TimeUnit, String> e : ((Map<TimeUnit, String>) value).entrySet()){
                parent.set(e.getKey().name().toLowerCase(), e.getValue());
            }
            return parent;
        }
        return value;
    }

    @NotNull
    public String getUnit(TimeUnit unit) {
        String s = units.get(unit);
        return s == null ? unit.name().toLowerCase() : ChatUtil.formatColorCodes(s);
    }

    @NotNull
    public String getExpiryDurationLorePrefix() {
        return ChatUtil.formatColorCodes(expiryDurationLorePrefix);
    }

    @NotNull
    public String getExpiryDurationLoreSuffix() {
        return ChatUtil.formatColorCodes(expiryDurationLoreSuffix);
    }

    @NotNull
    public String getExpiryDateLorePrefix() {
        return ChatUtil.formatColorCodes(expiryDateLorePrefix);
    }

    @NotNull
    public String getExpiryDateLoreSuffix() {
        return ChatUtil.formatColorCodes(expiryDateLoreSuffix);
    }

    @NotNull
    public String getDateFormat() {
        return dateFormat;
    }

    @NotNull
    public Map<TimeUnit, String> getUnits() {
        return units;
    }

    @NotNull
    public String getTimedHoloMsg() {
        return ChatUtil.formatColorCodes(timedHoloMsg);
    }

    @NotNull
    public List<TimeUnit> getTimedHoloUnits() {
        return timedHoloUnits;
    }

    public boolean isTimedHoloEnabled() {
        return timedHoloEnabled;
    }
}
