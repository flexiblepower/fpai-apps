package org.flexiblepower.smartmeter.parser;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class DatagramParser {
    public static final DatagramParser SINGLETON = new DatagramParser();

    private final Map<String, PropertyAndPattern> mapping;

    private DatagramParser() {
        mapping = new HashMap<String, PropertyAndPattern>();
        mapping.put("1-0:1.8.1", new PropertyAndPattern(new KwhValueParser(), "electricityConsumptionLowRateKwh"));
        mapping.put("1-0:1.8.2", new PropertyAndPattern(new KwhValueParser(), "electricityConsumptionNormalRateKwh"));
        mapping.put("1-0:2.8.1", new PropertyAndPattern(new KwhValueParser(), "electricityProductionLowRateKwh"));
        mapping.put("1-0:2.8.2", new PropertyAndPattern(new KwhValueParser(), "electricityProductionNormalRateKwh"));
        mapping.put("1-0:1.7.0", new PropertyAndPattern(new WattValueParser(), "currentPowerConsumptionW"));
        mapping.put("1-0:2.7.0", new PropertyAndPattern(new WattValueParser(), "currentPowerProductionW"));
        mapping.put("0-1:24.3.0", new PropertyAndPattern(new CubicMetreValueParser(), "gasConsumptionM3"));
    }

    public SmartMeterMeasurement parse(String datagram) {

        SmartMeterMeasurement result = new SmartMeterMeasurement();

        String[] datagramLines = DatagramCleaner.asArray(datagram);

        for (String line : datagramLines) {

            for (Map.Entry<String, PropertyAndPattern> entry : mapping.entrySet()) {
                if (line.startsWith(entry.getKey())) {
                    entry.getValue().extract(line, result);
                    break;
                }
            }
        }

        return result;
    }

    private class PropertyAndPattern {

        private final ValueParser valueParser;
        private final String fieldName;

        public PropertyAndPattern(ValueParser valueParser, String fieldName) {
            this.valueParser = valueParser;
            this.fieldName = fieldName;
        }

        public void extract(String line, SmartMeterMeasurement measurement) {
            BigDecimal value = valueParser.parse(line);

            if (fieldName.equals("electricityConsumptionLowRateKwh")) {
                measurement.setElectricityConsumptionLowRateKwh(value);
            }

            else if (fieldName.equals("electricityConsumptionNormalRateKwh")) {
                measurement.setElectricityConsumptionNormalRateKwh(value);
            }

            else if (fieldName.equals("electricityProductionLowRateKwh")) {
                measurement.setElectricityProductionLowRateKwh(value);
            }

            else if (fieldName.equals("electricityProductionNormalRateKwh")) {
                measurement.setElectricityProductionNormalRateKwh(value);
            }

            else if (fieldName.equals("currentPowerConsumptionW")) {
                measurement.setCurrentPowerConsumptionW(value);
            }

            else if (fieldName.equals("currentPowerProductionW")) {
                measurement.setCurrentPowerProductionW(value);
            }
            else if (fieldName.equals("gasConsumptionM3")) {
                measurement.setGasConsumptionM3(value);
            }

        }
    }
}
