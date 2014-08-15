package org.flexiblepower.smartmeter.parser;

public class DatagramCleaner {

    public static String[] asArray(String source) {

        // Remove windowsy line ends
        source = source.replaceAll("\\r", "");

        // Put gas measurements on one line
        source = source.replaceAll("\\(m3\\)\\n", "(m3)");

        return source.split("\\n");
    }
}
