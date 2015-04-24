package org.flexiblepower.uncontrolled.simulation;

public enum Devices {

    off {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            return 0;
        }
    },
    tv {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            return cloudy + cloudy * randomFactor;
        }
    },
    coffee {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            return sunny + (sunny / 10) * randomFactor;
        }
    };

    abstract double getProduction(double randomFactor, double cloudy, double sunny);

}
