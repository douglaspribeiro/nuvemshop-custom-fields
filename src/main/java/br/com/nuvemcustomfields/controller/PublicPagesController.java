package br.com.nuvemcustomfields.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicPagesController {

    @GetMapping("/privacy")
    public String privacyAlias() {
        return "redirect:/privacy/";
    }

    @GetMapping("/privacy/")
    public String privacy() {
        return "public/privacy";
    }

    @GetMapping("/support")
    public String supportAlias() {
        return "redirect:/support/";
    }

    @GetMapping("/support/")
    public String support() {
        return "public/support";
    }
}
