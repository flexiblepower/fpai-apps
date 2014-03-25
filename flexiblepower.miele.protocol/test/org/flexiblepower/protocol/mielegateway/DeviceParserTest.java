package org.flexiblepower.protocol.mielegateway;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.flexiblepower.protocol.mielegateway.xml.Device;
import org.flexiblepower.protocol.mielegateway.xml.XMLUtil;

public class DeviceParserTest {
    public static void main(String[] args) throws MalformedURLException {
        List<Device> devices = Device.parseDevices(XMLUtil.get()
                                                          .parseXml(new URL("http://miele-gateway.labsgn.tno.nl/homebus"))
                                                          .getDocumentElement());
        System.out.println(devices);

        for (Device device : devices) {
            Device detailDevice = Device.parseDevice(XMLUtil.get()
                                                            .parseXml(device.getActions().get("Details"))
                                                            .getDocumentElement());
            System.out.println(detailDevice);
        }
    }
}
