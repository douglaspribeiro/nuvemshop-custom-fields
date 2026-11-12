package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MerchantNewsServiceTest {
    final BuyerUploadService uploads=mock(BuyerUploadService.class);
    Store store(PlanType plan) {var s=new Store();s.setStoreId(42L);s.setPlan(plan);return s;}
    @ParameterizedTest @EnumSource(PlanType.class)
    void everyPlanSeesTheSameAvailableFeaturesAndDirectLinks(PlanType plan) {
        when(uploads.enabled(anyLong())).thenReturn(true);
        var items=new MerchantNewsService(uploads).forStore(store(plan));
        assertThat(items).extracting(MerchantNewsService.Item::href).containsExactly("/admin/templates","/admin/templates","/admin/products");
    }
    @Test void uploadAnnouncementWaitsForConfiguredStorage() {
        var items=new MerchantNewsService(uploads).forStore(store(PlanType.FREE));
        assertThat(items).extracting(MerchantNewsService.Item::id).containsExactly("templates-batch-v1","templates-v1");
    }
}
