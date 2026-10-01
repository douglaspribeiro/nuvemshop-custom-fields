package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.properties.SesProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Transporte e remetente SES compartilhados pelo suporte e pela reconquista. */
@Configuration
public class SesMailConfig {
    @Bean
    public JavaMailSender sesMailSender(SesProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        sender.setDefaultEncoding("UTF-8");
        var smtp = sender.getJavaMailProperties();
        smtp.setProperty("mail.smtp.auth", "true");
        smtp.setProperty("mail.smtp.starttls.enable", "true");
        smtp.setProperty("mail.smtp.starttls.required", "true");
        smtp.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        smtp.setProperty("mail.smtp.connectiontimeout", "5000");
        smtp.setProperty("mail.smtp.timeout", "10000");
        smtp.setProperty("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
