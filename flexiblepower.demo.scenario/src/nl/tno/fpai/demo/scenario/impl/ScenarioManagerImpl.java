package nl.tno.fpai.demo.scenario.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

import nl.tno.fpai.demo.scenario.ScenarioManager;
import nl.tno.fpai.demo.scenario.data.IdSet;
import nl.tno.fpai.demo.scenario.data.Scenario;
import nl.tno.fpai.demo.scenario.data.ScenarioConfiguration;
import nl.tno.fpai.demo.scenario.data.ScenarioConfiguration.Type;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.ConfigurationPolicy;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true,
provide = { ScenarioManager.class },
designate = ScenarioManagerImpl.Config.class,
configurationPolicy = ConfigurationPolicy.optional)
public class ScenarioManagerImpl implements ScenarioManager {
    public interface Config {
        @Meta.AD(deflt = "scenarios.xml",
                description = "The file that should be loaded during activation.",
                required = false)
        String filename();
    }

    private static final Logger log = LoggerFactory.getLogger(ScenarioManagerImpl.class);

    private ConfigurationAdmin configurationAdmin;

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    private MetaTypeService metatype;

    @Reference
    public void setMetatype(MetaTypeService metatype) {
        this.metatype = metatype;
    }

    private final Set<Configuration> configurations = Collections.synchronizedSet(new HashSet<Configuration>());
    private Map<String, Scenario> scenarios;

