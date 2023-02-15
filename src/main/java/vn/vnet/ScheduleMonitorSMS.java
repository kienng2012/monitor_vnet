package vn.vnet;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.vnet.util.ParameterStringBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Ref call api :https://github.com/eugenp/tutorials/tree/master/core-java-modules/core-java-networking-2
 */
@Component
@Log4j2
public class ScheduleMonitorSMS {
    @Value("${sms.smpp.user-id}")
    private String username;
    @Value("${sms.smpp.password}")
    private String password;
    @Value("${sms.smpp.sourceAddress}")
    private String sourceAddress;
    @Value("${sms.smpp.destAddress}")
    private String destAddress;

    @Value("${sms.smpp.urlApi}")
    private String urlApi;

    @Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "${sms.async.time-monitor}")
//    @Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "1000")
    public void fixedDelayForApiVNet() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date now = new Date();
        String strDate = sdf.format(now);
        String msg = Application.API_CHANNEL + strDate;
        log.debug("fixedDelayForApiVNet schedule:: " + strDate);
        Long beginTransTime = System.currentTimeMillis();
        String resultApi = null;
        try {
            resultApi = this.sendTextMessage(urlApi, username, password, sourceAddress.trim(), destAddress, msg);
        } catch (Exception e) {
            log.error("[Exception_API_VNet] Cannot call api VNet. Detail: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        Long longResultApi = null;
        if (resultApi != null) {

            try {
                longResultApi = Long.parseLong(resultApi.trim());
            } catch (NumberFormatException e) {
                log.error("[fixedDelayForApiVNet] Cannot parse result API, result API= {}", resultApi);
            }
            if (longResultApi != null && longResultApi > 0) {
                log.info("[fixedDelayForApiVNet] Call Api VNet success with MessageId={}, Message={}", longResultApi, msg);
            } else {
                log.error("[fixedDelayForApiVNet] Call Api VNet fail with MessageId={}, Message={}", longResultApi, msg);
            }
        } else {
            log.error("[Exception_API_VNet] Call api VNet return null");
        }
        log.info("[fixedDelayForApiVNet] Take along: {} (ms) MessageId={}, Message={}", (System.currentTimeMillis() - beginTransTime), longResultApi, msg);
    }

    @Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "${sms.async.time-monitor}")
//    @Scheduled(initialDelayString = "${sms.async.initial-delay}", fixedDelayString = "1000")
    public void monitorPingGWVnet() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        Date now = new Date();
        String strDate = sdf.format(now);
        String msg = Application.API_CHANNEL + strDate;
        log.debug("fixedDelayForApiVNet schedule:: " + strDate);
        Long beginTransTime = System.currentTimeMillis();
        String resultApi = null;
        checkTelnet("10.19.10.120",9696);
        log.info("[fixedDelayForApiVNet] Take along: {} (ms) , Message={}", (System.currentTimeMillis() - beginTransTime), msg);
    }

    protected String sendTextMessage(String urlLink, String username, String password, String sourceAddr, String destAddr, String msg) throws Exception {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("username", username);
        parameters.put("password", password);
        parameters.put("request_id", String.valueOf(System.currentTimeMillis()));
        parameters.put("source_addr", sourceAddr);
        parameters.put("dest_addr", destAddr);
        parameters.put("telco_code", "");
        parameters.put("type", "0");
        parameters.put("message", msg);
        String params = ParameterStringBuilder.getParamsString(parameters);
        String fullUrl = urlLink + params;
//        System.out.println(fullUrl);
        URL url = new URL(fullUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        if (con.getResponseCode() == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            log.debug(content);
            return content.toString();
        } else {
            log.error("[Exception_API_VNet] API return HTTP Code= {}", con.getResponseCode());
        }
        return null;
    }

    private void checkTelnet(String ip, int port) {
        Socket pingSocket = null;
        PrintWriter out = null;
        BufferedReader in = null;

        try {
            pingSocket = new Socket(ip, port);
            out = new PrintWriter(pingSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(pingSocket.getInputStream()));
        } catch (IOException e) {
            return;
        }

        out.println("ping");
        try {
            System.out.println(in.readLine());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        out.close();
        try {
            in.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            pingSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
