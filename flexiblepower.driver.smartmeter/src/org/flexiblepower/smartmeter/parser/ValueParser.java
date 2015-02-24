package org.flexiblepower.smartmeter.parser;

import java.math.BigDecimal;

public interface ValueParser {
    BigDecimal parse(String value);
}
