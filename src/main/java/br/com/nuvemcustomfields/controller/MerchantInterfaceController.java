package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.config.MerchantUiModelInterceptor;
import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.service.MerchantInterfaceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class MerchantInterfaceController {
    private final MerchantInterfaceService preferences;

    public MerchantInterfaceController(MerchantInterfaceService preferences) {
        this.preferences = preferences;
    }

    @PostMapping("/admin/interface")
    public String choose(@RequestParam String choice, @RequestParam(required = false) String csrf,
                         HttpServletRequest request) {
        var session = request.getSession(false);
        Long storeId = MerchantUiModelInterceptor.storeId(session);
        if (storeId == null || MerchantUiModelInterceptor.isOperator(session)
                || !MerchantUiModelInterceptor.validToken(session, storeId, csrf)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        Choice selected = switch (choice) {
            case "preview" -> Choice.PREVIEW;
            case "classic" -> Choice.CLASSIC;
            case "decline" -> Choice.DECLINE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
        if (!preferences.choose(storeId, selected)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return "redirect:/admin";
    }
}
