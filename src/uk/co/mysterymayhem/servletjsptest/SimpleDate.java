package uk.co.mysterymayhem.servletjsptest;


import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Created by Mysteryem on 24/04/2017.
 */
public class SimpleDate {
    private static final Calendar UTC_CALENDAR = GregorianCalendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ENGLISH);

    private final int year;
    // 0 = jan, just like with Calendar/Date classes
    private final int month;
    private final int dayOfMonth;

    private static Calendar setAndGetCalendarFromInstant(Instant instant) {
        UTC_CALENDAR.setTimeInMillis(instant.toEpochMilli());
        return UTC_CALENDAR;
    }

    public SimpleDate(Instant instant) {
        this(setAndGetCalendarFromInstant(instant));
    }

    public SimpleDate(Calendar date) {
        this.year = date.get(Calendar.YEAR);
        this.month = date.get(Calendar.MONTH);
        this.dayOfMonth = date.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public int hashCode() {
        int hashCode = 31 + this.year;
        hashCode = 31 * hashCode + this.month;
        hashCode = 31 * hashCode + this.dayOfMonth;
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof SimpleDate) {
            SimpleDate other = (SimpleDate)obj;
            return this.year == other.year && this.month == other.month && this.dayOfMonth == other.dayOfMonth;
        }
        return false;
    }
}
