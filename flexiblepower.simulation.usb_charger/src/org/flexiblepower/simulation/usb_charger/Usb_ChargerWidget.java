package org.flexiblepower.simulation.usb_charger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.flexiblepower.ui.Widget;

public class Usb_ChargerWidget implements Widget {
    public static class Update {
        private int Consumption;

        public Update() {
        }

        public Update(int Consumption) {
            this.Consumption = Consumption;
        }

        public int getConsumption() {
            return Consumption;
        }

    }

    private static final DateFormat FORMATTER = new SimpleDateFormat("HH:mm:ss");

    private final Usb_Charger simulation;

    public Usb_ChargerWidget(Usb_Charger simulation) {
        this.simulation = simulation;
    }

    public Update GetCurrentConsumption() {
        simulation.GetCurrentConsumption();
        return new Update(simulation.GetCurrentConsumption());
    }

    public Update turnChargerOn() throws MqttPersistenceException, MqttException {
        simulation.setChargerState(true);
        return new Update(simulation.GetCurrentConsumption());
    }

    public Update turnChargerOff() throws MqttPersistenceException, MqttException {
        simulation.setChargerState(false);
        return new Update(simulation.GetCurrentConsumption());
    }

    @Override
    public String getTitle(Locale locale) {
        return "Usb charger";
    }
}
