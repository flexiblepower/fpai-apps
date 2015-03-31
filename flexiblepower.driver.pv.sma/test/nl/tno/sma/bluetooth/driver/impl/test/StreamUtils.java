package nl.tno.sma.bluetooth.driver.impl.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;

public class StreamUtils {

    public static InputStream toInputStream(String input) {
        byte[] bytes = ByteUtils.hexStringToBytes(input);
        return new ByteArrayInputStream(bytes);
    }

    public static String fromOutputStream(OutputStream os) {
        return ((ByteArrayOutputStream) os).toString();
    }
}
