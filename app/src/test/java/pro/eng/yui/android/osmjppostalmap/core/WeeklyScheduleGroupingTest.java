package pro.eng.yui.android.osmjppostalmap.core;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import pro.eng.yui.oss.osm.lib.jppostalcore.parser.CollectionTimeParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.parser.OpeningHoursParser;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.CollectionTime;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.IDaySchedule;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.IDayStatus;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.ITagPart;
import pro.eng.yui.oss.osm.lib.jppostalcore.types.OpenCloseTime;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyScheduleGroupingTest {

    @Test
    void collectionSchedulesWithTheSameMultipleTimesAreEqual() {
        IDaySchedule sunday = schedule(CollectionTimeParser.DayStatus.UNKNOWN,
                new CollectionTime("09:00"), new CollectionTime("13:00"));
        IDaySchedule holiday = schedule(CollectionTimeParser.DayStatus.UNKNOWN,
                new CollectionTime("09:00"), new CollectionTime("13:00"));

        assertTrue(WeeklyScheduleGrouping.areSchedulesEqual(sunday, holiday));
    }

    @Test
    void collectionSchedulesWithDifferentTimesAreNotEqual() {
        IDaySchedule sunday = schedule(CollectionTimeParser.DayStatus.UNKNOWN, new CollectionTime("09:00"));
        IDaySchedule holiday = schedule(CollectionTimeParser.DayStatus.UNKNOWN, new CollectionTime("10:00"));

        assertFalse(WeeklyScheduleGrouping.areSchedulesEqual(sunday, holiday));
    }

    @Test
    void differentStatusesAreNotEqualEvenWhenBothHaveNoTimes() {
        IDaySchedule sunday = schedule(CollectionTimeParser.DayStatus.CLOSED_DAY);
        IDaySchedule holiday = schedule(CollectionTimeParser.DayStatus.UNKNOWN);

        assertFalse(WeeklyScheduleGrouping.areSchedulesEqual(sunday, holiday));
    }

    @Test
    void twoUnknownSchedulesRemainEqual() {
        IDaySchedule sunday = schedule(CollectionTimeParser.DayStatus.UNKNOWN);
        IDaySchedule holiday = schedule(CollectionTimeParser.DayStatus.UNKNOWN);

        assertTrue(WeeklyScheduleGrouping.areSchedulesEqual(sunday, holiday));
    }

    @Test
    void openingSchedulesCompareEveryOpeningInterval() {
        IDaySchedule sunday = schedule(OpeningHoursParser.DayStatus.OPEN_DAY,
                new OpenCloseTime("09:00", "12:00"), new OpenCloseTime("13:00", "17:00"));
        IDaySchedule matchingHoliday = schedule(OpeningHoursParser.DayStatus.OPEN_DAY,
                new OpenCloseTime("09:00", "12:00"), new OpenCloseTime("13:00", "17:00"));
        IDaySchedule differentHoliday = schedule(OpeningHoursParser.DayStatus.OPEN_DAY,
                new OpenCloseTime("09:00", "12:00"), new OpenCloseTime("14:00", "17:00"));

        assertTrue(WeeklyScheduleGrouping.areSchedulesEqual(sunday, matchingHoliday));
        assertFalse(WeeklyScheduleGrouping.areSchedulesEqual(sunday, differentHoliday));
    }

    @Test
    void missingSchedulesAreEqualOnlyWhenBothAreMissing() {
        IDaySchedule unknown = schedule(CollectionTimeParser.DayStatus.UNKNOWN);

        assertTrue(WeeklyScheduleGrouping.areSchedulesEqual(null, null));
        assertFalse(WeeklyScheduleGrouping.areSchedulesEqual(null, unknown));
    }

    private static IDaySchedule schedule(IDayStatus status, ITagPart... parts) {
        List<ITagPart> schedule = Arrays.asList(parts);
        return new IDaySchedule() {
            @Override
            public IDayStatus status() {
                return status;
            }

            @Override
            @NotNull
            public List<? extends ITagPart> schedule() {
                return schedule;
            }
        };
    }
}
