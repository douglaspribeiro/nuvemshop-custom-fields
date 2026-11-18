package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Set;

/** A version is immutable; applying another version is an explicit audited operation. */
public record SettlementRule(String id, PaymentProviderType provider, String account, PaymentEnvironment environment,
        String country, String currency, String method, String installment, LocalDate validFrom, LocalDate validUntil,
        String zone, LocalTime cutoff, int releaseDays, boolean businessDays, boolean rollForward, Set<LocalDate> holidays,
        PayoutSchedule schedule, int payoutDay, int bankDays, int firstPayoutDays, LocalDate firstPayment,
        BigDecimal minimum, BigDecimal reserve, BigDecimal estimatedFeePercent, BigDecimal estimatedFixedFee,
        boolean blocked, String evidence, String operator, DateBase dateBase, LocalTime payoutCutoff) {
    public enum DateBase { PAYMENT, CAPTURE, POSTING }
    public SettlementRule(String id, PaymentProviderType provider, String account, PaymentEnvironment environment,
        String country, String currency, String method, String installment, LocalDate validFrom, LocalDate validUntil,
        String zone, LocalTime cutoff, int releaseDays, boolean businessDays, boolean rollForward, Set<LocalDate> holidays,
        PayoutSchedule schedule, int payoutDay, int bankDays, int firstPayoutDays, LocalDate firstPayment,
        BigDecimal minimum, BigDecimal reserve, BigDecimal estimatedFeePercent, BigDecimal estimatedFixedFee,
        boolean blocked, String evidence, String operator) {
        this(id,provider,account,environment,country,currency,method,installment,validFrom,validUntil,zone,cutoff,releaseDays,businessDays,rollForward,holidays,schedule,payoutDay,bankDays,firstPayoutDays,firstPayment,minimum,reserve,estimatedFeePercent,estimatedFixedFee,blocked,evidence,operator,DateBase.PAYMENT,null);
    }
    public enum PayoutSchedule { DAILY, WEEKLY, FORTNIGHTLY, MONTHLY, MANUAL }
    public void validate() {
        if(provider==null || environment==null || account==null || !account.matches("[A-Za-z0-9_.-]{1,80}")
                || validFrom==null || validUntil!=null && validUntil.isBefore(validFrom)
                || releaseDays<0 || releaseDays>3660 || bankDays<0 || bankDays>366 || firstPayoutDays<0 || firstPayoutDays>3660
                || schedule==null || dateBase==null || payoutDay<1 || payoutDay>31 || schedule==PayoutSchedule.WEEKLY && payoutDay>7
                || evidence==null || evidence.isBlank() || operator==null || operator.isBlank())
            throw new IllegalArgumentException("Regra inválida: confira vigência, prazos, fonte e responsável.");
        ZoneId.of(zone);
        for(var value:new BigDecimal[]{minimum,reserve,estimatedFeePercent,estimatedFixedFee})
            if(value!=null && value.signum()<0) throw new IllegalArgumentException("Taxas e limites devem ser positivos.");
        if(estimatedFeePercent!=null && estimatedFeePercent.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException("Taxa percentual inválida.");
        if(currency!=null && !currency.isBlank()) java.util.Currency.getInstance(currency);
    }
    public boolean matches(FinancialEntry payment) {
        LocalDate base=base(payment);
        return provider==payment.provider() && account.equals(payment.account()) && environment==payment.environment()
                && match(country,payment.country()) && match(currency,payment.currency()) && match(method,payment.method())
                && match(installment,payment.installment()) && base!=null && !base.isBefore(validFrom)
                && (validUntil==null || !base.isAfter(validUntil));
    }
    private static boolean match(String selector,String value) {return selector==null || selector.isBlank() || selector.equalsIgnoreCase(value);}
    public int specificity() {return (country==null||country.isBlank()?0:1)+(currency==null||currency.isBlank()?0:1)
            +(method==null||method.isBlank()?0:1)+(installment==null||installment.isBlank()?0:1);}
    public LocalDate base(FinancialEntry payment) {
        if(dateBase==DateBase.CAPTURE)return payment.capturedAt()==null?null:payment.capturedAt().atZone(ZoneId.of(zone)).toLocalDate();
        if(dateBase==DateBase.POSTING)return payment.postedDate();
        if(payment.paidAt()!=null) return payment.paidAt().atZone(ZoneId.of(zone)).toLocalDate();
        return payment.paidDate();
    }
    public boolean working(LocalDate day) {return day.getDayOfWeek()!=DayOfWeek.SATURDAY && day.getDayOfWeek()!=DayOfWeek.SUNDAY
            && (holidays==null || !holidays.contains(day));}
    public LocalDate roll(LocalDate date) {while(!working(date)) date=date.plusDays(1); return date;}
    public LocalDate add(LocalDate date,int count,boolean business) {
        for(int n=0;n<count;) {date=date.plusDays(1); if(!business || working(date)) n++;}
        return date;
    }
    public LocalDate releaseDate(FinancialEntry payment) {
        LocalDate base=base(payment); if(base==null)return null;
        Instant timestamp=dateBase==DateBase.CAPTURE?payment.capturedAt():dateBase==DateBase.PAYMENT?payment.paidAt():null;
        if(cutoff!=null && (timestamp==null || !timestamp.atZone(ZoneId.of(zone)).toLocalTime().isBefore(cutoff)))
            base=base.plusDays(1);
        if(businessDays) base=roll(base);
        LocalDate result=add(base,releaseDays,businessDays);
        return rollForward?roll(result):result;
    }
    public LocalDate payoutDate(FinancialEntry payment, BigDecimal knownEligibleBalance) {
        LocalDate date=releaseDate(payment);
        // If the release instant is unavailable, a payout cutoff cannot safely be assumed met.
        if(date!=null && payoutCutoff!=null)date=date.plusDays(1);
        if(date==null || blocked || schedule==PayoutSchedule.MANUAL || minimum!=null && minimum.signum()>0
                && (knownEligibleBalance==null || knownEligibleBalance.subtract(reserve==null?BigDecimal.ZERO:reserve).compareTo(minimum)<0)) return null;
        if(firstPayoutDays>0) {
            if(firstPayment==null)return null;
            LocalDate first=firstPayment.plusDays(firstPayoutDays); if(date.isBefore(first))date=first;
        }
        switch(schedule) {
            case WEEKLY -> {while(date.getDayOfWeek().getValue()!=payoutDay)date=date.plusDays(1);}
            case FORTNIGHTLY -> {
                // Two monthly windows anchored on the configured day; days > month length use month end.
                LocalDate first=date.withDayOfMonth(Math.min(payoutDay,date.lengthOfMonth()));
                LocalDate second=first.plusDays(15);
                date=!date.isAfter(first)?first:!date.isAfter(second)?second:first.plusMonths(1).withDayOfMonth(Math.min(payoutDay,first.plusMonths(1).lengthOfMonth()));
            }
            case MONTHLY -> {LocalDate candidate=date.withDayOfMonth(Math.min(payoutDay,date.lengthOfMonth()));
                if(candidate.isBefore(date)) {candidate=date.plusMonths(1);candidate=candidate.withDayOfMonth(Math.min(payoutDay,candidate.lengthOfMonth()));} date=candidate;}
            default -> { }
        }
        return add(roll(date),bankDays,true);
    }
}
