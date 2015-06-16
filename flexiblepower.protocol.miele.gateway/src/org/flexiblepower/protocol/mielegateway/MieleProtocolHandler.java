package org.flexiblepower.protocol.mielegateway;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import javax.measure.Measure;
import javax.measure.unit.SI;

import org.flexiblepower.context.FlexiblePowerContext;
import org.flexiblepower.protocol.mielegateway.MieleProtocolHandler.Config;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.protocol.mielegateway.xml.Device;
import org.flexiblepower.protocol.mielegateway.xml.XMLUtil;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true, provide = MieleProtocolHandler.class, designateFactory = Config.class)
public class MieleProtocolHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MieleProtocolHandler.class);

    @Meta.OCD(description = "Protocol handler for the Miele Gateway")
    public static interface Config {
        @Meta.AD(deflt = "miele-gateway.labsgn.tno.nl", description = "The hostname where the gateway can be reached")
        public String hostname();

        @Meta.AD(deflt = "10", description = "The time in seconds between polls")
        public int pollingTime();
    }

    private FlexiblePowerContext fpContext;

    @Reference
    public void setContext(FlexiblePowerContext fpContext) {
        this.fpContext = fpContext;
    }

    private final List<MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>>> factories = new CopyOnWriteArrayList<MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>>>();

    @Reference(optional = true, dynamic = true, multiple = true)
    public void addMieleResourceDriverFactory(MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>> factory) {
        factories.add(factory);
    }

    public void removeMieleResourceDriverFactory(MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>> factory) {
        factories.remove(factory);
    }

    private URL homebusURL;

    private ScheduledFuture<?> future;

    @Activate
    public void activate(BundleContext context, Map<String, Object> properties) throws MalformedURLException {
        Config config = Configurable.createConfigurable(MieleProtocolHandler.Config.class, properties);
        homebusURL = new URL("http://" + config.hostname() + "/homebus/?language=en");

        future = fpContext.scheduleWithFixedDelay(this,
                                                  Measure.valueOf(0, SI.SECOND),
                                                  Measure.valueOf(config.pollingTime(), SI.SECOND));
    }

    @Deactivate
    public void deactivate() {
        try {
            future.cancel(false);
            for (MieleResourceDriverWrapper wrapper : wrappers.values()) {
                wrapper.close();
            }
            wrappers.clear();
        } catch (IOException e) {
            log.warn("Exception while closing MieleResourceDriver", e);
        }
    }

    private final Map<Integer, MieleResourceDriverWrapper> wrappers = new HashMap<Integer, MieleResourceDriverWrapper>();

    public void handleUpdate(int id, String key, String value) {
        MieleResourceDriverWrapper wrapper = wrappers.get(id);
        if (wrapper != null) {
            updateDriver(wrapper);
        }
    }

    @Override
    public synchronized void run() {
        Document document = XMLUtil.get().parseXml(homebusURL);
        if (document != null) {
            List<Device> devicesResult = Device.parseDevices(document.getDocumentElement());

            log.debug("Received devices: {}", devicesResult);

            for (Device deviceResult : devicesResult) {
                if (deviceResult.getName() != null && deviceResult.getId() != null) {
                    MieleResourceDriverWrapper wrapper = getWrapper(deviceResult.getId(), deviceResult);
                    if (wrapper != null) {
                        updateDriver(wrapper);
                    } else {
                        log.warn("Got a device for which there is no driver available. (Name={}, Type={})",
                                 deviceResult.getName(),
                                 deviceResult.getType());
                    }
                }
            }
        }
    }

    private MieleResourceDriverWrapper getWrapper(int id, Device deviceResult) {
        if (!wrappers.containsKey(id) && deviceResult.getActions().containsKey("Details")) {
            for (MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>> factory : factories) {
                if (factory.canHandleType(deviceResult.getType())) {
                    MieleResourceDriverWrapper wrapper = new MieleResourceDriverWrapper();
                    MieleResourceDriver<?, ?> driver = factory.create(deviceResult.getName(), wrapper);
                    wrapper.setDriver(driver);
                    wrapper.setDetailsURL(deviceResult.getActions().get("Details"));
                    wrappers.put(id, wrapper);

                    log.info("Created driver for {}", deviceResult.getName());
                    break;
                }
            }
        }

        return wrappers.get(id);
    }

    private void updateDriver(MieleResourceDriverWrapper wrapper) {
        Document detailDoc = XMLUtil.get().parseXml(wrapper.getDetailsURL());
        if (detailDoc != null) {
            Device detailDevice = Device.parseDevice(detailDoc.getDocumentElement());
            log.debug("Got device update wrapper={} information={} actions={}",
                      wrapper,
                      detailDevice.getInformation(),
                      detailDevice.getActions());
            if (detailDevice != null) {
                wrapper.updateState(detailDevice.getInformation(), detailDevice.getActions());
            }
        }
    }
}
