package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.entity.*;
import br.com.nuvemcustomfields.service.UpgradeCouponService;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.UUID;

@Controller
public class BackofficeUpgradeCouponsController {
    public static final String TOKEN_KEY="upgradeCouponActionToken";
    private final UpgradeCouponService coupons;
    public BackofficeUpgradeCouponsController(UpgradeCouponService coupons){this.coupons=coupons;}
    @GetMapping("/backoffice/payments/coupons")
    public String list(Model model,HttpSession session,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        if(session.getAttribute(TOKEN_KEY)==null)session.setAttribute(TOKEN_KEY,UUID.randomUUID().toString());
        model.addAttribute("couponActionToken",session.getAttribute(TOKEN_KEY));
        var catalog=coupons.list();
        model.addAttribute("coupons",catalog);model.addAttribute("couponUses",coupons.recentUses());
        model.addAttribute("couponNames",catalog.stream().collect(java.util.stream.Collectors.toMap(UpgradeCoupon::getId,UpgradeCoupon::getCode)));
        model.addAttribute("environments",PaymentEnvironment.values());
        model.addAttribute("couponPlans",new PlanType[]{PlanType.PREMIUM,PlanType.PREMIUM_PLUS,PlanType.PREMIUM_ULTRA});
        return "backoffice/upgrade-coupons";
    }
    @PostMapping("/backoffice/payments/coupons")
    public String save(@RequestParam String actionToken,@RequestParam(required=false) Long id,@RequestParam String code,
            @RequestParam BigDecimal discountPercent,@RequestParam PaymentEnvironment environment,@RequestParam PlanType targetPlan,
            @RequestParam(defaultValue="false") boolean enabled,@RequestParam(defaultValue="") String startsAt,
            @RequestParam(defaultValue="") String endsAt,@RequestParam(required=false) Integer maxUses,
            @RequestParam(defaultValue="1") int maxUsesPerStore,HttpSession session,RedirectAttributes flash){
        validate(session,actionToken);
        try{
            coupons.save(id,code,discountPercent,environment,targetPlan,enabled,date(startsAt),date(endsAt),maxUses,maxUsesPerStore);
            flash.addFlashAttribute("message","Cupom salvo. O desconto vale somente para o ajuste deste ciclo, sem alterar a mensalidade recorrente.");
        }catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        catch(org.springframework.dao.DataIntegrityViolationException ex){flash.addFlashAttribute("error","Já existe um cupom com este código. Atualize a lista.");}
        return "redirect:/backoffice/payments/coupons";
    }
    @PostMapping("/backoffice/payments/coupons/{id}/delete")
    public String delete(@PathVariable Long id,@RequestParam String actionToken,@RequestParam(defaultValue="") String confirmation,
            HttpSession session,RedirectAttributes flash){
        validate(session,actionToken);
        if(!"EXCLUIR".equals(confirmation))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirme a exclusão do cupom.");
        try{flash.addFlashAttribute("message",coupons.delete(id)?"Cupom excluído.":"Cupom desativado: o histórico foi preservado.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:/backoffice/payments/coupons";
    }
    private Instant date(String value){
        try{return value.isBlank()?null:LocalDateTime.parse(value).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();}
        catch(java.time.format.DateTimeParseException ex){throw new IllegalArgumentException("Informe datas e horários válidos.");}
    }
    private void validate(HttpSession session,String value){
        if(!(session.getAttribute(TOKEN_KEY) instanceof String expected)
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),value.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Abra a página de cupons novamente.");
    }
}
