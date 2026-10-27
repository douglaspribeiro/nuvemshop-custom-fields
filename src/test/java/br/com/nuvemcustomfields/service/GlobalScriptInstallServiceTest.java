package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ScriptDiagnostics;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalScriptInstallServiceTest {
    private Store store(long id) {
        Store s = new Store(); s.setStoreId(id); s.setAccessToken("test"); return s;
    }
    @Test void verifiesAfterInstallAndContinuesAfterFailure() {
        var repo = mock(StoreRepository.class);
        var scripts = mock(ScriptInstallService.class);
        var first = store(1); var second = store(2);
        when(repo.findAllActive()).thenReturn(List.of(first, second));
        when(repo.findActiveByStoreId(1L)).thenReturn(java.util.Optional.of(first));
        when(repo.findActiveByStoreId(2L)).thenReturn(java.util.Optional.of(second));
        var missing = new ScriptDiagnostics(List.of(11470L), List.of(), List.of(11470L), null);
        var healthy = new ScriptDiagnostics(List.of(11470L), List.of(), List.of(), null);
        when(scripts.diagnose(first)).thenReturn(missing);
        when(scripts.diagnose(second)).thenReturn(missing, healthy);
        var batch = new GlobalScriptInstallService(repo, scripts);
        assertTrue(batch.start()); assertFalse(batch.start());
        batch.processNext(); batch.processNext();
        assertFalse(batch.snapshot().running());
        assertEquals(List.of("Falha", "Corrigida"), batch.snapshot().results().stream().map(r -> r.status()).toList());
        verify(scripts).installPersonalizerScript(first);
        verify(scripts).installPersonalizerScript(second);
    }
    @Test void skipsRemovedStoresAndDoesNotReinstallHealthyStores() {
        var repo = mock(StoreRepository.class); var scripts = mock(ScriptInstallService.class);
        var first = store(1); var second = store(2); var noToken = new Store();
        when(repo.findAllActive()).thenReturn(List.of(first, second, noToken));
        when(repo.findActiveByStoreId(1L)).thenReturn(java.util.Optional.empty());
        when(repo.findActiveByStoreId(2L)).thenReturn(java.util.Optional.of(second));
        when(scripts.diagnose(second)).thenReturn(new ScriptDiagnostics(List.of(11470L), List.of(), List.of(), null));
        var batch = new GlobalScriptInstallService(repo, scripts);
        batch.start(); batch.processNext(); batch.processNext();
        assertEquals(2, batch.snapshot().total());
        assertEquals(List.of("Ignorada", "Já associada"), batch.snapshot().results().stream().map(r -> r.status()).toList());
        verify(scripts, never()).installPersonalizerScript(any());
    }
}
