package br.com.nuvemcustomfields.finance;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.config.BackofficeSessionInterceptor;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static br.com.nuvemcustomfields.finance.FinancialEntry.*;

@Controller
@RequestMapping("/backoffice/finance")
public class FinancialController {
    private final FinancialLedger ledger;private final FinancialAgenda agenda;private final FinancialImport importer;private final FinancialSynchronizer sync;
    public FinancialController(FinancialLedger ledger,FinancialAgenda agenda,FinancialImport importer,FinancialSynchronizer sync){this.ledger=ledger;this.agenda=agenda;this.importer=importer;this.sync=sync;}
    @ModelAttribute public void common(HttpSession session,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        if(!Boolean.TRUE.equals(session.getAttribute(BackofficeSessionInterceptor.SESSION_KEY)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(session.getAttribute("financeCsrf")==null)session.setAttribute("financeCsrf",UUID.randomUUID().toString());
        model.addAttribute("labels",Map.ofEntries(Map.entry("PAYMENT","Pagamento confirmado"),Map.entry("RELEASE","Disponibilidade no gateway"),Map.entry("PAYOUT","Repasse"),Map.entry("BANK","Crédito bancário conciliado"),Map.entry("ADJUSTMENT","Ajuste"),Map.entry("CONFIRMED","Confirmado"),Map.entry("PROVIDER","Informado pelo provedor"),Map.entry("ESTIMATED","Estimado"),Map.entry("UNKNOWN","Não informado"),Map.entry("PRODUCTION","Produção"),Map.entry("SANDBOX","Sandbox / teste"),Map.entry("DAILY","Diário"),Map.entry("WEEKLY","Semanal"),Map.entry("FORTNIGHTLY","Quinzenal"),Map.entry("MONTHLY","Mensal"),Map.entry("MANUAL","Sob solicitação")));
        model.addAttribute("csrf",session.getAttribute("financeCsrf"));model.addAttribute("providers",PaymentProviderType.values());
        model.addAttribute("stages",Stage.values());model.addAttribute("certainties",Certainty.values());model.addAttribute("environments",PaymentEnvironment.values());
        model.addAttribute("dateBases",SettlementRule.DateBase.values());model.addAttribute("schedules",SettlementRule.PayoutSchedule.values());model.addAttribute("ruleForm",new SettlementRuleForm());
    }
    private void check(HttpSession session,String csrf){if(csrf==null || !csrf.equals(session.getAttribute("financeCsrf")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Formulário expirado. Atualize a página.");}
    @GetMapping public String page(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate until,
            @RequestParam(required=false) Stage stage,@RequestParam(required=false) String provider,@RequestParam(required=false) String account,
            @RequestParam(required=false) String currency,@RequestParam(required=false) String method,@RequestParam(required=false) String status,
            @RequestParam(required=false) PaymentEnvironment environment,@RequestParam(required=false) String view,Model model){
        var filter=new FinancialAgenda.Filter(from,until,stage,provider,account,currency,method,status,environment,view);
        model.addAttribute("summaries",agenda.summaries(filter));model.addAttribute("filter",filter);model.addAttribute("result",agenda.query(filter));model.addAttribute("coverage",ledger.coverage());model.addAttribute("rules",ledger.rules());return "backoffice/finance";
    }
    @GetMapping("/export.csv") @ResponseBody public ResponseEntity<String> export(@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate until,
            @RequestParam(required=false) Stage stage,@RequestParam(required=false) String provider,@RequestParam(required=false) String account,
            @RequestParam(required=false) String currency,@RequestParam(required=false) String method,@RequestParam(required=false) String status,
            @RequestParam(required=false) PaymentEnvironment environment,@RequestParam(required=false) String view){
        return csv("agenda.csv",agenda.csv(new FinancialAgenda.Filter(from,until,stage,provider,account,currency,method,status,environment,view)));
    }
    private static ResponseEntity<String> csv(String filename,String body){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header("Content-Disposition","attachment; filename="+filename).header("Cache-Control","no-store").body(body);}
    @GetMapping("/import-model.csv") @ResponseBody public ResponseEntity<String> template(){return csv("modelo-financeiro.csv",FinancialImport.EXTENDED_HEADER+"\r\n");}
    @GetMapping("/{key}") public String detail(@PathVariable String key,Model model){
        var entry=ledger.require(key);model.addAttribute("entry",entry);model.addAttribute("history",ledger.history(key));model.addAttribute("links",ledger.links(key));
        model.addAttribute("children",ledger.entries().stream().filter(e->key.equals(e.parentKey())).toList());model.addAttribute("rules",ledger.rules().stream().filter(r->r.matches(FinancialAgenda.project(entry,ledger.entries()))).toList());return "backoffice/finance-detail";
    }
    @PostMapping("/sync") public String synchronize(@RequestParam String csrf,HttpSession session){check(session,csrf);sync.synchronize();return "redirect:/backoffice/finance";}
    @PostMapping("/rules/preview") public String previewRule(@ModelAttribute SettlementRuleForm form,@RequestParam String paymentKey,@RequestParam(required=false) BigDecimal eligibleBalance,@RequestParam String csrf,HttpSession session,Model model){
        check(session,csrf);var rule=form.rule();rule.validate();var entries=ledger.preview(ledger.require(paymentKey),rule,eligibleBalance);session.setAttribute("financeRule",ledger.json(rule));session.setAttribute("financeRuleAt",Instant.now());session.setAttribute("financeRuleToken",UUID.randomUUID().toString());model.addAttribute("reviewToken",session.getAttribute("financeRuleToken"));model.addAttribute("preview",entries);model.addAttribute("pendingRule",rule);model.addAttribute("action","rules/save");return "backoffice/finance-preview";
    }
    private void review(HttpSession session,String key,String token){if(token==null || !token.equals(session.getAttribute(key)))throw new IllegalArgumentException("Esta prévia foi substituída ou confirmada. Revise novamente.");}
    private void fresh(HttpSession session,String key){Object at=session.getAttribute(key);if(!(at instanceof Instant instant)||instant.isBefore(Instant.now().minusSeconds(1800)))throw new IllegalArgumentException("Prévia expirada; simule novamente.");}
    @PostMapping("/rules/save") public String saveRule(@RequestParam String csrf,@RequestParam String reviewToken,HttpSession session){check(session,csrf);review(session,"financeRuleToken",reviewToken);fresh(session,"financeRuleAt");var rule=session.getAttribute("financeRule")==null?null:ledger.read((String)session.getAttribute("financeRule"),SettlementRule.class);if(rule==null)throw new IllegalArgumentException("Simule a regra antes de salvar.");ledger.saveRule(rule);session.removeAttribute("financeRule");return "redirect:/backoffice/finance";}
    @PostMapping("/estimates/preview") public String previewEstimates(@RequestParam List<String> paymentKey,@RequestParam String ruleId,@RequestParam String operator,@RequestParam String csrf,HttpSession session,Model model){
        check(session,csrf);if(paymentKey.size()>1000||operator.isBlank())throw new IllegalArgumentException("Seleção ou responsável inválidos.");
        var rule=ledger.rules().stream().filter(r->r.id().equals(ruleId)).findFirst().orElseThrow();var entries=new ArrayList<FinancialEntry>();
        for(String key:paymentKey)entries.addAll(ledger.preview(ledger.require(key),rule));
        session.setAttribute("financeEstimateKeys",ledger.storeDraft("ESTIMATE",paymentKey));session.setAttribute("financeEstimateRule",ruleId);session.setAttribute("financeEstimateOperator",operator);session.setAttribute("financeEstimateAt",Instant.now());
        model.addAttribute("reviewToken",session.getAttribute("financeEstimateKeys"));model.addAttribute("preview",entries);model.addAttribute("action","estimates/apply");return "backoffice/finance-preview";
    }
    @SuppressWarnings("unchecked") @PostMapping("/estimates/apply") public String apply(@RequestParam String csrf,@RequestParam String reviewToken,HttpSession session){
        check(session,csrf);review(session,"financeEstimateKeys",reviewToken);fresh(session,"financeEstimateAt");var keys=session.getAttribute("financeEstimateKeys")==null?null:Arrays.asList(ledger.read(ledger.draft((String)session.getAttribute("financeEstimateKeys"),"ESTIMATE"),String[].class));if(keys==null)throw new IllegalArgumentException("Gere uma prévia.");
        for(String key:keys)ledger.estimate(key,(String)session.getAttribute("financeEstimateRule"),(String)session.getAttribute("financeEstimateOperator"),true);
        ledger.deleteDraft((String)session.getAttribute("financeEstimateKeys"));session.removeAttribute("financeEstimateKeys");return "redirect:/backoffice/finance";
    }
    @PostMapping("/import/preview") public String importPreview(@RequestParam MultipartFile file,@RequestParam String evidence,@RequestParam String operator,@RequestParam String csrf,HttpSession session,Model model)throws java.io.IOException{
        check(session,csrf);if(file.getSize()>2_000_000||operator.isBlank()||operator.length()>120)throw new IllegalArgumentException("Arquivo ou responsável inválido.");
        var entries=importer.preview(new String(file.getBytes(),StandardCharsets.UTF_8),evidence);session.setAttribute("financeImport",ledger.storeDraft("IMPORT",entries));session.setAttribute("financeImportOperator",operator);session.setAttribute("financeImportAt",Instant.now());
        model.addAttribute("reviewToken",session.getAttribute("financeImport"));model.addAttribute("preview",entries);model.addAttribute("action","import/commit");return "backoffice/finance-preview";
    }
    @SuppressWarnings("unchecked") @PostMapping("/import/commit") public String commitImport(@RequestParam String csrf,@RequestParam String reviewToken,HttpSession session){
        check(session,csrf);review(session,"financeImport",reviewToken);fresh(session,"financeImportAt");var entries=session.getAttribute("financeImport")==null?null:Arrays.asList(ledger.read(ledger.draft((String)session.getAttribute("financeImport"),"IMPORT"),FinancialEntry[].class));if(entries==null)throw new IllegalArgumentException("Importação expirada.");
        importer.commit(entries,(String)session.getAttribute("financeImportOperator"));ledger.deleteDraft((String)session.getAttribute("financeImport"));session.removeAttribute("financeImport");return "redirect:/backoffice/finance";
    }
    @PostMapping("/{key}/allocate") public String allocate(@PathVariable String key,@RequestParam String releaseKey,@RequestParam BigDecimal amount,@RequestParam String evidence,@RequestParam String operator,@RequestParam String csrf,HttpSession session){
        check(session,csrf);ledger.allocate(key,releaseKey,amount,evidence,operator);return "redirect:/backoffice/finance/"+key;
    }
    @PostMapping("/{key}/bank") public String bank(@PathVariable String key,@RequestParam String reference,@RequestParam LocalDate date,@RequestParam BigDecimal amount,@RequestParam String evidence,@RequestParam String operator,@RequestParam String csrf,HttpSession session){
        check(session,csrf);var payout=ledger.require(key);ledger.record(new FinancialEntry(null,payout.provider(),payout.account(),payout.environment(),reference,"1",Stage.BANK,key,null,null,null,null,payout.currency(),null,null,null,amount,date,null,null,"reconciled",Certainty.CONFIRMED,"MANUAL",evidence,Instant.now(),null,null),operator);return "redirect:/backoffice/finance/"+key;
    }
    @PostMapping("/{key}/adjustment") public String adjustment(@PathVariable String key,@RequestParam String reference,
            @RequestParam String kind,@RequestParam(required=false) LocalDate date,@RequestParam(required=false) BigDecimal amount,
            @RequestParam(required=false) BigDecimal exchangeRate,@RequestParam String evidence,@RequestParam String operator,@RequestParam String csrf,HttpSession session){
        check(session,csrf);if(!Set.of("date_override","correction","refund","dispute","fee","reserve_hold","reserve_release","anticipation","payout_return","fx").contains(kind))throw new IllegalArgumentException("Tipo de ajuste inválido.");
        if("date_override".equals(kind) && date==null || !"date_override".equals(kind) && amount==null)throw new IllegalArgumentException("Informe data ou valor do ajuste.");
        var parent=ledger.require(key);ledger.record(new FinancialEntry(null,parent.provider(),parent.account(),parent.environment(),reference,"1",Stage.ADJUSTMENT,key,parent.storeId(),parent.subscriptionId(),parent.country(),parent.method(),parent.currency(),null,null,null,amount,date,null,null,kind,Certainty.CONFIRMED,"MANUAL",evidence,Instant.now(),null,null).withFinancialDates(null,null,exchangeRate),operator);
        return "redirect:/backoffice/finance/"+key;
    }
    @ExceptionHandler(IllegalArgumentException.class) public String invalid(IllegalArgumentException ex,HttpServletResponse response,Model model){response.setStatus(400);model.addAttribute("financeError",ex.getMessage());return "backoffice/finance-error";}
}
