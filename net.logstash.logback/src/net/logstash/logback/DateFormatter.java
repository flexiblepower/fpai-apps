package net.logstash.logback;

import java.util.Calendar;

public class DateFormatter {
    public static String format(long timeStamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeStamp);
        return String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03d",
                             calendar.get(Calendar.YEAR),
                             calendar.get(Calendar.MONTH) + 1, // 0-based...
                             calendar.get(Calendar.DAY_OF_MONTH),
                             calendar.get(Calendar.HOUR_OF_DAY),
                             calendar.get(Calendar.MINUTE),
                             calendar.get(Calendar.SECOND),
                             calendar.get(Calendar.MILLISECOND));
    }
}
