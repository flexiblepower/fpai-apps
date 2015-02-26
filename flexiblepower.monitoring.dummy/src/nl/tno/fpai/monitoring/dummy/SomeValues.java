package nl.tno.fpai.monitoring.dummy;


/** Specification of some dummy values. */
public interface SomeValues {
    /** @return Some other values. */
    SomeOtherValues getSomeOtherValues();

    /** @return Some dummy value. */
    double getValue1();

    /** @return Some dummy value. */
    long getTimestamp();

    /** @return Some dummy value. */
    String getObserverId();

}
