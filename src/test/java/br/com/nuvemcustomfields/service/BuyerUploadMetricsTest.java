package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.BuyerStorageProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.io.*;
import static org.assertj.core.api.Assertions.*;

class BuyerUploadMetricsTest {
    JdbcTemplate db(){return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));}
    @Test void snapshotCountsSharedStorageByProviderIncludingPendingDeletion() {
        var db=db();db.execute("create table buyer_uploads(storage_provider varchar(30),state varchar(20),size_bytes bigint)");
        db.update("insert into buyer_uploads values ('AWS_S3','ORDERED',10),('OCI_OBJECT_STORAGE','DELETING',20),('AWS_S3','DELETED',50)");
        var registry=new SimpleMeterRegistry();var metrics=new BuyerUploadMetrics(db,registry);
        assertThat(registry.get("ncf.buyer.storage.bytes").tag("provider","AWS_S3").gauge().value()).isNaN();
        metrics.refresh();
        assertThat(registry.get("ncf.buyer.storage.bytes").tag("provider","AWS_S3").gauge().value()).isEqualTo(10);
        assertThat(registry.get("ncf.buyer.storage.bytes").tag("provider","OCI_OBJECT_STORAGE").gauge().value()).isEqualTo(20);
        db.execute("drop table buyer_uploads");metrics.refresh();
        assertThat(registry.get("ncf.buyer.metrics.failures").counter().count()).isEqualTo(1);
    }
    @Test void trafficCountsBytesActuallyReadAndSeparatesCompletedFromPartialDownloads() throws Exception {
        var registry=new SimpleMeterRegistry();var metrics=new BuyerUploadMetrics(db(),registry);
        try(var stream=metrics.download(BuyerStorageProvider.AWS_S3,10,new ByteArrayInputStream(new byte[10]))) {assertThat(stream.readNBytes(3)).hasSize(3);}
        try(var stream=metrics.download(BuyerStorageProvider.AWS_S3,10,new ByteArrayInputStream(new byte[10]))) {assertThat(stream.readAllBytes()).hasSize(10);}
        assertThat(registry.get("ncf.buyer.transfer.bytes").tags("provider","AWS_S3","direction","download").counter().count()).isEqualTo(13);
        assertThat(registry.get("ncf.buyer.transfers").tags("provider","AWS_S3","direction","download","outcome","success").counter().count()).isEqualTo(1);
        assertThat(registry.get("ncf.buyer.transfers").tags("provider","AWS_S3","direction","download","outcome","incomplete").counter().count()).isEqualTo(1);
    }
    @Test void failedReadIsCountedOnceEvenWhenTheStreamIsClosed() throws Exception {
        var registry=new SimpleMeterRegistry();var metrics=new BuyerUploadMetrics(db(),registry);
        try(var stream=metrics.download(BuyerStorageProvider.OCI_OBJECT_STORAGE,10,new InputStream(){public int read()throws IOException{throw new IOException("test");}})) {
            assertThatThrownBy(stream::read).isInstanceOf(IOException.class);
        }
        assertThat(registry.get("ncf.buyer.transfers").tags("provider","OCI_OBJECT_STORAGE","direction","download","outcome","failed").counter().count()).isEqualTo(1);
    }
}
