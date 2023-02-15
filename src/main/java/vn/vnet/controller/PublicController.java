package vn.vnet.controller;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import vn.vnet.dto.MonitorVNetDto;
import vn.vnet.dto.PingDto;
import vn.vnet.dto.StatusDto;
import vn.vnet.service.MailService;

import javax.validation.Valid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@Log4j2
public class PublicController {

    private static final String EMAIL_TITLE = "ALERT DELAY SMS VNET MONITOR";
    @Value("${sms.smpp.maxTimeToDelayConfig}")
    private Long maxTimeToDelayConfig;

    @Value("${sms.smpp.allowSendAlert}")
    private Boolean allowSendAlert;

    @Value("${sms.smpp.emailTo}")
    private String emailTo;

    @Value("${sms.smpp.emailCc}")
    private String emailCc;

    @Value("${sms.smpp.emailsCc}")
    private String[] emailsCc;

    @Value("${sms.smpp.userForApiCallback}")
    private String userForApiCallback;

    @Value("${sms.smpp.passwordForApiCallback}")
    private String passwordForApiCallback;

    @Autowired
    private MailService mailService;

    @PostMapping("/ping")
    public ResponseEntity<?> ping(
            @Valid() @RequestBody PingDto dto, Errors errors) {
        try {
            return new ResponseEntity<>(Optional.ofNullable(new StatusDto(1)), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(Optional.ofNullable(new StatusDto(-99)), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/monitor_vn/receiveNotify")
    public ResponseEntity<?> receiveMonitorCallback(@Valid() @RequestBody MonitorVNetDto dto) {
        log.info(dto.toString());
        try {
            if (!this.validateUser(dto.getUsername(), dto.getPassword()))
                return new ResponseEntity<>(Optional.ofNullable(new StatusDto(-1)), HttpStatus.UNAUTHORIZED);
            this.monitorSpeedSms(dto.getMessage(), dto.getRequestTime(), dto.getMessageId());

            return new ResponseEntity<>(Optional.ofNullable(new StatusDto(1)), HttpStatus.OK);
        } catch (Exception e) {
            log.error("[receiveMonitorCallback] BAD_REQUEST. Exception={}", e);
            return new ResponseEntity<>(Optional.ofNullable(new StatusDto(-99)), HttpStatus.BAD_REQUEST);

        }
    }

    private void monitorSpeedSms(String msg, long timeVnetCallToMoniorSytem, long messageId) {
        Timestamp timeVnetCallToMonitor = new Timestamp(timeVnetCallToMoniorSytem);
        log.info("timeVnetCallToMonitor= {}. Message= {}", timeVnetCallToMonitor, msg);
        long currentTime = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date now = new Date();
        String strNow = sdf.format(now);
        if (msg.contains("|")) {
            String[] lstMsg = msg.split("\\|");
            String channel = lstMsg[0].toUpperCase().trim();
            String strTimeRequest = lstMsg[1].trim();
            Timestamp timeBeginRequest = this.convertStringToTime(strTimeRequest);
            long totalDelayTime = currentTime - timeBeginRequest.getTime(); //(ms)

            if (totalDelayTime > maxTimeToDelayConfig) {
                String msgAlert = String.format("[monitorSpeedSms] EXCEED_QUOTA_DELAY_TIME_SMS. Channel=%1$s, MessageId=%2$d => Send Alert Now .totalDelayTime=%3$d > maxTimeToDelayConfig=%4$d (ms) \n Full Msg=%5$s |TimeResponse=%6$s", channel, messageId, totalDelayTime, maxTimeToDelayConfig, msg, strNow);
//                log.debug("[monitorSpeedSms] EXCEED_QUOTA_DELAY_TIME_SMS. Channel={}, MessageId={} => Send Alert Now .totalDelayTime={} > maxTimeToDelayConfig={} (ms)", channel, messageId, totalDelayTime, maxTimeToDelayConfig);
                log.warn(msgAlert);
                if (allowSendAlert) {
                    //TODO: Send ALERT Email
                    mailService.sendMail(emailTo, emailCc, emailsCc, EMAIL_TITLE, msgAlert);
                }
            } else {
                log.info("[monitorSpeedSms] OK. Channel={}, MessageId={} => Do not Send Alert .totalDelayTime={} < maxTimeToDelayConfig={} (ms)", channel, messageId, totalDelayTime, maxTimeToDelayConfig);
            }
        } else {
            log.debug("[monitorSpeedSms] INVALID_MSG. MessageId={}, Msg={}", messageId, msg);
        }
    }

    private boolean validateUser(String username, String password) {
        if (username != null && password != null) {
            if (username.isEmpty() || password.isEmpty())
                return false;
            else if (username.equals(userForApiCallback) && password.equals(passwordForApiCallback))
                return true;
            else
                return false;

        } else
            return false;
    }

    private Timestamp convertStringToTime(String strTime) {
        Timestamp timeRequest = null;
        DateTimeFormatter formatDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            LocalDateTime localDateTime = LocalDateTime.from(formatDateTime.parse(strTime));
            timeRequest = Timestamp.valueOf(localDateTime);
        } catch (Exception ex) {
            log.error("Time request is not valid format yyyy-MM-dd HH:mm:ss.SSS. Time request={}", strTime);
            timeRequest = new Timestamp(System.currentTimeMillis());
        }
        return timeRequest;
    }


}