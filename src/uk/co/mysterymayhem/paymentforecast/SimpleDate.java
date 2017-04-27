package uk.co.mysterymayhem.paymentforecast;


import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Created by Mysteryem on 24/04/2017.
 */
public class SimpleDate implements Comparable<SimpleDate> {
    private static final Calendar UTC_CALENDAR = GregorianCalendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC), Locale.ENGLISH);

    private final int year;
    // 0 = jan, just like with Calendar/Date classes
    private final int month;
    private final int dayOfMonth;

    public SimpleDate(Instant instant) {
        this(setAndGetCalendarFromInstant(instant));
    }

    public SimpleDate(Calendar date) {
        this.year = date.get(Calendar.YEAR);
        this.month = date.get(Calendar.MONTH);
        this.dayOfMonth = date.get(Calendar.DAY_OF_MONTH);
    }

    private static Calendar setAndGetCalendarFromInstant(Instant instant) {
        UTC_CALENDAR.setTimeInMillis(instant.toEpochMilli());
        return UTC_CALENDAR;
    }

    public String prettyToString() {
        UTC_CALENDAR.clear();
        UTC_CALENDAR.set(this.year, this.month, this.dayOfMonth);
        StringBuilder builder = new StringBuilder();

        builder
                .append(UTC_CALENDAR.get(Calendar.DAY_OF_MONTH))
                .append(" ")
                .append(UTC_CALENDAR.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.ENGLISH))
                .append(" ")
                .append(UTC_CALENDAR.get(Calendar.YEAR));

        return builder.toString();
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
            SimpleDate other = (SimpleDate) obj;
            return this.year == other.year && this.month == other.month && this.dayOfMonth == other.dayOfMonth;
        }
        return false;
    }

    @Override
    public int compareTo(SimpleDate o) {
        if (this.year > o.year) {
            return 1;
        } else if (this.year < o.year) {
            return -1;
        } else {
            if (this.month > o.month) {
                return 1;
            } else if (this.month < o.month) {
                return -1;
            } else {
                if (this.dayOfMonth > o.dayOfMonth) {
                    return 1;
                } else if (this.dayOfMonth < o.dayOfMonth) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }
}
