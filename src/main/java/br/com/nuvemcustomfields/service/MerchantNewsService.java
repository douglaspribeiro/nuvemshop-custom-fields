package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.HomologProperties;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class MerchantNewsService {
    public record Item(String id, String messageKey, String href) {}
    private final HomologProperties homolog;
    private final BuyerUploadService uploads;
    public MerchantNewsService(HomologProperties homolog, BuyerUploadService uploads) {
        this.homolog = homolog; this.uploads = uploads;
    }
    public List<Item> forStore(Store store) {
        if (!homolog.allows(store.getStoreId())) return List.of();
        var news = new ArrayList<Item>();
        news.add(new Item("templates-v1", "merchant.news.templates", "/admin/templates"));
        if (uploads.enabled(store.getStoreId())) news.add(new Item("buyer-uploads-v2", "merchant.news.uploads", "/admin/products"));
        return List.copyOf(news);
    }
}
