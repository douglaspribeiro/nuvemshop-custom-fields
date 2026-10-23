package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/** Durable observations prevent a stale COMPLETED execution from resurrecting refunded access. */
@Service
public class DlocalGoFinancialService {
    private final DlocalGoGateway gateway;
    private final DlocalGoReversalRepository reversals;
    public DlocalGoFinancialService(DlocalGoGateway gateway,DlocalGoReversalRepository reversals) {
        this.gateway=gateway;this.reversals=reversals;
    }
    @Transactional
    public String paymentStatus(String order,String currency,BigDecimal paid) {
        observe(order,currency,"REFUND","/v1/refunds?country=AR");
        observe(order,currency,"CHARGEBACK","/v1/chargebacks?country=AR");
        BigDecimal refunded=BigDecimal.ZERO;
        for(var entry:reversals.findByEnvironmentAndOrderId(gateway.environment(),order)) {
            if(!currency.equals(entry.getCurrency())) throw new PaymentGatewayException("Moeda do estorno divergente.");
            if("CHARGEBACK".equals(entry.getKind()) && Set.of("PENDING","SUCCESS").contains(entry.getStatus())) return "chargedBack";
            if("REFUND".equals(entry.getKind()) && "SUCCESS".equals(entry.getStatus())) refunded=refunded.add(entry.getAmount());
        }
        return refunded.compareTo(paid)>=0 && paid.signum()>0?"refunded":(refunded.signum()>0?"partialRefund":"paid");
    }
    public BigDecimal refunded(String order) {
        return reversals.findByEnvironmentAndOrderId(gateway.environment(),order).stream()
                .filter(e -> "REFUND".equals(e.getKind()) && "SUCCESS".equals(e.getStatus()))
                .map(DlocalGoReversal::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private void observe(String order,String currency,String kind,String path) {
        for(JsonNode row:gateway.financialPages(path)) {
            if(!order.equals(row.path("order_id").asText())) continue;
            String remote=row.path("id").asText(),status=row.path("status").asText();
            if(!remote.matches("[A-Za-z0-9-]{1,120}") || !Set.of("PENDING","SUCCESS","REJECTED","CANCELLED").contains(status)
                    || !currency.equals(row.path("currency").asText())) throw new PaymentGatewayException("Estorno dLocal Go inválido.");
            String key=gateway.environment()+":"+kind+":"+remote;
            var entry=reversals.findById(key).orElseGet(DlocalGoReversal::new);
            if(entry.getId()!=null && (!order.equals(entry.getOrderId()) || !kind.equals(entry.getKind())))
                throw new PaymentGatewayException("Identidade de estorno divergente.");
            if("REFUND".equals(kind) && "SUCCESS".equals(entry.getStatus())) continue;
            BigDecimal amount=new BigDecimal(row.path("REFUND".equals(kind)?"amount":"local_amount").asText());
            if(amount.signum()<=0) throw new PaymentGatewayException("Valor de estorno inválido.");
            entry.setId(key);entry.setEnvironment(gateway.environment());entry.setOrderId(order);entry.setKind(kind);
            entry.setCurrency(currency);entry.setAmount(amount);entry.setStatus(status);reversals.save(entry);
        }
    }
}
