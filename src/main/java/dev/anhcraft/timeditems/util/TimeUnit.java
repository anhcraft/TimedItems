package dev.anhcraft.timeditems.util;

import java.util.*;

public enum TimeUnit {
    MILLISECOND(1),
    TICK(50),
    SECOND(1000),
    MINUTE(SECOND.ms * 60L),
    HOUR(MINUTE.ms * 60),
    DAY(HOUR.ms * 24),
    WEEK(DAY.ms * 7),
    MONTH_28(DAY.ms * 28),
    MONTH_29(DAY.ms * 29),
    MONTH_30(DAY.ms * 30),
    MONTH_31(DAY.ms * 31),
    YEAR(DAY.ms * 365),
    LEAP_YEAR(DAY.ms * 366),
    DECADE(YEAR.ms * 8 + LEAP_YEAR.ms * 2),
    CENTURY(DECADE.ms * 10),
    MILLENNIUM(CENTURY.ms * 10);

    private long ms;

    TimeUnit(long ms) {
        this.ms = ms;
    }

    public long getMillis(){
        return ms;
    }

    public long convert(long duration, TimeUnit to){
        return duration*ms/to.getMillis();
    }

    public long toMillis(long duration){
        return duration*ms;
    }

    public double getSeconds(){
        return ms/1000d;
    }

    public static TreeMap<TimeUnit, Long> format(TimeUnit unit, long duration, List<TimeUnit> formattedUnits){
        formattedUnits.sort(Comparator.reverseOrder());

        TreeMap<TimeUnit, Long> map = new TreeMap<>(Comparator.comparingDouble(
                TimeUnit::getMillis).reversed());
        for(TimeUnit u : formattedUnits){
            if(duration <= 0){
                map.put(u, 0L);
            } else {
                long x = unit.convert(duration, u);
                map.put(u, x);
                duration -= u.convert(x, unit);
            }
        }
        return map;
    }
}
