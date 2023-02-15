package vn.vnet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.SmppClient;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppBindException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;

/**
 * https://juliuskrah.com/blog/2018/12/28/building-an-smpp-application-using-spring-boot/
 * https://github.com/juliuskrah/smpp/blob/master/README.md
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(ApplicationProperties.class)
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);
    public static final String SMPP_CHANNEL = "smpp|";
    public static final String API_CHANNEL = "api|";
    @Autowired
    private SmppSession session;
    @Autowired
    private ApplicationProperties properties;

    @Value("${sms.smpp.sourceAddress}")
    private String sourceAddress;
    @Value("${sms.smpp.destAddress}")
    private String destAddress;


    public static void main(String[] args) {
        ConfigurableApplicationContext ctx = SpringApplication.run(Application.class, args);
//		SmppSession session = ctx.getBean(SmppSession.class);
//		new Application().sendTextMessage(session, "VNet_Monitor", "Hello World", "84967891610");
    }

    public SmppSessionConfiguration sessionConfiguration(ApplicationProperties properties) {
        SmppSessionConfiguration sessionConfig = new SmppSessionConfiguration();
        sessionConfig.setName("smpp.session");
        sessionConfig.setInterfaceVersion(SmppConstants.VERSION_3_4);
        sessionConfig.setType(SmppBindType.TRANSCEIVER);
        sessionConfig.setHost(properties.getSmpp().getHost());
        sessionConfig.setPort(properties.getSmpp().getPort());
        sessionConfig.setSystemId(properties.getSmpp().getUserId());
        sessionConfig.setPassword(properties.getSmpp().getPassword());
        sessionConfig.setSystemType(null);
        sessionConfig.getLoggingOptions().setLogBytes(false);
        sessionConfig.getLoggingOptions().setLogPdu(true);

        return sessionConfig;
    }

    @Bean(destroyMethod = "destroy")
    public SmppSession session(ApplicationProperties properties) throws SmppBindException, SmppTimeoutException,
            SmppChannelException, UnrecoverablePduException, InterruptedException {
        SmppSessionConfiguration config = sessionConfiguration(properties);
        SmppSession session = clientBootstrap(properties).bind(config, new ClientSmppSessionHandler(properties));

        return session;
    }

    public SmppClient clientBootstrap(ApplicationProperties properties) {
        return new DefaultSmppClient(Executors.newCachedThreadPool(), properties.getAsync().getSmppSessionSize());
    }

    private void sendTextMessage(SmppSession session, String sourceAddress, String message, String destinationAddress) {
        if (session.isBound()) {
            try {
                boolean requestDlr = true;
                SubmitSm submit = new SubmitSm();
                byte[] textBytes;
                textBytes = CharsetUtil.encode(message, CharsetUtil.CHARSET_ISO_8859_1);
                submit.setDataCoding(SmppConstants.DATA_CODING_LATIN1);
                if (requestDlr) {
                    submit.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);
                }

                if (textBytes != null && textBytes.length > 255) {
                    submit.addOptionalParameter(
                            new Tlv(SmppConstants.TAG_MESSAGE_PAYLOAD, textBytes, "message_payload"));
                } else {
                    submit.setShortMessage(textBytes);
                }

                submit.setSourceAddress(new Address((byte) 0x05, (byte) 0x01, sourceAddress));
                submit.setDestAddress(new Address((byte) 0x01, (byte) 0x01, destinationAddress));
                SubmitSmResp submitResponse = session.submit(submit, 10000);
                if (submitResponse.getCommandStatus() == SmppConstants.STATUS_OK) {
                    log.info("SMS submitted, message id {}", submitResponse.getMessageId());
                } else {
                    throw new IllegalStateException(submitResponse.getResultMessage());
                }
            } catch (RecoverablePduException | UnrecoverablePduException | SmppTimeoutException | SmppChannelException
                     | InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return;
        }

        throw new IllegalStateException("SMPP session is not connected");
    }

    @Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "${sms.async.initial-delay}")
    void enquireLinkJob() {
        if (session.isBound()) {
            try {
                log.info("sending enquire_link");
                EnquireLinkResp enquireLinkResp = session.enquireLink(new EnquireLink(),
                        properties.getAsync().getTimeout());
                log.info("enquire_link_resp: {}", enquireLinkResp);
            } catch (SmppTimeoutException e) {
                log.info("Enquire link failed, executing reconnect; " + e);
                log.error("CANNOT_BOUND_SMPP ==> Restart now", e);
                //TODO: Kienng restart project using script *.sh
            } catch (SmppChannelException e) {
                log.info("Enquire link failed, executing reconnect; " + e);
                log.warn("", e);
            } catch (InterruptedException e) {
                log.info("Enquire link interrupted, probably killed by reconnecting");
            } catch (Exception e) {
                log.error("Enquire link failed, executing reconnect", e);
            }
        } else {
            log.error("enquire link running while session is not connected");
        }
    }

    @Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "${sms.async.time-monitor}")
    public void fixedDelayForSMPPVNet() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date now = new Date();
        String strDate = sdf.format(now);
        String msg = SMPP_CHANNEL+strDate;
        this.sendTextMessage(session, sourceAddress, msg, destAddress);
        System.out.println("Fixed Delay scheduler:: " + strDate);
    }
}
