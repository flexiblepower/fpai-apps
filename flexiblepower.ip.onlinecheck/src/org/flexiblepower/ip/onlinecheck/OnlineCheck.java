package org.flexiblepower.ip.onlinecheck;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.flexiblepower.observation.Observation;
import org.flexiblepower.observation.ObservationProvider;
import org.flexiblepower.observation.ext.AbstractObservationProvider;
import org.flexiblepower.observation.ext.ObservationProviderRegistrationHelper;
import org.flexiblepower.time.TimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.annotation.component.Activate;
import aQute.bnd.annotation.component.Component;
import aQute.bnd.annotation.component.Deactivate;
import aQute.bnd.annotation.component.Reference;
import aQute.bnd.annotation.metatype.Configurable;
import aQute.bnd.annotation.metatype.Meta;

@Component(provide = {}, designate = OnlineCheck.Config.class)
public class OnlineCheck extends AbstractObservationProvider<OnlineCheck.Status> implements
                                                                                ObservationProvider<OnlineCheck.Status>,
                                                                                Runnable {
    private static final Logger log = LoggerFactory.getLogger(OnlineCheck.class);

    public static interface Config {
        @Meta.AD(description = "The hostnames that should be checked. This can be an IP address (e.g. 192.168.0.1 or [fe80::1]), a local hostname (e.g. \"router\") or a fully qualified name (e.g. \"www.google.com\")")
                String[]
                hostnames();

        @Meta.AD(deflt = "30", description = "The interval at which we should check if the given hosts are online.")
        int interval();
    }

    public static class Status {
        private final String hostname;
        private final boolean online;

        public Status(String hostname, boolean online) {
            this.hostname = hostname;
            this.online = online;
        }

        public String getHostname() {
            return hostname;
        }

        public boolean isOnline() {
            return online;
        }

        @Override
        public String toString() {
            return "Host [" + hostname + "] is" + (online ? "" : " not") + " online";
        }
    }

    private ScheduledExecutorService executorService;

    @Reference
    public void setExecutorService(ScheduledExecutorService executorService) {
        this.executorService = executorService;
    }

    private TimeService timeService;

    @Reference
    public void setTimeService(TimeService timeService) {
        this.timeService = timeService;
    }

    private String[] hostnames;
    private int timeout;
    private ScheduledFuture<?> future;

    @Activate
    public void activate(Map<String, ?> properties) {
        Config config = Configurable.createConfigurable(Config.class, properties);
        hostnames = config.hostnames();
        timeout = Math.min(2500, (config.interval() * 1000) / hostnames.length);
        future = executorService.scheduleAtFixedRate(this, 0, config.interval(), TimeUnit.SECONDS);

        new ObservationProviderRegistrationHelper(this).observationOf("hostnames")
                                                       .observationType(Status.class)
                                                       .register();
    }

    @Deactivate
    public void deactivate() {
        future.cancel(true);
    }

    @Override
    public void run() {
        for (String hostname : hostnames) {
            if (Thread.interrupted()) {
                break;
            }

            try {
                boolean online = InetAddress.getByName(hostname).isReachable(timeout);
                Status status = new Status(hostname, online);
                publish(new Observation<Status>(timeService.getTime(), status));
                log.debug("Status {}", status);
            } catch (UnknownHostException ex) {
                log.warn("Unknow host [" + hostname + "}", ex);
                publish(new Observation<Status>(timeService.getTime(), new Status(hostname, false)));
            } catch (IOException ex) {
                log.error("I/O Error while trying to reach the hostname [" + hostname + "]", ex);
                publish(new Observation<Status>(timeService.getTime(), new Status(hostname, false)));
            }
        }
    }
}
