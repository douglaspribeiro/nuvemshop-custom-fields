package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.PlanType;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

/** Fila de revisão manual: receber redact não apaga nem autoriza contatos. */
@Service
public class StoreErasureRequestService {
    private final StoreRepository stores;
    private final StoreDepartureService departures;
    private final PaymentSubscriptionService payments;
    private final StoreDataErasureService erasure;
    public StoreErasureRequestService(StoreRepository stores, StoreDepartureService departures,
            PaymentSubscriptionService payments, StoreDataErasureService erasure) {
        this.stores = stores; this.departures = departures; this.payments = payments; this.erasure = erasure;
    }
    @Transactional
    public void receive(Long storeId, Instant receivedAt) {
        if (storeId == null || receivedAt == null) throw new IllegalArgumentException("Evento incompleto.");
        stores.findByStoreIdForUpdate(storeId).ifPresent(store -> {
            departures.record(storeId, true);
            if (!store.isErasurePending()) store.setErasureRequestedAt(receivedAt);
            if (store.getUninstalledAt() == null) store.setUninstalledAt(receivedAt);
            store.setAccessToken(null);
            store.setScope(null);
            store.setPlan(PlanType.FREE);
            store.setBillingSuspended(true);
            payments.revokeAccessAfterUninstall(storeId);
            stores.saveAndFlush(store);
        });
    }
    @Transactional
    public void recordReason(Long storeId, String reason, String justification) {
        var store = stores.findByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (store.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "A loja está ativa.");
        String value = reason == null ? "" : reason.strip();
        String detail = justification == null ? "" : justification.strip();
        if (value.isBlank() || value.length() > 160 || detail.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe motivo até 160 caracteres e justificativa até 2.000.");
        store.setDepartureReason(value);
        store.setDepartureJustification(detail.isBlank() ? null : detail);
        stores.save(store);
    }
    @Transactional
    public void eraseManually(Long storeId) {
        var store = stores.findByStoreIdForUpdate(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (store.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "A loja está ativa. A exclusão manual só é permitida para lojas desinstaladas ou pendentes.");
        erasure.erase(storeId);
    }
}
