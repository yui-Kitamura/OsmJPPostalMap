package pro.eng.yui.android.osmjppostalmap.core;

import pro.eng.yui.oss.osm.lib.jppostalcore.types.IDaySchedule;

import java.util.ArrayList;
import java.util.List;

public final class WeeklyScheduleGrouping {

    private WeeklyScheduleGrouping() {
    }

    public static boolean areSchedulesEqual(IDaySchedule s1, IDaySchedule s2) {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;
        if (s1.status() != s2.status()) return false;

        List<String> list1 = new ArrayList<>();
        for (Object part : s1.schedule()) list1.add(part.toString());
        List<String> list2 = new ArrayList<>();
        for (Object part : s2.schedule()) list2.add(part.toString());

        return list1.equals(list2);
    }
}
