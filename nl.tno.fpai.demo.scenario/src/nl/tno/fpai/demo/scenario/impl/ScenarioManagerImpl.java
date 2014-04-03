package nl.tno.fpai.demo.scenario.impl;

import java.io.FileInputStream;
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
        @Meta.AD(deflt = "", description = "The file that should be loaded during activation.", required = false)
        String filename();
    }

    private static final Logger log = LoggerFactory.getLogger(ScenarioManagerImpl.class);

    private ConfigurationAdmin configurationAdmin;

    @Reference
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    private final Set<Configuration> configurations = Collections.synchronizedSet(new HashSet<Configuration>());
    private Map<String, Scenario> scenarios;
    private volatile String status;

    @Activate
    public void activate(BundleContext context, Map<String, ?> properties) throws IOException {
        Config config = Configurable.createConfigurable(Config.class, properties);
        String filename = config.filename();

        if (filename == null || filename.isEmpty()) {
            // No file given, ignore
            return;
        }

        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);
        if (is == null) {
            is = new FileInputStream(filename);
        }

        try {
            scenarios = ScenarioReader.readScenarios(new InputStreamReader(is));
        } catch (IOException ex) {
            log.warn("Could not load scenarios from file [" + filename + "]", ex);
            throw ex;
        } finally {
            is.close();
        }

        status = "Loaded scenario's, nothing started";
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
            status = "Loaded scenario [" + name + "]";
        }
    }

    public String getStatus() {
        return status;
    }

    private void purgeAll() {
        int count = 0;
        status = "Purging old configurations " + count + "/" + configurations.size();
        for (Configuration configuration : configurations) {
            try {
                configuration.delete();
                count++;
                status = "Purging old configurations " + count + "/" + configurations.size();
            } catch (IOException e) {
                log.warn("Could not delete configuration " + configuration, e);
            }
        }
        configurations.clear();
        status = "Loaded scenario's, nothing started";
    }

    private void startScenario(Scenario scenario) {
        status = "Starting scenario [" + scenario.getName() + "] ";

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
        status = "Starting scenario [" + scenario.getName() + "] " + count + "/" + scenario.getConfigurations().size();
        for (ScenarioConfiguration config : scenario.getConfigurations()) {
            startConfiguration(config, idMap);
            count++;
            status = "Starting scenario [" + scenario.getName()
                     + "] "
                     + count
                     + "/"
                     + scenario.getConfigurations().size();
        }
    }

    private void startConfiguration(ScenarioConfiguration config, Map<String, Set<String>> idMap) {
        try {
            String location = getBundleLocation(config.getBundleId());
            if (location != null) {
                Set<String> ids = idMap.get(config.getIdRef());
                if (ids != null) {
                    for (String id : ids) {
                        startConfiguration(config, location, id, idMap);
                    }
                } else {
                    String id = UUID.randomUUID().toString();
                    startConfiguration(config, location, id, idMap);
                }
            } else {
                log.info("Ignoring configuration, the given bundle can not be found\n" + config);
            }
        } catch (IOException ex) {
            log.error("Could not create configuration", ex);
        }
    }

    private void startConfiguration(ScenarioConfiguration config,
                                    String location,
                                    String id,
                                    Map<String, Set<String>> idMap) throws IOException {
        Configuration configuration;
        if (config.getType() == Type.MULTIPLE) {
            configuration = configurationAdmin.createFactoryConfiguration(config.getId(), location);
        } else {
            configuration = configurationAdmin.getConfiguration(config.getId(), location);
        }
        configuration.update(translateProperties(config.getProperties(), id, idMap));
        configurations.add(configuration);
    }

    private Dictionary<String, ?> translateProperties(Map<String, String> properties,
                                                      String id,
                                                      Map<String, Set<String>> idMap) {
        Dictionary<String, Object> result = new Hashtable<String, Object>();
        for (Entry<String, String> entry : properties.entrySet()) {
            Object value = translate(Arrays.asList(entry.getValue().split(",")), id, idMap);
            if (value != null) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private Object translate(List<String> values, String id, Map<String, Set<String>> idMap) {
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
            return newValues.get(0);
        } else {
            return newValues.toArray(new String[newValues.size()]);
        }
    }

    private String getBundleLocation(String bundleId) {
        for (Bundle bundle : FrameworkUtil.getBundle(getClass()).getBundleContext().getBundles()) {
            if (bundleId.equals(bundle.getSymbolicName())) {
                return bundle.getLocation();
            }
        }
        return null;
    }
}
