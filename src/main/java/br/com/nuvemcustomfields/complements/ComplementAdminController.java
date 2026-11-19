package br.com.nuvemcustomfields.complements;

import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.properties.NuvemshopProperties;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.NuvemshopApiClient;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/apps")
public class ComplementAdminController {
    private static final String HOME = "redirect:/admin/apps/complements";
    private static final String CSRF = "complementAdminCsrf";

    private final AdminStoreService stores;
    private final NuvemshopApiClient catalog;
    private final ComplementOffers offers;
    private final Messages messages;
    private final NuvemshopProperties properties;

    public ComplementAdminController(AdminStoreService stores, NuvemshopApiClient catalog,
                                     ComplementOffers offers, Messages messages, NuvemshopProperties properties) {
        this.stores = stores;
        this.catalog = catalog;
        this.offers = offers;
        this.messages = messages;
        this.properties = properties;
    }

    @GetMapping
    public String apps(HttpSession session, Model model) {
        page(session, model);
        return "admin/apps";
    }

    @GetMapping("/complements")
    public String list(HttpSession session, Model model) {
        Store store = page(session, model);
        model.addAttribute("offerGroups", offers.list(store.getStoreId()).stream()
                .collect(Collectors.groupingBy(ComplementOffers.Offer::parentId, LinkedHashMap::new, Collectors.toList())));
        return "admin/complements";
    }

    @GetMapping("/complements/new")
    public String newOffer(@RequestParam(required = false) Long parentId,
                           @RequestParam(required = false) Long productId,
                           @RequestParam(defaultValue = "false") boolean create,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(required = false) String q,
                           HttpSession session, Locale locale, Model model) {
        Store store = page(session, model);
        boolean canCreate = ComplementOffers.hasScope(store, "write_products");
        model.addAttribute("canCreate", canCreate);
        model.addAttribute("canReauthorize", !canCreate && ComplementOffers.hasScope(properties.scopes(), "write_products"));
        model.addAttribute("parentId", parentId);
        model.addAttribute("productId", productId);
        model.addAttribute("creationForm", parentId != null && create && canCreate);
        try {
            if (parentId != null) {
                model.addAttribute("parentName", ComplementOffers.name(offers.product(store, parentId), locale.getLanguage()));
            }
            if (parentId != null && create && canCreate) {
                model.addAttribute("requestId", UUID.randomUUID());
            } else if (parentId != null && productId != null && !parentId.equals(productId)) {
                var product = offers.product(store, productId);
                model.addAttribute("productName", ComplementOffers.name(product, locale.getLanguage()));
                model.addAttribute("variants", offers.variants(product, locale.getLanguage()));
            } else {
                model.addAttribute("catalogPage", catalog.listProducts(store, page, 30, q));
            }
        } catch (ComplementOffers.Invalid ex) {
            model.addAttribute("error", messages.get(ex.getMessage()));
        } catch (RestClientException ex) {
            model.addAttribute("error", messages.get("complements.error.catalog"));
        }
        return "admin/complement-form";
    }

    @PostMapping("/complements/link")
    public String link(@RequestParam long parentId, @RequestParam long productId, @RequestParam long variantId,
                       @RequestParam String label, @RequestParam(required = false) String subtitle,
                       @RequestParam String csrf, HttpSession session, Locale locale,
                       RedirectAttributes flash) {
        Store store = authorized(session, csrf);
        try {
            offers.link(store, parentId, productId, variantId, label, subtitle, locale.getLanguage());
            notice(flash, "saved");
            return HOME;
        } catch (ComplementOffers.Invalid ex) {
            error(flash, ex);
            flash.addFlashAttribute("draftLabel", label);
            flash.addFlashAttribute("draftSubtitle", subtitle);
            return "redirect:/admin/apps/complements/new?parentId=" + parentId + "&productId=" + productId;
        }
    }

