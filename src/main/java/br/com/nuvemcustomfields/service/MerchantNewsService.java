package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class MerchantNewsService {
    public record Item(String id, String messageKey, String href) {}
    private final BuyerUploadService uploads;
    public MerchantNewsService(BuyerUploadService uploads) {
        this.uploads = uploads;
    }
    public List<Item> forStore(Store store) {
        var news = new ArrayList<Item>();
        news.add(new Item("named-colors-v1", "merchant.news.colors", "/admin/products"));
        news.add(new Item("templates-batch-v1", "merchant.news.templateBatch", "/admin/templates"));
        news.add(new Item("templates-v1", "merchant.news.templates", "/admin/templates"));
        if (uploads.enabled(store.getStoreId())) news.add(new Item("buyer-uploads-v2", "merchant.news.uploads", "/admin/products"));
        return List.copyOf(news);
    }
}
