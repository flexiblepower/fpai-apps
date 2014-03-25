package org.flexiblepower.protocol.mielegateway;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.flexiblepower.protocol.mielegateway.MieleProtocolHandler.Config;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriver;
import org.flexiblepower.protocol.mielegateway.api.MieleResourceDriverFactory;
import org.flexiblepower.protocol.mielegateway.xml.Device;
import org.flexiblepower.protocol.mielegateway.xml.XMLUtil;
import org.osgi.framework.BundleContext;
import org.w3c.dom.Document;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(immediate = true, provide = {}, designateFactory = Config.class)
public class MieleProtocolHandler implements Runnable {
	@Meta.OCD(description = "Protocol handler for the Miele Gateway")
	public static interface Config {
		@Meta.AD(deflt = "miele-gateway.labsgn.tno.nl", description = "The hostname where the gateway can be reached")
		public String hostname();

		@Meta.AD(deflt = "10", description = "The time in seconds between polls")
		public int pollingTime();
	}

	private ScheduledExecutorService executorService;

	@Reference
	public void setScheduledExecutorService(
			ScheduledExecutorService executorService) {
		this.executorService = executorService;
	}

	private final List<MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>>> factories = new CopyOnWriteArrayList<MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>>>();

	@Reference(optional = true, dynamic = true, multiple = true)
	public void addMieleResourceDriverFactory(
			MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>> factory) {
		factories.add(factory);
	}

	public void removeMieleResourceDriverFactory(
			MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>> factory) {
		factories.remove(factory);
	}

	private URL homebusURL;

	@Activate
	public void activate(BundleContext context, Map<String, Object> properties)
			throws MalformedURLException {
		Config config = Configurable.createConfigurable(
				MieleProtocolHandler.Config.class, properties);
		homebusURL = new URL("http://" + config.hostname() + "/homebus");

		executorService.scheduleWithFixedDelay(this, 0, config.pollingTime(),
				TimeUnit.SECONDS);
	}

	@Deactivate
	public void deactivate() {
	}

	private final Map<String, MieleResourceDriver<?, ?>> drivers = new HashMap<String, MieleResourceDriver<?, ?>>();

	@Override
	public void run() {
		Document document = XMLUtil.get().parseXml(homebusURL);
		if (document != null) {
			List<Device> devices = Device.parseDevices(document
					.getDocumentElement());

			for (Device device : devices) {
				if (device.getName() != null) {
					if (!drivers.containsKey(device.getName())) {
						for (MieleResourceDriverFactory<?, ?, MieleResourceDriver<?, ?>> factory : factories) {
							if (factory.canHandleType(device.getName())) {
								MieleResourceDriver<?, ?> driver = factory
										.create(device.getName());
								drivers.put(device.getName(), driver);
								break;
							}
						}
					}
				}

				MieleResourceDriver<?, ?> driver = drivers
						.get(device.getName());
				if (driver != null
						&& device.getActions().containsKey("Details")) {
					Document detailDoc = XMLUtil.get().parseXml(
							device.getActions().get("Details"));
					if (detailDoc != null) {
						Device detailDevice = Device.parseDevice(detailDoc
								.getDocumentElement());
						driver.updateState(detailDevice.getInformation(),
								detailDevice.getActions());
					}
				}
			}
		}
	}
}
