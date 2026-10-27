package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.properties.SesProperties;
import br.com.nuvemcustomfields.repository.StoreRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class StoreAdminEmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreAdminEmailService.class);
    private final StoreRepository stores;
    private final SesProperties properties;
    private final JavaMailSender sender;

    public StoreAdminEmailService(StoreRepository stores, SesProperties properties, JavaMailSender sender) {
        this.stores = stores;
        this.properties = properties;
        this.sender = sender;
    }

    public void send(Long storeId, String subject, String body) {
        var store = stores.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Loja não encontrada."));
        String recipient = store.getStoreEmail();
        try {
            if (recipient == null || recipient.isBlank() || recipient.contains("\r") || recipient.contains("\n"))
                throw new AddressException();
            var address = new InternetAddress(recipient, true);
            address.validate();
            if (!recipient.equals(address.getAddress())) throw new AddressException();
        } catch (AddressException ex) {
            throw new IllegalArgumentException("A loja não possui um e-mail válido cadastrado.");
        }
        if (subject == null || subject.isBlank() || subject.length() > 200
                || subject.contains("\r") || subject.contains("\n"))
            throw new IllegalArgumentException("Informe um assunto de até 200 caracteres, em uma única linha.");
        if (body == null || body.isBlank() || body.length() > 10000)
            throw new IllegalArgumentException("Informe uma mensagem de até 10.000 caracteres.");
        if (!properties.configured())
            throw new IllegalArgumentException("O envio de e-mail não está configurado neste ambiente.");
        var mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(recipient);
        mail.setSubject(subject.strip());
        mail.setText(body.strip());
        try {
            sender.send(mail);
        } catch (RuntimeException ex) {
            LOGGER.warn("store.admin_email.failed store_id={} type={}", storeId, ex.getClass().getSimpleName());
            throw new IllegalStateException("Não foi possível confirmar o envio. Confira o provedor de e-mail antes de tentar novamente.");
        }
        LOGGER.info("store.admin_email.sent store_id={}", storeId);
    }
}
