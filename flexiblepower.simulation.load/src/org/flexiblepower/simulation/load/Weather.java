package org.flexiblepower.simulation.load;


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
            double result = cloudy + cloudy * randomFactor;
            return result;
        }
    },
    sun {
        @Override
        double getProduction(double randomFactor, double cloudy, double sunny) {
            double result = sunny + (sunny / 10.0) * randomFactor;
            return result;
        }
    };

    abstract double getProduction(double randomFactor, double cloudy, double sunny);

}
