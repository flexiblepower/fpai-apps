package org.flexiblepower.protocol.mielegateway.multicast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flexiblepower.protocol.mielegateway.MieleProtocolHandler;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.ConfigurationPolicy;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true,
           provide = {},
           designate = MieleMulticastHandler.Config.class,
           configurationPolicy = ConfigurationPolicy.optional)
public class MieleMulticastHandler {
    private static final Logger log = LoggerFactory.getLogger(MieleMulticastHandler.class);

    private static final Pattern DATA_PATTERN = Pattern.compile("type=[^&]+&id=[^&.]+.([-0-9]+)&svar=([^&]+)&value=([^&]+)");

    @Meta.OCD(description = "Configuration for the multicast service that reads multicast messages from the Miele home gateway.")
    public static interface Config {
        @Meta.AD(deflt = "true",
                 description = "Should automatically create new handlers when a Miele home gateway is detected",
                 required = false)
        public boolean automaticHandlerCreation();
    }

    private ConfigurationAdmin configAdmin;

    @Reference(optional = true)
    public void setConfigurationAdmin(ConfigurationAdmin configAdmin) {
        this.configAdmin = configAdmin;
    }

    private final Map<InetAddress, MieleProtocolHandler> protocolHandlers = new ConcurrentHashMap<InetAddress, MieleProtocolHandler>();

    private static InetAddress getAddress(Object value) {
        if (value != null) {
            try {
                return InetAddress.getByName(value.toString());
            } catch (UnknownHostException e) {
                log.warn("Ignoring hostname [{}], it is not a known hostname. Maybe the DNS in not configured correctly?",
                         e);
            }
        }
        return null;
    }

    private static InetAddress getAddress(Map<String, Object> dictionary) {
        return getAddress(dictionary.get("hostname"));
    }

    private static InetAddress getAddress(Dictionary<String, Object> dictionary) {
        return getAddress(dictionary.get("hostname"));
    }

    @Reference(optional = true, dynamic = true, multiple = true)
    public void addMieleProtocolHandler(MieleProtocolHandler protocolHandler, Map<String, Object> properties) {
        InetAddress address = getAddress(properties);
        if (address != null && !protocolHandlers.containsKey(address)) {
            protocolHandlers.put(address, protocolHandler);
        }
    }

    public void removeMieleProtocolHandler(MieleProtocolHandler protocolHandler, Map<String, Object> properties) {
        protocolHandlers.remove(getAddress(properties));
    }

    private MieleGatewayDetector gatewayDetector;
    private MieleUpdateHandler updateHandler;

    public void activate(BundleContext context, Map<String, Object> properties) {
        Config config = Configurable.createConfigurable(MieleMulticastHandler.Config.class, properties);

        if (config.automaticHandlerCreation()) {
            if (configAdmin == null) {
                log.warn("Can not automatically create configuration for new Miele Gateways, missing ConfigurationAdmin");
            } else {
                gatewayDetector = new MieleGatewayDetector();
            }
        }
        updateHandler = new MieleUpdateHandler();
    }

    @Deactivate
    public void deactivate() {
        updateHandler.close();
        if (gatewayDetector != null) {
            gatewayDetector.close();
        }
    }

    private class MieleGatewayDetector extends MulticastListenerThread {
        public MieleGatewayDetector() {
            super("Miele Gateway Detector Thread", "239.255.68.138", 1609);
        }

        @Override
        protected void handle(String data, InetAddress address, int port) {
            if (data.startsWith("http") && data.endsWith("/homebus/") && !protocolHandlers.containsKey(address)) {
                // Found Miele gateway on address
                try {
                    synchronized (MieleMulticastHandler.this) {
                        Configuration[] configurations = configAdmin.listConfigurations("(" + Constants.OBJECTCLASS
                                                                                        + "="
                                                                                        + MieleProtocolHandler.class.getName()
                                                                                        + ")");
                        boolean foundConfig = false;
                        if (configurations != null) {
                            for (Configuration config : configurations) {
                                if (address.equals(getAddress(config.getProperties()))) {
                                    foundConfig = true;
                                    break;
                                }
                            }
                        }

                        if (!foundConfig) {
                            log.debug("Creating new config for address {}", address);
                            // Create new config
                            Dictionary<String, Object> properties = new Hashtable<String, Object>();
                            properties.put("hostname", address.getHostAddress());
                            properties.put("pollingTime", 60);
                            Configuration configuration = configAdmin.createFactoryConfiguration(MieleProtocolHandler.class.getName());
                            configuration.update(properties);
                        }
                    }
                } catch (IOException e) {
                } catch (InvalidSyntaxException e) {
                }
            }
        }
    }

    private class MieleUpdateHandler extends MulticastListenerThread {
        public MieleUpdateHandler() {
            super("Miele Update Handler Thread", "239.255.68.139", 2810);
        }

        @Override
        protected void handle(String data, InetAddress address, int port) {
            MieleProtocolHandler protocolHandler = protocolHandlers.get(address);
            if (protocolHandler != null) {
                Matcher matcher = DATA_PATTERN.matcher(data);
                if (matcher.matches()) {
                    int id = Integer.parseInt(matcher.group(1));
                    String key = matcher.group(2);
                    String value = matcher.group(3);
                    protocolHandler.handleUpdate(id, key, value);
                    log.debug("Got update id={} key={} value={}", id, key, value);
                }
            }
        }
    }
}
