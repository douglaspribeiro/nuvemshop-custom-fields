package br.com.nuvemcustomfields.service;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.*;
import com.oracle.bmc.objectstorage.requests.*;
import com.oracle.bmc.objectstorage.responses.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class OciBuyerFileStorageTest {
    final ObjectStorage client=mock(ObjectStorage.class);
    final OciBuyerFileStorage storage=new OciBuyerFileStorage("bucket","namespace","files",client);
    @Test void publicBucketIsRejectedBeforeWriting() {
        when(client.getBucket(any(GetBucketRequest.class))).thenReturn(GetBucketResponse.builder()
                .bucket(Bucket.builder().publicAccessType(Bucket.PublicAccessType.ObjectRead).build()).build());
        assertThatThrownBy(() -> storage.put("bucket","files/1/id",new byte[]{1},"image/png"))
                .isInstanceOf(IllegalStateException.class);
        verify(client,never()).putObject(any(PutObjectRequest.class));
    }
    @Test void privateBucketUsesTheRecordedLocationAndContentType() {
        when(client.getBucket(any(GetBucketRequest.class))).thenReturn(GetBucketResponse.builder()
                .bucket(Bucket.builder().publicAccessType(Bucket.PublicAccessType.NoPublicAccess).build()).build());
        storage.put("recorded-bucket","files/1/id",new byte[]{1},"image/png");
        verify(client).putObject(argThat(r -> r.getBucketName().equals("recorded-bucket")
                && r.getNamespaceName().equals("namespace") && r.getObjectName().equals("files/1/id")
                && r.getContentType().equals("image/png") && r.getContentLength()==1));
    }
    @Test void removesExactVersionsAndDeleteMarkersAcrossPagesWithoutCreatingNewMarker() {
        when(client.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(
                ListObjectVersionsResponse.builder().opcNextPage("page-2").objectVersionCollection(ObjectVersionCollection.builder()
                        .items(List.of(ObjectVersionSummary.builder().name("key").versionId("v1").build(),
                                ObjectVersionSummary.builder().name("key-other").versionId("other").build())).build()).build(),
                ListObjectVersionsResponse.builder().objectVersionCollection(ObjectVersionCollection.builder()
                        .items(List.of(ObjectVersionSummary.builder().name("key").versionId("marker").isDeleteMarker(true).build())).build()).build());
        storage.delete("old-bucket","key");
        verify(client).listObjectVersions(argThat(r -> "page-2".equals(r.getPage())));
        verify(client,times(2)).deleteObject(argThat(r -> r.getBucketName().equals("old-bucket")
                && r.getObjectName().equals("key") && List.of("v1","marker").contains(r.getVersionId())));
        verify(client,never()).deleteObject(argThat(r -> r.getVersionId()==null));
    }
}
