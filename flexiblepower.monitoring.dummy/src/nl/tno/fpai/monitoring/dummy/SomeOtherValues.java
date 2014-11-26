package nl.tno.fpai.monitoring.dummy;

import javax.measure.Measurable;
import javax.measure.quantity.Power;

/** Specification of some other dummy values. */
public interface SomeOtherValues {
    /** @return Some dummy value. */
    double getValue1();

    /** @return Some dummy value. */
    String getValue2();

    /** @return Some dummy value. */
    Measurable<Power> getValue3();
}
