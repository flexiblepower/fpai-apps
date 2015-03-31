package org.flexiblepower.driver.pv.sma.impl;

import java.io.Closeable;

import org.flexiblepower.driver.pv.sma.impl.data.OperationInfo;
import org.flexiblepower.driver.pv.sma.impl.data.ProductionInfo;
import org.flexiblepower.driver.pv.sma.impl.data.SpotAcInfo;
import org.flexiblepower.driver.pv.sma.impl.utils.BackoffTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistentSMA implements Closeable {

    private static final String INVERTER_ADDRESS = "00802529EC47";
    private static final String INVERTER_PASSWORD = "0000";

    private static final Logger logger = LoggerFactory.getLogger(PersistentSMA.class);

    private final String inverterAddress;
    private final String inverterPassword;
    private final SMA sma;
    private final BackoffTimer backoffTimer;
    private volatile boolean isConnected;

    public static void main(String[] args) {
        PersistentSMA persistentSMA = new PersistentSMA(INVERTER_ADDRESS, INVERTER_PASSWORD);
        persistentSMA.requestOperationInfo(1);
        persistentSMA.requestProductionInfo(2);
        persistentSMA.requestSpotAcInfo(3);
        persistentSMA.close();
    }

    public PersistentSMA(String inverterAddress, String inverterPassword) {
        this.inverterAddress = inverterAddress;
        this.inverterPassword = inverterPassword;
        sma = new SMA();
        backoffTimer = new BackoffTimer(5000, 1.5, 20000);
        isConnected = false;
    }

    @Override
    public void close() {
        isConnected = false;
        sma.closeConnection();
    }

    private int openConnection(String inverter, String password, int maxAttempts) {
        while (!isConnected && maxAttempts > 0) {
            try {
                sma.openConnection(inverter, password);
                isConnected = true;
            } catch (Exception e) {
                logger.warn("Could not open connection with SMA inverter, retrying...", e);
                maxAttempts--;
                backoffTimer.sleep();
            }
        }
        backoffTimer.reset();
        return maxAttempts;
    }

    public OperationInfo requestOperationInfo(int maxAttempts) {
        OperationInfo result = null;
        while (result == null && maxAttempts > 0) {
            maxAttempts = openConnection(inverterAddress, inverterPassword, maxAttempts);
            try {
                result = sma.requestOperationInfo();
            } catch (Exception e) {
                logger.warn("Could not parse operation info from SMA inverter, retrying...", e);
                maxAttempts--;
                backoffTimer.sleep();
                close();
            }
        }
        backoffTimer.reset();
        return result;
    }

    public ProductionInfo requestProductionInfo(int maxAttempts) {
        ProductionInfo result = null;
        while (result == null && maxAttempts > 0) {
            maxAttempts = openConnection(inverterAddress, inverterPassword, maxAttempts);
            try {
                result = sma.requestProductionInfo();
            } catch (Exception e) {
                logger.warn("Could not parse production info from SMA inverter, retrying...", e);
                maxAttempts--;
                backoffTimer.sleep();
                close();
            }
        }
        backoffTimer.reset();
        return result;
    }

    public SpotAcInfo requestSpotAcInfo(int maxAttempts) {
        SpotAcInfo result = null;
        while (result == null && maxAttempts > 0) {
            maxAttempts = openConnection(inverterAddress, inverterPassword, maxAttempts);
            try {
                result = sma.requestSpotAcInfo();
            } catch (Exception e) {
                logger.warn("Could not parse spot AC info from SMA inverter, retrying...", e);
                maxAttempts--;
                backoffTimer.sleep();
                close();
            }
        }
        backoffTimer.reset();
        return result;
    }
}
