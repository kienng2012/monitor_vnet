package vn.vnet.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * @author Kienng
 * https://medium.com/@Seonggil/send-email-with-spring-boot-and-gmail-27c14fc3d859
 * https://www.geeksforgeeks.org/spring-boot-sending-email-via-smtp/
 */
@Log4j2
@Service(value = "MailService")
public class MailService {
    private Environment env;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    public MailService(Environment environment) {
        this.env = environment;
    }

    public void sendMail(String sentTo, String cc,String[] lstEmailCc, String title, String content) {
        // Try block to check for exceptions
        try {
            // Creating a simple mail message
            SimpleMailMessage mailMessage = new SimpleMailMessage();

            // Setting up necessary details
            mailMessage.setFrom(env.getProperty("spring.mail.username"));
            mailMessage.setTo(sentTo);
//            mailMessage.setCc(cc);
            mailMessage.setCc(lstEmailCc);
//            mailMessage.setBcc(bcc);
            mailMessage.setText(content);
            mailMessage.setSubject(title);

            // Sending the mail
            javaMailSender.send(mailMessage);
            log.info("Mail Sent Successfully...");
        }

        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("[sendMail] CANNOT SEND EMAIL. Detail ={}", e);
        }
    }
}
