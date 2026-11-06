package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.service.SesEventConsumer;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/backoffice/email-events")
public class BackofficeEmailEventsController {
    private static final String TOKEN = "sesEventConsumptionToken";
    private final SesEventConsumer consumer;
    private final ExecutorService executor;
    public BackofficeEmailEventsController(SesEventConsumer consumer,
            @Qualifier("deploymentBackgroundExecutor") ExecutorService executor) {
        this.consumer = consumer; this.executor = executor;
    }
    @GetMapping
    public String page(HttpSession session, Model model) {
        synchronized (session) {
            if (!(session.getAttribute(TOKEN) instanceof String)) session.setAttribute(TOKEN, UUID.randomUUID().toString());
            model.addAttribute("actionToken", session.getAttribute(TOKEN));
        }
        model.addAttribute("run", consumer.status());
        model.addAttribute("configurationProblem", consumer.configurationProblem());
        return "backoffice/email-events";
    }
    @PostMapping("/consume")
    public String consume(@RequestParam(defaultValue = "") String actionToken, HttpSession session, RedirectAttributes flash) {
        Object saved = session.getAttribute(TOKEN);
        if (!(saved instanceof String expected) || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actionToken.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Abra a página novamente antes de executar esta ação.");
        switch (consumer.startManual(executor)) {
            case STARTED -> flash.addFlashAttribute("message", "Consumo solicitado. Acompanhe o resultado nesta página.");
            case RUNNING -> flash.addFlashAttribute("message", "Já existe um consumo em andamento nesta instância. Aguarde a conclusão.");
            case DISABLED, MISCONFIGURED -> flash.addFlashAttribute("error", consumer.configurationProblem());
            case UNAVAILABLE -> flash.addFlashAttribute("error", "A aplicação está encerrando. Tente novamente após a reinicialização.");
        }
        return "redirect:/backoffice/email-events";
    }
}
