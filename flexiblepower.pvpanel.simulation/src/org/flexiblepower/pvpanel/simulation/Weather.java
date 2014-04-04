package org.flexiblepower.pvpanel.simulation;

public enum Weather {

    moon {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            return 0;
        }
    },
    clouds {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            return cloudy + cloudy * randomFactor;
        }
    },
    sun {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            return sunny + (sunny / 10) * randomFactor;
        }
    };

    abstract double getProduction(double randomFactor, double cloudy, double sunny);

}
