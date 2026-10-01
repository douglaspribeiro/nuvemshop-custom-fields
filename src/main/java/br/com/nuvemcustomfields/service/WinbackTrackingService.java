package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

@Service
public class WinbackTrackingService {
    private final WinbackCampaignRepository campaigns;
    private final WinbackEmailRepository emails;
    private final WinbackEmailEventRepository events;
    private final StoreRepository stores;
    private final WinbackDiscountService discounts;
    private final WinbackCouponRepository coupons;
    public WinbackTrackingService(WinbackCampaignRepository campaigns, WinbackEmailRepository emails,
            WinbackEmailEventRepository events, StoreRepository stores, WinbackDiscountService discounts,
            WinbackCouponRepository coupons) {
        this.campaigns = campaigns; this.emails = emails; this.events = events; this.stores = stores;
        this.discounts = discounts; this.coupons = coupons;
    }

    /** Caller holds the store lock in the webhook transaction. */
    @Transactional
    public WinbackCampaign record(Store store) {
        return campaigns.findByStoreIdAndUninstalledAt(store.getStoreId(), store.getUninstalledAt())
                .orElseGet(() -> {
                    var campaign = new WinbackCampaign(store.getStoreId(), store.getUninstalledAt());
                    campaign.setPaidBeforeDeparture(discounts.hasPaidHistory(store.getStoreId()));
                    return campaigns.save(campaign);
                });
    }

    @Transactional
    public void reinstalled(Long storeId) {
        stores.findByStoreIdForUpdate(storeId).ifPresent(store ->
                campaigns.findByStoreIdAndReinstalledAtIsNull(storeId).forEach(c -> c.reinstalled(Instant.now())));
    }

    @Transactional(readOnly = true)
    public Page<WinbackCampaign> list(int page, String query, String stage, java.time.LocalDate from,
            java.time.LocalDate to) {
        if (!Set.of("", "reinstalled", "responded", "opted-out", "open", "click", "delivery-failed",
                "converted", "coupon-used", "paid-before", "feature-request").contains(stage))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro inválido.");
        if (from != null && to != null && from.isAfter(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período inválido.");
        var zone = java.time.ZoneId.of("America/Sao_Paulo");
        return campaigns.search(query.strip().toLowerCase(Locale.ROOT), stage,
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
                PageRequest.of(Math.max(0, page), 30));
    }

    @Transactional(readOnly = true)
    public Detail detail(String id) {
        var c = campaigns.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var mail = emails.findByCampaignIdOrderByCreatedAtAsc(id);
        var timeline = mail.isEmpty() ? List.<WinbackEmailEvent>of()
                : events.findByEmailIdInOrderByOccurredAtAsc(mail.stream().map(WinbackEmail::getId).toList());
        return new Detail(c, stores.findByStoreId(c.getStoreId()).orElse(null), mail, timeline,
                coupons.findByCampaignId(c.getId()).orElse(null));
    }

    public record Detail(WinbackCampaign campaign, Store store, List<WinbackEmail> emails,
                         List<WinbackEmailEvent> events, WinbackCoupon coupon) {
        public boolean has(String type) { return events.stream().anyMatch(e -> type.equals(e.getType())); }
    }
}