    @PostMapping("/complements/create")
    public String create(@RequestParam UUID requestId, @RequestParam long parentId, @RequestParam String label,
                         @RequestParam(required = false) String subtitle,
                         @ModelAttribute ComplementOffers.NewProduct product, @RequestParam String csrf,
                         HttpSession session, Locale locale, RedirectAttributes flash) {
        Store store = authorized(session, csrf);
        try {
            offers.create(store, requestId, parentId, label, subtitle, product, locale.getLanguage());
            notice(flash, "saved");
            return HOME;
        } catch (ComplementOffers.Invalid ex) {
            error(flash, ex);
            if (ex.getMessage().endsWith(".uncertain") || ex.getMessage().endsWith(".submitted")) return HOME;
            flash.addFlashAttribute("draft", product);
            flash.addFlashAttribute("draftLabel", label);
            flash.addFlashAttribute("draftSubtitle", subtitle);
            return "redirect:/admin/apps/complements/new?parentId=" + parentId + "&create=true";
        }
    }

    @PostMapping("/complements/{id}/recover")
    public String recover(@PathVariable UUID id, @RequestParam String csrf, HttpSession session,
                          RedirectAttributes flash) {
        Store store = authorized(session, csrf);
        try {
            offers.recover(store, id);
            notice(flash, "saved");
        } catch (ComplementOffers.Invalid ex) {
            error(flash, ex);
        }
        return HOME;
    }

    public record Preview(String image, String price, String currency) {}

    @GetMapping("/complements/{id}/preview")
    @ResponseBody
    public Preview preview(@PathVariable UUID id, HttpSession session) {
        Store store = stores.requireCurrentStore(session);
        var offer = offers.list(store.getStoreId()).stream()
                .filter(candidate -> candidate.ready() && candidate.id().equals(id.toString())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            var product = offers.product(store, offer.productId());
            String image = product.path("images").path(0).path("src").asText("");
            for (var variant : product.path("variants")) {
                if (variant.path("id").asLong() != offer.variantId()) continue;
                String price = variant.path("promotional_price").asText("");
                if (price.isBlank()) price = variant.path("price").asText("");
                return new Preview(image.startsWith("https://") ? image : null, price, store.getStoreCurrency());
            }
        } catch (ComplementOffers.Invalid ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @PostMapping("/complements/{id}/update")
    public String update(@PathVariable UUID id, @RequestParam String label,
                         @RequestParam(required = false) String subtitle,
                         @RequestParam(defaultValue = "false") boolean enabled,
                         @RequestParam(defaultValue = "true") boolean showImage,
                         @RequestParam(required = false) String backgroundColor, @RequestParam(required = false) String textColor,
                         @RequestParam(required = false) String accentColor,
                         @RequestParam(defaultValue = "true") boolean showBorder, @RequestParam(defaultValue = "true") boolean roundedCorners,
                         @RequestParam String csrf,
                         HttpSession session, RedirectAttributes flash) {
        Store store = authorized(session, csrf);
        try {
            offers.update(store.getStoreId(), id, label, subtitle, enabled,
                    new ComplementOffers.Appearance(showImage, backgroundColor, textColor, accentColor, showBorder, roundedCorners));
            notice(flash, "updated");
        } catch (ComplementOffers.Invalid ex) {
            error(flash, ex);
        }
        return HOME;
    }

    @PostMapping("/complements/{id}/remove")
    public String remove(@PathVariable UUID id, @RequestParam String csrf, HttpSession session,
                         RedirectAttributes flash) {
        Store store = authorized(session, csrf);
        try {
            offers.remove(store.getStoreId(), id);
            notice(flash, "removed");
        } catch (ComplementOffers.Invalid ex) {
            error(flash, ex);
        }
        return HOME;
    }

    private Store page(HttpSession session, Model model) {
        Store store = stores.requireCurrentStore(session);
        if (session.getAttribute(CSRF) == null) session.setAttribute(CSRF, UUID.randomUUID().toString());
        model.addAttribute("csrf", session.getAttribute(CSRF));
        model.addAttribute("store", store);
        return store;
    }

    private Store authorized(HttpSession session, String token) {
        Store store = stores.requireCurrentStore(session);
        if (!token.equals(session.getAttribute(CSRF))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return store;
    }

    private void error(RedirectAttributes flash, ComplementOffers.Invalid ex) {
        flash.addFlashAttribute("error", messages.get(ex.getMessage()));
    }

    private void notice(RedirectAttributes flash, String key) {
        flash.addFlashAttribute("message", messages.get("complements." + key));
    }
}
