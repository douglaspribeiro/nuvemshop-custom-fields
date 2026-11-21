package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.SupportAttachment;
import br.com.nuvemcustomfields.service.SupportReplyEvent;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class SupportAttachmentWeb {
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_EMAIL_ATTACHMENT_BYTES = 6L * 1024 * 1024;
    private static final int MAX_FILENAME_CHARS = 240;

    private SupportAttachmentWeb() {
    }

    static List<SupportReplyEvent.Attachment> merchantAttachment(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty()) {
            return List.of();
        }
        if (upload.getSize() > MAX_FILE_BYTES) {
            throw new AttachmentTooLarge();
        }
        return List.of(new SupportReplyEvent.Attachment(safeFilename(upload.getOriginalFilename()),
                MediaType.APPLICATION_OCTET_STREAM_VALUE, upload.getBytes()));
    }

    static List<SupportReplyEvent.Attachment> supportImages(List<MultipartFile> uploads) throws IOException {
        if (uploads == null) {
            return List.of();
        }
        var attachments = new ArrayList<SupportReplyEvent.Attachment>();
        long totalBytes = 0;
        for (var upload : uploads) {
            if (upload.isEmpty()) {
                continue;
            }
            totalBytes += upload.getSize();
            if (upload.getSize() > MAX_FILE_BYTES || totalBytes > MAX_EMAIL_ATTACHMENT_BYTES) {
                throw new IllegalArgumentException("Anexe até 5 MB por imagem e 6 MB por resposta.");
            }
            byte[] bytes = upload.getBytes();
            String contentType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(bytes));
            String extension;
            if ("image/png".equals(contentType)) {
                extension = ".png";
            } else if ("image/jpeg".equals(contentType)) {
                extension = ".jpg";
            } else {
                throw new IllegalArgumentException("Anexe apenas imagens PNG ou JPEG.");
            }
            String filename = "imagem-" + (attachments.size() + 1) + extension;
            attachments.add(new SupportReplyEvent.Attachment(filename, contentType, bytes));
        }
        return attachments;
    }

    static ResponseEntity<byte[]> download(SupportAttachment file) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFilename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.getContents());
    }

    private static String safeFilename(String name) {
        if (name == null) {
            return "anexo";
        }
        String path = name.replace('\\', '/');
        String filename = path.substring(path.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "_").strip();
        if (filename.isEmpty() || filename.equals(".") || filename.equals("..")) {
            return "anexo";
        }
        int limit = Math.min(filename.codePointCount(0, filename.length()), MAX_FILENAME_CHARS);
        return filename.substring(0, filename.offsetByCodePoints(0, limit));
    }

    static class AttachmentTooLarge extends RuntimeException {
    }
}
