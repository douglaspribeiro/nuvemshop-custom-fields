package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.config.LocalHomologationGuard;
import br.com.nuvemcustomfields.dto.*;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.List;

@Component
@Primary
@Profile("local-homolog & !prod & !production")
@ConditionalOnProperty(name="local.homologation.enabled",havingValue="true")
public class LocalNuvemshopApiClient extends NuvemshopApiClient {
    public LocalNuvemshopApiClient(NuvemshopProperties p,RestClient.Builder b,LocalHomologationGuard guard){super(p,b);}
    private void check(Store store){if(store==null || !Long.valueOf(LocalHomologationGuard.STORE_ID).equals(store.getStoreId()) || !LocalHomologationGuard.STORE_TOKEN.equals(store.getAccessToken()))throw new IllegalArgumentException("A API simulada aceita somente a loja fictícia local.");}
    @Override public StoreProfile getStoreProfile(Store store){check(store);return new StoreProfile(store.getStoreName(),"BR","BRL",store.getStoreEmail());}
    @Override public ProductPage listProducts(Store store,int page,int perPage,String query){
        check(store);int p=Math.max(1,page),size=Math.clamp(perPage,1,200);
        var products=List.of(new ProductSummary(900001L,"Caneca de teste"),new ProductSummary(900002L,"Camiseta de teste"),new ProductSummary(900003L,"Presente de teste"))
                .stream().filter(item->query==null || query.isBlank() || item.name().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).toList();
        return new ProductPage(products.stream().skip((long)(p-1)*size).limit(size).toList(),p,size,products.size(),(long)p*size<products.size(),query);
    }
    private JsonNode empty(Store s){check(s);return JsonNodeFactory.instance.arrayNode();}
    @Override public JsonNode listWebhooks(Store s){return empty(s);}
    @Override public JsonNode listScripts(Store s){return empty(s);}
    @Override public JsonNode listRecentOrders(Store s){return empty(s);}
    @Override public JsonNode listOrdersForSales(Store s,int p,int size,Instant c1,Instant c2,Instant u1,Instant u2){return empty(s);}
    @Override public void createWebhook(Store s,String event,String url){check(s);}
    @Override public void createScript(Store s,Long id){check(s);}
    @Override public void deleteScript(Store s,Long id){check(s);}
}
