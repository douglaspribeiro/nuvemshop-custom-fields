package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Single-instance batch: one store per tick; snapshots contain no access tokens. */
@Service
public class GlobalScriptInstallService {
    private final StoreRepository stores;
    private final ScriptInstallService scripts;
    private List<Long> pending = List.of();
    private final List<Result> results = new ArrayList<>();
    private boolean running;
    private int next;
    private Instant startedAt;

    public GlobalScriptInstallService(StoreRepository stores, ScriptInstallService scripts) {
        this.stores = stores;
        this.scripts = scripts;
    }

    public synchronized boolean start() {
        if (running) return false;
        pending = stores.findAllActive().stream()
                .filter(s -> s.getAccessToken() != null && !s.getAccessToken().isBlank())
                .map(s -> s.getStoreId()).toList();
        results.clear();
        next = 0;
        startedAt = Instant.now();
        running = !pending.isEmpty();
        return true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(running, pending.size(), results.size(), startedAt, List.copyOf(results));
    }

    @Scheduled(fixedDelayString = "${scripts.global.delay-ms:1000}")
    public void processNext() {
        Long id;
        synchronized (this) {
            if (!running || next >= pending.size()) return;
            id = pending.get(next++);
        }
        Result result;
        try {
            var store = stores.findActiveByStoreId(id).orElse(null);
            if (store == null || store.getAccessToken() == null || store.getAccessToken().isBlank()) {
                result = new Result(id, "Ignorada", "Loja desinstalada ou sem token.");
            } else {
                var before = scripts.diagnose(store);
                if (before.error() != null) {
                    result = new Result(id, "Falha", before.error());
                } else {
                    if (!before.healthy()) scripts.installPersonalizerScript(store);
                    var after = scripts.diagnose(store);
                    result = new Result(id, after.healthy()
                            ? (before.healthy() ? "Já associada" : "Corrigida") : "Falha",
                            after.error() != null ? after.error() : after.healthy()
                                    ? "Todos os scripts configurados estão associados."
                                    : "Scripts ausentes: " + after.missingIds());
                }
            }
        } catch (RuntimeException ex) {
            result = new Result(id, "Falha", "Não foi possível concluir. Consulte os logs da loja.");
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("script.global.failed store_id={}", id, ex);
        }
        synchronized (this) {
            results.add(result);
            if (results.size() == pending.size()) running = false;
        }
    }

    public record Result(Long storeId, String status, String detail) {}
    public record Snapshot(boolean running, int total, int processed, Instant startedAt, List<Result> results) {}
}
