package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import io.micrometer.core.instrument.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** Shared DB snapshots use max across replicas; byte counters are local and use sum of increases. */
@Component
public class BuyerUploadMetrics {
    private final JdbcTemplate jdbc;
    private final MeterRegistry registry;
    private volatile Map<String,Double> snapshot;
    private volatile Instant at;
    private static final List<String> STATES=List.of("RESERVED","TEMPORARY","CART","ORDERED","DELETING");
    public BuyerUploadMetrics(JdbcTemplate jdbc, MeterRegistry registry) {
        this.jdbc=jdbc;this.registry=registry;
        for(var provider:BuyerStorageProvider.values()) {
            String label=provider.name();
            Gauge.builder("ncf.buyer.storage.bytes",this,m->m.value(label+":bytes")).tag("provider",label).register(registry);
            for(String state:STATES) Gauge.builder("ncf.buyer.files",this,m->m.value(label+":"+state))
                    .tags("provider",label,"state",state).register(registry);
            for(String direction:List.of("upload","download")) {
                registry.counter("ncf.buyer.transfer.bytes","provider",label,"direction",direction);
                for(String outcome:List.of("success","failed","incomplete"))
                    registry.counter("ncf.buyer.transfers","provider",label,"direction",direction,"outcome",outcome);
            }
        }
        Gauge.builder("ncf.buyer.snapshot.timestamp.seconds",this,m->m.at==null?Double.NaN:m.at.getEpochSecond()).register(registry);
        registry.counter("ncf.buyer.metrics.failures");
    }
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString="${buyer-uploads.metrics-delay-ms:60000}", initialDelayString="${buyer-uploads.metrics-delay-ms:60000}")
    public synchronized void refresh() {
        try {
            var values=new HashMap<String,Double>();
            for(var p:BuyerStorageProvider.values()) {values.put(p.name()+":bytes",0d);for(String s:STATES)values.put(p.name()+":"+s,0d);}
            for(var row:jdbc.queryForList("select storage_provider,state,count(*) as file_count,sum(size_bytes) as size_total from buyer_uploads where state in ('RESERVED','TEMPORARY','CART','ORDERED','DELETING') group by storage_provider,state")) {
                String provider=BuyerStorageProvider.valueOf((String)row.get("storage_provider")).name();
                values.put(provider+":"+row.get("state"),((Number)row.get("file_count")).doubleValue());
                values.merge(provider+":bytes",((Number)row.get("size_total")).doubleValue(),Double::sum);
            }
            snapshot=Map.copyOf(values);at=Instant.now();
        } catch(RuntimeException e) {registry.counter("ncf.buyer.metrics.failures").increment();}
    }
    private double value(String key) {var values=snapshot;return values==null?Double.NaN:values.getOrDefault(key,Double.NaN);}
    private void outcome(BuyerStorageProvider provider,String direction,String result) {
        registry.counter("ncf.buyer.transfers","provider",provider.name(),"direction",direction,"outcome",result).increment();
    }
    private void bytes(BuyerStorageProvider provider,String direction,long size) {
        if(size>0)registry.counter("ncf.buyer.transfer.bytes","provider",provider.name(),"direction",direction).increment(size);
    }
    public void uploaded(BuyerStorageProvider provider,long size) {bytes(provider,"upload",size);outcome(provider,"upload","success");}
    public void uploadFailed(BuyerStorageProvider provider) {outcome(provider,"upload","failed");}
    public InputStream download(BuyerStorageProvider provider,long expected,InputStream source) {
        return new InputStream() {
            long read;boolean recorded;
            private void finish(String result){if(!recorded){recorded=true;outcome(provider,"download",result);}}
            private int counted(int n){if(n>0){read+=n;bytes(provider,"download",n);}else if(n<0)finish(read==expected?"success":"incomplete");return n;}
            @Override public int read() throws IOException {
                try {int n=source.read();counted(n<0?-1:1);return n;}catch(IOException e){finish("failed");throw e;}
            }
            @Override public int read(byte[] b,int offset,int len) throws IOException {
                try{return counted(source.read(b,offset,len));}catch(IOException e){finish("failed");throw e;}
            }
            @Override public void close() throws IOException {
                try{source.close();finish(read==expected?"success":"incomplete");}catch(IOException e){finish("failed");throw e;}
            }
        };
    }
    public void downloadFailed(BuyerStorageProvider provider){outcome(provider,"download","failed");}
}
