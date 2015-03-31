package org.flexiblepower.monitoring.elasticsearch;

import java.io.IOException;
import java.io.Writer;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.google.gson.stream.JsonWriter;

public class DataWriter extends JsonWriter {
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    public DataWriter(Writer w) {
        super(w);
        setLenient(true);
    }

    @Override
    public DataWriter name(String name) throws IOException {
        super.name(name);
        return this;
    }

    public DataWriter value(Object object) throws IOException {
        if (object == null) {
            nullValue();
        } else if (object instanceof Date) {
            value((Date) object);
        } else if (object instanceof Number) {
            value((Number) object);
        } else if (object instanceof CharSequence) {
            value(object.toString());
        } else {
            return null;
        }
        return this;
    }

    public DataWriter value(Date date) throws IOException {
        super.value(format(date.getTime()));
        return this;
    }

    public static String format(long timeStamp) {
        Calendar calendar = Calendar.getInstance(GMT);
        calendar.setTimeInMillis(timeStamp);
        return String.format("%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
                             calendar.get(Calendar.YEAR),
                             calendar.get(Calendar.MONTH) + 1, // 0-based...
                             calendar.get(Calendar.DAY_OF_MONTH),
                             calendar.get(Calendar.HOUR_OF_DAY),
                             calendar.get(Calendar.MINUTE),
                             calendar.get(Calendar.SECOND),
                             calendar.get(Calendar.MILLISECOND));
    }

    public DataWriter write(String name, Object value) throws IOException {
        if (value == null) {
            name(name).nullValue();
        } else if (value instanceof Date) {
            name(name).value((Date) value);
        } else if (value instanceof Number) {
            name(name).value((Number) value);
        } else if (value instanceof CharSequence) {
            name(name).value(value.toString());
        } else if (value.getClass().isArray()) {
            name(name).beginArray();
            if (value instanceof double[]) {
                for (double v : (double[]) value) {
                    value(v);
                }
            } else if (value instanceof int[]) {
                for (int v : (int[]) value) {
                    value(v);
                }
            } else if (value instanceof long[]) {
                for (long v : (long[]) value) {
                    value(v);
                }
            }
            endArray();
        }
        return this;
    }
}
