package nl.tno.hexabus.protocol;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TypeMap<V> {
    private static final Logger log = LoggerFactory.getLogger(TypeMap.class);

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Coded {
        byte value();
    }

    public static <V> TypeMap<V> create(Class<? extends V>... valueType) {
        return new TypeMap<V>(valueType);
    }

    private final Class<? extends V>[] values;
    private final int offset;

    private TypeMap(Class<? extends V>... values) {
        log.trace("Initializing TypeMap for values {}", Arrays.asList(values));

        int min = 255, max = 0;
        @SuppressWarnings("unchecked")
        Class<? extends V>[] temp = new Class[256];
        for (Class<? extends V> value : values) {
            Coded coded = value.getAnnotation(Coded.class);
            if (coded != null) {
                int ix = coded.value() & 0xff;
                min = Math.min(ix, min);
                max = Math.max(ix, max);
                log.trace("Mapping {} -> {}", ix, value);
                temp[ix] = value;
            } else {
                log.warn("Tried to add the value [{}], but it is missing the @Coded annotation", value);
            }
        }

        if (min > max) {
            throw new IllegalArgumentException("There were no valid classes detected");
        }

        this.values = Arrays.copyOfRange(temp, min, max + 1);
        this.offset = min;
    }

    public Class<? extends V> get(byte num) {
        int ix = (num & 0xff) - offset;
        if (ix < 0 || ix >= values.length) {
            return null;
        }
        return values[ix];
    }
}
