package nl.tno.hexabus.protocol;

import java.util.HashMap;
import java.util.Map;

import nl.tno.hexabus.protocol.ReverseEnumMap.ReversableEnum;

public class ReverseEnumMap<V extends Enum<V> & ReversableEnum> {
    public interface ReversableEnum {
        byte getValue();
    }

    private final Map<Byte, V> map = new HashMap<Byte, V>();

    public ReverseEnumMap(Class<V> valueType) {
        for (V v : valueType.getEnumConstants()) {
            map.put(v.getValue(), v);
        }
    }

    public V get(byte num) {
        return map.get(num);
    }
}
