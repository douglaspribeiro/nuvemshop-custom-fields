package br.com.nuvemcustomfields.service;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectVersionsIterable;
import java.util.function.Consumer;
import static org.mockito.Mockito.*;

class S3OptionImageStorageTest {
    @Test void deletesExactKeyVersionsAndMarkersWithoutDeletingNeighbourOrCreatingMarker() {
        var client=mock(S3Client.class);
        var pages=mock(ListObjectVersionsIterable.class);
        var page=ListObjectVersionsResponse.builder()
            .versions(ObjectVersion.builder().key("key").versionId("v1").build(), ObjectVersion.builder().key("key-other").versionId("v2").build())
            .deleteMarkers(DeleteMarkerEntry.builder().key("key").versionId("marker").build()).build();
        when(client.listObjectVersionsPaginator(org.mockito.ArgumentMatchers.<Consumer<ListObjectVersionsRequest.Builder>>any())).thenReturn(pages);
        when(pages.iterator()).thenReturn(java.util.List.of(page).iterator());
        when(client.getBucketVersioning(org.mockito.ArgumentMatchers.<Consumer<GetBucketVersioningRequest.Builder>>any()))
            .thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.ENABLED).build());
        var storage=new S3OptionImageStorage("bucket","prefix",client,null);
        storage.delete("bucket","key");
        verify(client,times(2)).deleteObject(org.mockito.ArgumentMatchers.<Consumer<DeleteObjectRequest.Builder>>argThat(consumer -> {
            var request=DeleteObjectRequest.builder();consumer.accept(request);var value=request.build();
            return value.key().equals("key") && value.bucket().equals("bucket") && value.versionId()!=null;
        }));
    }
}
