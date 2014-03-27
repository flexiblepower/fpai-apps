package flexiblepower.windparkkubbeweg.driver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Measurable;
import javax.measure.Measure;
import javax.measure.quantity.Power;
import javax.measure.unit.SI;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.ral.ResourceDriver;
import org.flexiblepower.ral.drivers.uncontrolled.UncontrolledState;
import org.flexiblepower.ral.ext.UncontrolledResourceDriver;
import org.flexiblepower.time.TimeService;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;
import flexiblepower.windparkkubbeweg.driver.WindparkKubbewegDriver.Config;

/**
 * Virtual windmill based on real data from www.windparkkubbeweg.nl. Production gets updated every minute.
 */
@Component(designateFactory = Config.class, provide = ResourceDriver.class, immediate = true)
public class WindparkKubbewegDriver extends UncontrolledResourceDriver<UncontrolledState> implements Runnable {
    public interface Config {
        @Meta.AD(description = "The url of the page with the windmills",
                 deflt = "http://www.windparkkubbeweg.nl/molens.php")
        String inetAddress();

        @Meta.AD(description = "The resourceId as needed for the FPAI framework to be wired to an energy app",
                 deflt = "windmill")
        String resourceId();

        @Meta.AD(description = "The factor to multiply the production of the windmill with", deflt = "0.001")
        double factor();
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private ScheduledExecutorService scheduler;

    @Reference
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    private static Logger logger = LoggerFactory.getLogger(WindparkKubbewegDriver.class);
    private static Pattern pattern = Pattern.compile("Productie: ([0-9]+)kW");
    private ScheduledFuture<?> scheduledFuture;
    private Config config;
    private ServiceRegistration<?> observationProviderRegistration;

    @Activate
    public void activate(Map<String, ?> properties) {
        config = Configurable.createConfigurable(Config.class, properties);
        observationProviderRegistration = new ObservationProviderRegistrationHelper(this).observationType(UncontrolledState.class)
                                                                                         .observationOf(config.resourceId())
                                                                                         .register();
        scheduledFuture = scheduler.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
    }

    @Deactivate
    public void deactivate() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
        if (observationProviderRegistration != null) {
            observationProviderRegistration.unregister();
            observationProviderRegistration = null;
        }
    }

    @Override
    public void run() {
        try {
            URL url = new URL(config.inetAddress());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = null;
                String production = null;
                while ((line = in.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        // found!
                        production = matcher.group(1);
                        break;
                    }
                }
                in.close();
                if (production != null) {
                    double productionKw = Double.parseDouble(production);
                    final Measurable<Power> demand = Measure.valueOf(-productionKw * config.factor(), SI.KILO(SI.WATT));
                    final Date now = timeService.getTime();
                    UncontrolledState state = new UncontrolledState() {

                        @Override
                        public boolean isConnected() {
                            return true;
                        }

                        @Override
                        public Date getTime() {
                            return now;
                        }

                        @Override
                        public Measurable<Power> getDemand() {
                            return demand;
                        }
                    };
                    logger.debug("Demand of windmill is " + demand);
                    publish(new Observation<UncontrolledState>(now, state));
                } else {
                    logger.warn("Could not find production in " + config.inetAddress());
                }
            } else {
                logger.warn("Received responsecode " + responseCode + " for url " + config.inetAddress());
            }
        } catch (Exception e) {
            logger.warn("Error while retrieving data", e);
        }
    }

}
