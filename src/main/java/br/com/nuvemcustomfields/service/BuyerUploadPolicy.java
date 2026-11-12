package br.com.nuvemcustomfields.service;

import java.time.Duration;

/** Decimal units, identical to the commercial offer (GB and MB, not GiB/MiB). */
public final class BuyerUploadPolicy {
    public static final long MAX_FILE_BYTES = 20_000_000L;
    public static final long PRO_BYTES = 2_000_000_000L;
    public static final long ULTRA_BYTES = 10_000_000_000L;
    public static final Duration TEMPORARY_RETENTION = Duration.ofHours(24);
    public static final Duration ORDER_RETENTION = Duration.ofDays(90);
    public static long maxFileBytes(br.com.nuvemcustomfields.entity.PlanType plan) {
        return plan == br.com.nuvemcustomfields.entity.PlanType.PREMIUM_ULTRA ? MAX_FILE_BYTES : 10_000_000L;
    }
    private BuyerUploadPolicy() {}

    public static int warningPercent(long occupied, long quota) {
        if (quota <= 0) return 0;
        if (occupied >= quota) return 100;
        if (occupied >= quota * 95 / 100) return 95;
        if (occupied >= quota * 75 / 100) return 75;
        if (occupied >= quota * 50 / 100) return 50;
        return 0;
    }
}
