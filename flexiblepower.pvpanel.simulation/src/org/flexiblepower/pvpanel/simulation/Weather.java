package org.flexiblepower.pvpanel.simulation;

public enum Weather {
    moon {
        @Override
        double getProduction(double randomFactor) {
            return 0;
        }
    },
    clouds {
        @Override
        double getProduction(double randomFactor) {
            return 200 + 201 * randomFactor;
        }
    },
    sun {
        @Override
        double getProduction(double randomFactor) {
            return 1500 + 101 * randomFactor;
        }
    };

    abstract double getProduction(double randomFactor);
}
