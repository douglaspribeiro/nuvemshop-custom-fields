package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class BuyerFileStorageRouterTest {
    final S3BuyerFileStorage aws = mock(S3BuyerFileStorage.class);
    final OciBuyerFileStorage oci = mock(OciBuyerFileStorage.class);
    final StoreRepository stores = mock(StoreRepository.class);
    @Test void storeOverrideWinsAndResetFollowsCurrentDefaultWithoutAffectingFileRouting() {
        var store = new Store(); store.setStoreId(1L);
        when(stores.findByStoreId(1L)).thenReturn(Optional.of(store));
        var router = new BuyerFileStorageRouter(BuyerStorageProvider.AWS_S3, aws, oci, stores);
        assertThat(router.forStore(1)).isSameAs(aws);
        store.setBuyerUploadStorageProvider(BuyerStorageProvider.OCI_OBJECT_STORAGE);
        assertThat(router.forStore(1)).isSameAs(oci);
        assertThat(router.forProvider(BuyerStorageProvider.AWS_S3)).isSameAs(aws);
        store.setBuyerUploadStorageProvider(null);
        assertThat(router.forStore(1)).isSameAs(aws);
        var switched = new BuyerFileStorageRouter(BuyerStorageProvider.OCI_OBJECT_STORAGE, aws, oci, stores);
        assertThat(switched.forStore(1)).isSameAs(oci);
        assertThat(switched.forProvider(BuyerStorageProvider.AWS_S3)).isSameAs(aws);
        assertThatThrownBy(() -> switched.forProvider(null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void unavailableSelectedBackendDoesNotSilentlyFallBack() {
        var store = new Store(); store.setBuyerUploadStorageProvider(BuyerStorageProvider.OCI_OBJECT_STORAGE);
        when(stores.findByStoreId(1L)).thenReturn(Optional.of(store));
        when(aws.enabled()).thenReturn(true);
        var router = new BuyerFileStorageRouter(BuyerStorageProvider.AWS_S3, aws, oci, stores);
        assertThat(router.forStore(1).enabled()).isFalse();
        assertThat(router.forProvider(BuyerStorageProvider.AWS_S3).enabled()).isTrue();
    }
}
