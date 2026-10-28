package br.com.nuvemcustomfields.service;

import java.time.Duration;

/** Decimal units, identical to the commercial offer (GB and MB, not GiB/MiB). */
public final class BuyerUploadPolicy {
    public static final String OFFER = "BUYER_UPLOAD_V1";
    public static final long MAX_FILE_BYTES = 20_000_000L;
    public static final long PRO_BYTES = 5_000_000_000L;
    public static final long ULTRA_BYTES = 25_000_000_000L;
    public static final Duration TEMPORARY_RETENTION = Duration.ofHours(24);
    public static final Duration ORDER_RETENTION = Duration.ofDays(90);
    private BuyerUploadPolicy() {}

    public static int warningPercent(long occupied, long quota) {
        if (quota <= 0) return 0;
        if (occupied >= quota) return 100;
        if (occupied >= quota * 95 / 100) return 95;
        if (occupied >= quota * 80 / 100) return 80;
        return 0;
    }
}
