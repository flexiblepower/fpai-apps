package org.flexiblepower.monitoring.mysql.writer;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;

/**
 * Class which iterates keys from properties which indicate a field in the definition of an {@link ObservationProvider}.
 */
final class ObservationProviderFieldsFilter implements Iterable<Entry<String, Object>> {

    /**
     * The prefix of keys in the observation provider's properties which indicate fields as part of the observations
     * (meta data).
     */
    static final String FIELD_PREFIX = ObservationProviderRegistrationHelper.KEY_OBSERVATION_TYPE + ".";

    /** The meta-data properties of the provider. */
    private final Map<String, Object> providerProperties;

    /**
     * @param observationWriter
     */
    ObservationProviderFieldsFilter(Map<String, Object> providerProperties) {
        this.providerProperties = providerProperties;
    }

    @Override
    public Iterator<Entry<String, Object>> iterator() {
        // create an Iterator over all properties
        // (which are to be filtered)
        final Iterator<Entry<String, Object>> entries = providerProperties.entrySet().iterator();

        // create an Iterator which works on the Iterator above and filters
        return new Iterator<Map.Entry<String, Object>>() {
            private Entry<String, Object> next = null;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    // if we already determined that there is a next field
                    return true;
                } else {
                    // otherwise try and determine this and memorize the result
                    next = next1();
                    return next != null;
                }
            }

            @Override
            public Entry<String, Object> next() {
                try {
                    if (next == null) {
                        // try to get the next field
                        next = next1();
                    }

                    if (next == null) {
                        // if no next field, we're at the end
                        throw new NoSuchElementException();
                    } else {
                        // return an entry with FIELD_PREFIX stripped
                        return new Entry<String, Object>() {
                            private final String key = next.getKey().substring(FIELD_PREFIX.length());
                            private final Object value = next.getValue();

                            @Override
                            public Object getValue() {
                                return value;
                            }

                            @Override
                            public String getKey() {
                                return key;
                            }

                            @Override
                            public Object setValue(Object value) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                } finally {
                    // if we return, always loose the pre computed result
                    next = null;
                }
            }

            private Entry<String, Object> next1() {
                // loop over then entries Iterator until we find
                // an entry of which the key starts with FIELD_PREFIX
                find: while (entries.hasNext()) {
                    Entry<String, Object> e = entries.next();
                    String key = e.getKey();
                    String prefix = key + ".";

                    // check if the key is a composite entry and skip if it is
                    for (String k : providerProperties.keySet()) {
                        if ((!key.equals(k)) && k.startsWith(prefix)) {
                            continue find;
                        }
                    }

                    if (key.startsWith(FIELD_PREFIX)) {
                        return e;
                    }
                }

                // return null if we're at the end
                return null;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