    @Activate
    public void activate(BundleContext context, Map<String, ?> properties) throws Exception {
        Config config = Configurable.createConfigurable(Config.class, properties);
        String filename = config.filename();

        File file = new File(filename);
        InputStream is = null;
        if (file.exists() && file.isFile() && file.canRead()) {
            is = new FileInputStream(file);
        } else {
            is = getClass().getClassLoader().getResourceAsStream(filename);
        }

        try {
            if (is == null) {
                throw new FileNotFoundException("Can not find the file with name: " + filename);
            }

            scenarios = ScenarioReader.readScenarios(new InputStreamReader(is));

            if (log.isDebugEnabled()) {
                log.debug("Loaded scenarios");
                for (Entry<String, Scenario> entry : scenarios.entrySet()) {
                    log.debug("Loaded scenario [{}]\n{}", entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception ex) {
            log.warn("Could not load scenarios from file [" + filename + "]", ex);
            throw ex;
        } finally {
            is.close();
        }

        setStatus("Loaded scenario's, nothing started");
    }

    @Deactivate
    public void deactivate() {
        purgeAll();
    }

    @Override
    public Set<String> getScenarios() {
        return scenarios.keySet();
    }

    @Override
    public synchronized void startScenario(String name) {
        Scenario scenario = scenarios.get(name);
        if (scenario != null) {
            purgeAll();
            startScenario(scenario);
            setStatus("Loaded scenario [" + name + "]");
        }
    }

    private volatile String status;
    private volatile int count, total;

    private void setStatus(int count) {
        this.count = count;

        if (log.isDebugEnabled()) {
            log.debug("Status = {}", getStatus());
        }
    }

    private void setStatus(String status) {
        setStatus(status, 0);
    }

    private void setStatus(String status, int total) {
        count = 0;
        this.total = total;
        this.status = status;

        if (log.isDebugEnabled()) {
            log.debug("Status = {}", getStatus());
        }
    }

    public String getStatus() {
        if (total <= 0) {
            return status;
        } else {
            return status + " " + count + "/" + total;
        }
    }

    private void purgeAll() {
        int count = 0;
        setStatus("Purging old configurations", configurations.size());
        for (Configuration configuration : configurations) {
            try {
                configuration.delete();
                setStatus(++count);
            } catch (IOException e) {
                log.warn("Could not delete configuration " + configuration, e);
            }
        }
        configurations.clear();
        setStatus("Loaded scenario's, nothing started");
    }

    private void startScenario(Scenario scenario) {
        setStatus("Starting scenario [" + scenario.getName() + "] ", scenario.getConfigurations().size());

        Map<String, Set<String>> idMap = new HashMap<String, Set<String>>();
        idMap = new HashMap<String, Set<String>>();

        // Generate the needed ids
        AtomicLong idGenerator = new AtomicLong();
        for (IdSet idSet : scenario.getIdSets().values()) {
            Set<String> ids = new HashSet<String>();
            for (int i = 0; i < idSet.getCount(); i++) {
                ids.add(Long.toString(idGenerator.getAndIncrement()));
            }
            idMap.put(idSet.getName(), ids);
        }

        int count = 0;
        for (ScenarioConfiguration config : scenario.getConfigurations()) {
            startConfiguration(config, idMap);
            setStatus(++count);
        }
    }

    private void startConfiguration(ScenarioConfiguration config, Map<String, Set<String>> idMap) {
        try {
            Bundle bundle = getBundle(config.getBundleId());
            if (bundle != null) {
                Set<String> ids = idMap.get(config.getIdRef());
                if (ids != null) {
                    for (String id : ids) {
                        startConfiguration(config, bundle, id, idMap);
                    }
                } else {
                    String id = UUID.randomUUID().toString();
                    startConfiguration(config, bundle, id, idMap);
                }
            } else {
                log.info("Ignoring configuration, the given bundle can not be found\n" + config);
            }
        } catch (Exception ex) {
            log.error("Could not create configuration", ex);
        }
    }

    private void startConfiguration(ScenarioConfiguration config,
                                    Bundle bundle,
                                    String id,
                                    Map<String, Set<String>> idMap) throws IOException {
        Dictionary<String, String[]> properties = translateProperties(config.getProperties(), id, idMap);
        MetaTypeInformation metaTypeInformation = metatype.getMetaTypeInformation(bundle);
        ObjectClassDefinition objectClassDefinition = metaTypeInformation.getObjectClassDefinition(config.getId(), null);
        Dictionary<String, Object> transformedProperties = transformTypes(objectClassDefinition, properties);

        Configuration configuration;
        if (config.getType() == Type.MULTIPLE) {
            configuration = configurationAdmin.createFactoryConfiguration(config.getId(), bundle.getLocation());
        } else {
            configuration = configurationAdmin.getConfiguration(config.getId(), bundle.getLocation());
        }
        configuration.update(transformedProperties);
        configurations.add(configuration);
    }

    private Dictionary<String, String[]> translateProperties(Map<String, String> properties,
                                                             String id,
                                                             Map<String, Set<String>> idMap) {
        Dictionary<String, String[]> result = new Hashtable<String, String[]>();
        for (Entry<String, String> entry : properties.entrySet()) {
            result.put(entry.getKey(), translateValue(Arrays.asList(entry.getValue().split(",")), id, idMap));
        }
        return result;
    }

    private String[] translateValue(List<String> values, String id, Map<String, Set<String>> idMap) {
        List<String> newValues = new ArrayList<String>(values.size());
        for (String value : values) {
            newValues.add(value.replace("%id%", id));
        }

        List<String> toReplace = new ArrayList<String>();
        for (Entry<String, Set<String>> idEntry : idMap.entrySet()) {
            toReplace.clear();
            String toMatch = "%" + idEntry.getKey() + "%";

            Iterator<String> it = newValues.iterator();
            while (it.hasNext()) {
                String value = it.next();
                if (value.contains(toMatch)) {
                    toReplace.add(value);
                    it.remove();
                }
            }

            for (String value : toReplace) {
                for (String replace : idEntry.getValue()) {
                    newValues.add(value.replace(toMatch, replace));
                }
            }
        }

        if (newValues.size() == 0) {
            return null;
        } else if (newValues.size() == 1) {
            return new String[] { newValues.get(0) };
        } else {
            return newValues.toArray(new String[newValues.size()]);
        }
    }

    private Dictionary<String, Object> transformTypes(ObjectClassDefinition objectClassDefinition,
                                                      Dictionary<String, String[]> properties) {
        Dictionary<String, Object> result = new Hashtable<String, Object>();

        for (AttributeDefinition attributeDefinition : objectClassDefinition.getAttributeDefinitions(ObjectClassDefinition.ALL)) {
            String key = attributeDefinition.getID();
            String[] current = properties.get(key);
            if (current == null) {
                current = attributeDefinition.getDefaultValue();
            }

            if (attributeDefinition.getCardinality() < 0) {
                // Should use a vector
                Vector<Object> vector = new Vector<Object>(current.length);
                for (String value : current) {
                    vector.add(parse(attributeDefinition.getType(), value));
                }
            } else if (attributeDefinition.getCardinality() > 0) {
                // Should use an array
                result.put(key, parse(attributeDefinition.getType(), current));
            } else {
                // Must be a single value
                result.put(key, parse(attributeDefinition.getType(), current[0]));
            }
        }

        return result;
    }

    private Object parse(int type, String[] value) {
        switch (type) {
        case AttributeDefinition.BOOLEAN: {
            boolean[] array = new boolean[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Boolean.parseBoolean(value[ix]);
            }
            return array;
        }
        case AttributeDefinition.BYTE: {
            byte[] array = new byte[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Byte.parseByte(value[ix]);
            }
            return array;
        }
        case AttributeDefinition.CHARACTER: {
            char[] array = new char[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = value[ix].isEmpty() ? ' ' : value[ix].charAt(0);
            }
            return array;
        }
        case AttributeDefinition.DOUBLE: {
            double[] array = new double[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Double.parseDouble(value[ix]);
            }
            return array;
        }
        case AttributeDefinition.FLOAT: {
            float[] array = new float[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Float.parseFloat(value[ix]);
            }
            return array;
        }
        case AttributeDefinition.INTEGER: {
            int[] array = new int[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Integer.parseInt(value[ix]);
            }
            return array;
        }
        case AttributeDefinition.LONG: {
            long[] array = new long[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Long.parseLong(value[ix]);
            }
            return array;
        }
        case AttributeDefinition.SHORT: {
            short[] array = new short[value.length];
            for (int ix = 0; ix < array.length; ix++) {
                array[ix] = Short.parseShort(value[ix]);
            }
            return array;
        }
        default: {
            return value;
        }
        }
    }

    private Object parse(int type, String value) {
        switch (type) {
        case AttributeDefinition.BOOLEAN:
            return Boolean.parseBoolean(value);
        case AttributeDefinition.BYTE:
            return Byte.parseByte(value);
        case AttributeDefinition.CHARACTER:
            return value.isEmpty() ? ' ' : value.charAt(0);
        case AttributeDefinition.DOUBLE:
            return Double.parseDouble(value);
        case AttributeDefinition.FLOAT:
            return Float.parseFloat(value);
        case AttributeDefinition.INTEGER:
            return Integer.parseInt(value);
        case AttributeDefinition.LONG:
            return Long.parseLong(value);
        case AttributeDefinition.SHORT:
            return Short.parseShort(value);
        default:
            return value;
        }
    }

    private Bundle getBundle(String bundleId) {
        for (Bundle bundle : FrameworkUtil.getBundle(getClass()).getBundleContext().getBundles()) {
            if (bundleId.equals(bundle.getSymbolicName())) {
                return bundle;
            }
        }
        return null;
    }
}
