package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.payment.*;
import br.com.nuvemcustomfields.repository.PaymentSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.env.Environment;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
public class FinancialSynchronizer {
    private final List<PaymentGateway> gateways;
    private final PaymentSubscriptionRepository subscriptions;
    private final FinancialLedger ledger;
    private final FinancialNormalizer normalizer;
    private final Environment configuration;
    public FinancialSynchronizer(List<PaymentGateway> gateways,PaymentSubscriptionRepository subscriptions,
            FinancialLedger ledger,FinancialNormalizer normalizer,Environment configuration){
        this.gateways=gateways;this.subscriptions=subscriptions;this.ledger=ledger;this.normalizer=normalizer;this.configuration=configuration;
    }
    public String account(PaymentProviderType provider){return configuration.getProperty("payments.finance.accounts."+provider.name().toLowerCase(Locale.ROOT),"default");}
    @Scheduled(fixedDelayString="${payments.finance.sync-delay-ms:900000}",initialDelayString="${payments.finance.initial-delay-ms:120000}")
    public void scheduled(){if(configuration.getProperty("payments.finance.enabled",Boolean.class,true))synchronize();}
    public synchronized void synchronize(){
        var known=subscriptions.findAll();
        for(PaymentProviderType provider:PaymentProviderType.values()){
            var optional=gateways.stream().filter(g->g.provider()==provider).findFirst();String account=account(provider);
            if(optional.isEmpty()){ledger.syncStatus(provider+":"+account,provider,account,PaymentEnvironment.PRODUCTION,"Sem adaptador ativo. Importação manual documentada disponível.",false);continue;}
            PaymentGateway gateway=optional.get();PaymentEnvironment environment=gateway.environment();
            String scope=provider+":"+account+":"+environment;
            if(!gateway.configured() && !gateway.operational()){
                ledger.syncStatus(scope,provider,account,environment,"Gateway desativado ou sem credenciais. Histórico por relatório manual.",false);continue;}
            boolean success=true;int count=0;
            for(var subscription:known){
                if(subscription.getProvider()!=provider || subscription.getProviderEnvironment()!=environment || subscription.getProviderSubscriptionId()==null)continue;
                String subScope=scope+":"+subscription.getProviderSubscriptionId();
                try{
                    // Fetch the complete page chain before recording a successful coverage statement.
                    var data=gateway.financialHistory(subscription.getProviderSubscriptionId());
                    for(var item:data) for(var entry:normalizer.payment(provider,account,environment,subscription.getStoreId(),subscription.getProviderSubscriptionId(),subscription.getCountryCode(),item)){
                        var saved=ledger.record(entry,"gateway-sync");count++;
                        if(saved.stage()==FinancialEntry.Stage.PAYMENT)ledger.rule(saved).ifPresent(rule->ledger.estimate(saved.key(),rule.id(),"gateway-sync",false));
                    }
                    for(var payment:ledger.entries())if(payment.provider()==provider && payment.account().equals(account)
                            && payment.environment()==environment && payment.stage()==FinancialEntry.Stage.PAYMENT && subscription.getProviderSubscriptionId().equals(payment.subscriptionId()))
                        ledger.rule(payment).ifPresent(rule->ledger.estimate(payment.key(),rule.id(),"gateway-sync",false));
                    ledger.syncStatus(subScope,provider,account,environment,"Histórico consultado da assinatura conhecida. Datas e líquidos ausentes exigem relatório; contratos substituídos fora da base podem não estar cobertos.",true);
                }catch(RuntimeException ex){success=false;ledger.syncStatus(subScope,provider,account,environment,"Consulta de histórico pendente; registros anteriores preservados. Conferir permissões e importar relatório se necessário.",false);
                    LoggerFactory.getLogger(getClass()).warn("finance.sync.failed provider={} reason={}",provider,ex.getClass().getSimpleName());}
            }
            if(provider==PaymentProviderType.STRIPE || provider.isDodo()){
                try{for(var item:gateway.financialPayouts())ledger.record(normalizer.payout(provider,account,environment,item),"gateway-sync");}
                catch(RuntimeException ex){success=false;}
            }
            ledger.syncStatus(scope,provider,account,environment,"Leitura de "+count+" lançamentos. "+coverage(provider),success);
        }
    }
    public static String coverage(PaymentProviderType provider){return switch(provider){
        case STRIPE->"Cobranças/faturas, transações de saldo e payouts via API. Crédito bancário e vínculo agregado: conciliação documentada.";
        case MERCADO_PAGO->"Pagamentos de assinaturas, tarifas, líquido e money_release_date/status via API. Saques: relatório manual.";
        case EFI->"Cobranças do histórico da assinatura via API. Disponibilidade, taxas e repasses: relatório ou regra de conta documentada.";
        case DODO,DODO_TEST->"Pagamentos e payouts via API. Data efetiva não substituída por created_at/updated_at. Saldo, liquidação e vínculo de payout: relatório/ledger documentado.";
        case CREEM->"Histórico de transações via API, sujeito à permissão. Liberação e payouts: relatório documentado.";
        default->"Sem adaptador financeiro: importação documentada por conta/ambiente.";};}
}
