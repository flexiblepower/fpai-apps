package nl.tno.hexabus.driver;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Test {
    public static void main(String[] args) throws IOException {
        Logger logger = LoggerFactory.getLogger(Test.class);
        HexabusLifecycle lifecycle = new HexabusLifecycle();
        new Thread(lifecycle).start();

        boolean on = false;
        while (true) {
            int read = System.in.read();
            if (read == 'x') {
                break;
            } else if (read == '\n') {
                logger.debug("Switching all switches to {}", on ? "on" : "off");
                lifecycle.switchAllTo(on);
                on = !on;
            }
        }
        lifecycle.deactivate();
    }
}
