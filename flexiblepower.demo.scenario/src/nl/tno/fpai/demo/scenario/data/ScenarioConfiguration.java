package nl.tno.fpai.demo.scenario.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ScenarioConfiguration {
    public static class Builder {
        private String bundleId, id, reference;
        private String idRef;
        private ScenarioConfiguration.Type type;
        private final Map<String, String> properties;

        public Builder() {
            properties = new HashMap<String, String>();
            type = null;
        }

        public Builder setBundleId(String bundleId) {
            this.bundleId = bundleId;
            return this;
        }

        public Builder setServiceId(String serviceId) {
            type = Type.SINGLE;
            id = serviceId;
            return this;
        }

        public Builder setFactoryId(String factoryId) {
            type = Type.MULTIPLE;
            id = factoryId;
            return this;
        }

        public Builder setIdRef(String idRef) {
            this.idRef = idRef;
            return this;
        }

        public Builder setProperty(String key, String value) {
            properties.put(key, String.valueOf(value));
            return this;
        }

        public Builder setType(String type) {
            this.type = ScenarioConfiguration.Type.valueOf(type.toUpperCase());
            return this;
        }

        public Builder setReference(String reference) {
            this.reference = reference;
            return this;
        }

        public ScenarioConfiguration build() {
            return new ScenarioConfiguration(bundleId, id, reference, idRef, type, properties);
        }
    }

    public static enum Type {
        SINGLE, MULTIPLE
    }

    private final String bundleId, id, reference;
    private final String idRef;
    private final Map<String, String> properties;
    private final Type type;

    ScenarioConfiguration(String bundleId,
                          String id,
                          String reference,
                          String idRef,
                          Type type,
                          Map<String, String> properties) {
        if (bundleId == null || id == null || type == null || properties == null) {
            throw new IllegalArgumentException("Missing information for scenario: bundleId=" + bundleId
                                               + ", id="
                                               + id
                                               + ", type="
                                               + type);
        }

        this.bundleId = bundleId;
        this.id = id;
        this.reference = reference;
        this.idRef = idRef;
        this.type = type;
        this.properties = Collections.unmodifiableMap(new HashMap<String, String>(properties));
    }

    public String getBundleId() {
        return bundleId;
    }

    public String getId() {
        return id;
    }

    public String getIdRef() {
        return idRef;
    }

    public String getReference() {
        return reference;
    }

    public Type getType() {
        return type;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return toString(new StringBuilder(), 0).toString();
    }

    public StringBuilder toString(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append('\t');
        }
        sb.append("<config bundleId=\"").append(bundleId);
        if (type == Type.MULTIPLE) {
            sb.append("\" factoryId=\"");
        } else {
            sb.append("\" serviceId=\"");
        }
        sb.append(id).append("\"");
        if (idRef != null) {
            sb.append(" idRef=\"").append(idRef).append("\"");
        }
        if (reference != null) {
            sb.append(" ref=\"").append(reference).append("\"");
        }
        sb.append(">\n");
        for (Entry<String, String> entry : properties.entrySet()) {
            for (int i = 0; i < indent + 1; i++) {
                sb.append('\t');
            }
            sb.append("<")
              .append(entry.getKey())
              .append(">")
              .append(entry.getValue())
              .append("</")
              .append(entry.getKey())
              .append(">\n");
        }
        for (int i = 0; i < indent; i++) {
            sb.append('\t');
        }
        sb.append("</config>");
        return sb;
    }
}
