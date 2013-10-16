package nl.tno.fpai.monitoring.test;

import javax.measure.Measurable;
import javax.measure.quantity.Power;

public interface SomeValues {
    double getValue1();

    String getValue2();

    Measurable<Power> getValue3();
}
