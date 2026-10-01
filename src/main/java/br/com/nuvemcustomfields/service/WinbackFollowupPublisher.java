package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.WinbackEmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class WinbackFollowupPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(WinbackFollowupPublisher.class);
    private final WinbackEmailRepository emails;
    private final WinbackCampaignService campaigns;
    private final boolean enabled;
    public WinbackFollowupPublisher(WinbackEmailRepository emails, WinbackCampaignService campaigns,
            @Value("${winback.mail-enabled:false}") boolean enabled) {
        this.emails = emails; this.campaigns = campaigns; this.enabled = enabled;
    }
    @Scheduled(fixedDelayString = "${winback.followup-delay-ms:10000}")
    public void sendDue() {
        if (!enabled) return;
        for (var mail : emails.findByStatusOrderByCreatedAtAsc("PREPARED", PageRequest.of(0, 10))) {
            try {
                String id = campaigns.prepareFollowup(mail.getId());
                if (id != null) campaigns.send(id);
            } catch (Exception ex) {
                LOGGER.warn("winback.followup.failed type={}", ex.getClass().getSimpleName());
            }
        }
    }
}
