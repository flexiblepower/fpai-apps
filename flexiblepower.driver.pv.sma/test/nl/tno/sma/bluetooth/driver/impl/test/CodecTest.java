package nl.tno.sma.bluetooth.driver.impl.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;

import junit.framework.TestCase;

import org.flexiblepower.driver.pv.sma.impl.L1Codec;
import org.flexiblepower.driver.pv.sma.impl.L1Command;
import org.flexiblepower.driver.pv.sma.impl.L1Packet;
import org.flexiblepower.driver.pv.sma.impl.L2Codec;
import org.flexiblepower.driver.pv.sma.impl.L2Command;
import org.flexiblepower.driver.pv.sma.impl.L2Packet;
import org.flexiblepower.driver.pv.sma.impl.data.OperationInfo;
import org.flexiblepower.driver.pv.sma.impl.data.ProductionInfo;
import org.flexiblepower.driver.pv.sma.impl.data.SpotAcInfo;
import org.flexiblepower.driver.pv.sma.impl.response.DataResponsePacket;
import org.flexiblepower.driver.pv.sma.impl.utils.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodecTest extends TestCase {

    private static final String INVERTER_ADDRESS = "00802529EC47";
    private static final String CLIENT_ADDRESS = "464253414D53";

    private final static Logger logger = LoggerFactory.getLogger(CodecTest.class);

    private void checkL1Request(String hex,
                                String expectedSource,
                                String expectedDestination,
                                Object expectedCommand,
                                String expectedData) throws IOException {
        L1Packet l1Packet = L1Codec.readPacket(StreamUtils.toInputStream(hex));

        assertEquals(expectedSource, l1Packet.getSource());
        assertEquals(expectedDestination, l1Packet.getDestination());
        assertEquals(expectedCommand, l1Packet.getCommand());
        assertEquals(expectedData, ByteUtils.toHexString(l1Packet.getData()));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        L1Codec.writePacket(l1Packet, os);
        String constructed = ByteUtils.toHexString(os.toByteArray());

        assertEquals(hex, constructed);
    }

    private void checkL2Request(String hex,
                                String expectedL1Source,
                                String expectedL1Destination,
                                String expectedL2Source,
                                String expectedL2Destination,
                                Object expectedCommand) throws IOException {
        L2Packet l2Packet = L2Codec.readPacket(StreamUtils.toInputStream(hex));

        assertEquals(expectedL1Source, l2Packet.getL1Source());
        assertEquals(expectedL1Destination, l2Packet.getL1Destination());
        assertEquals(expectedL2Source, l2Packet.getL2Source());
        assertEquals(expectedL2Destination, l2Packet.getL2Destination());
        assertEquals(expectedCommand, l2Packet.getCommand());

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        L2Codec.writePacket(l2Packet, os);
        String constructed = ByteUtils.toHexString(os.toByteArray());

        assertEquals(hex, constructed);
    }

    public void testHandshake1() throws IOException {
        logger.debug("testHandshake1");
        checkL1Request("7E1F006147EC29258000000000000000020000047000010000000001000000",
                       INVERTER_ADDRESS,
                       ByteUtils.BROADCAST_ADDRESS,
                       L1Command.HANDSHAKE_1,
                       "00047000010000000001000000");
    }

    public void testHandshake2() throws IOException {
        logger.debug("testHandshake2");
        checkL1Request("7E1F006100000000000047EC29258000020000047000010000000001000000",
                       ByteUtils.BROADCAST_ADDRESS,
                       INVERTER_ADDRESS,
                       L1Command.HANDSHAKE_2,
                       "00047000010000000001000000");
    }

    public void testLogOff() throws IOException {
        logger.debug("testLogOff");
        checkL2Request("7E3A0044000000000000FFFFFFFFFFFF01007EFF03606508A0FFFFFFFFFFFF0003534D4153424600030000000002800E01FDFFFFFFFFFFF5DF7E",
                       ByteUtils.BROADCAST_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       CLIENT_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       L2Command.LOGOFF);
    }

    public void testLogOn() throws IOException {
        logger.debug("testLogOn");
        checkL2Request("7E52002C000000000000FFFFFFFFFFFF01007EFF0360650EA0FFFFFFFFFFFF0001534D4153424600010000000004800C04FDFF07000000840300003785495200000000B8B8B8B888888888888888887F477E",
                       ByteUtils.BROADCAST_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       CLIENT_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       L2Command.LOGON);
    }

    public void testProductionRequest() throws IOException {
        logger.debug("testProductionRequest");
        checkL2Request("7E3E004000000000000047EC2925800001007EFF03606509A0FFFFFFFFFFFF0000534D415342460000000000000A800002005400012600FF2226001C4B7E",
                       ByteUtils.BROADCAST_ADDRESS,
                       INVERTER_ADDRESS,
                       CLIENT_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       L2Command.PRODUCTION);
    }

    public void testProductionResponse() throws IOException, ParseException {
        logger.debug("testProductionResponse");
        String str = "7E5F002147EC292580003CFB0FDC1B0001007EFF0360657D3180534D4153424600A06300B4AE57770000000000000A80010200540000000001000000010126003A8549524D730F0000000000012226003A854952BF1A00000000000066FB7E";
        L2Packet l2Packet = L2Codec.readPacket(StreamUtils.toInputStream(str));
        ProductionInfo productionInfo = new ProductionInfo(DataResponsePacket.parse(l2Packet).getElements());

        assertEquals(new BigDecimal(1012557).divide(new BigDecimal(1000)), productionInfo.getLifetime());
        assertEquals(new BigDecimal(6847).divide(new BigDecimal(1000)), productionInfo.getToday());
    }

    public void testSpotACPowerRequest() throws IOException {
        logger.debug("testSpotACPowerRequest");
        checkL2Request("7E3E004000000000000047EC2925800001007EFF03606509A1FFFFFFFFFFFF0000534D41534246000000000000108000020051003F2600FF3F2600DCDA7E",
                       ByteUtils.BROADCAST_ADDRESS,
                       INVERTER_ADDRESS,
                       CLIENT_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       L2Command.SPOT_AC_POWER);
    }

    public void testSpotACPowerResponse() throws IOException, ParseException {
        logger.debug("testSpotACPowerResponse");
        String str = "7E5A002447EC292580003CFB0FDC1B0001007EFF0360651080534D4153424600A06300B4AE57770000000000001080010200510000000000000000013F26403A8549523903000039030000390300003903000001000000A2D17E";
        L2Packet l2Packet = L2Codec.readPacket(StreamUtils.toInputStream(str));
        SpotAcInfo spotAC = new SpotAcInfo(DataResponsePacket.parse(l2Packet).getElements());

        assertEquals(new BigDecimal(825), spotAC.getPower());
    }

    public void testSpotACFrequencyRequest() throws IOException {
        logger.debug("testSpotACFrequencyRequest");
        checkL2Request("7E3F004100000000000047EC2925800001007EFF03606509A1FFFFFFFFFFFF0000534D415342460000000000007D31800002005100574600FF57460034FF7E",
                       ByteUtils.BROADCAST_ADDRESS,
                       INVERTER_ADDRESS,
                       CLIENT_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       L2Command.SPOT_AC_POWER);
    }

    public void testSpotACFrequencyResponse() throws IOException, ParseException {
        logger.debug("testSpotACFrequencyResponse");
        String str = "7E5F002147EC292580003CFB0FDC1B0001007EFF0360651080534D4153424600A06300B4AE57770000000000007D3180010200510A0000000A000000015746003A854952847D330000847D330000847D330000847D3300000100000097897E";
        L2Packet l2Packet = L2Codec.readPacket(StreamUtils.toInputStream(str));
        SpotAcInfo spotAC = new SpotAcInfo(DataResponsePacket.parse(l2Packet).getElements());

        assertEquals(new BigDecimal(4996).divide(new BigDecimal(100)), spotAC.getFrequency());
    }

    public void testOperationRequest() throws IOException {
        logger.debug("testOperationRequest");
        checkL2Request("7E3E004000000000000047EC2925800001007EFF03606509A1FFFFFFFFFFFF0000534D41534246000000000000108000020051003F2600FF3F2600DCDA7E",
                       ByteUtils.BROADCAST_ADDRESS,
                       INVERTER_ADDRESS,
                       CLIENT_ADDRESS,
                       ByteUtils.UNKNOWN_ADDRESS,
                       L2Command.SPOT_AC_POWER);
    }

    public void testOperationResponse() throws IOException, ParseException {
        logger.debug("testOperationResponse");
        String str = "7E5F002147EC292580003CFB0FDC1B0001007EFF0360657D3180534D4153424600A06300B4AE57770000000000000B80010200540200000003000000012E46003A85495263FA960000000000012F46003A854952F43A8C000000000061067E";
        L2Packet l2Packet = L2Codec.readPacket(StreamUtils.toInputStream(str));
        OperationInfo operationInfo = new OperationInfo(DataResponsePacket.parse(l2Packet).getElements());

        assertEquals(new BigDecimal(2748472).divide(new BigDecimal(1000)), operationInfo.getOperationTime());
        assertEquals(new BigDecimal(2552815).divide(new BigDecimal(1000)), operationInfo.getFeedinTime());
    }
}
