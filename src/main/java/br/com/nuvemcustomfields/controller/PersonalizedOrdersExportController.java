package br.com.nuvemcustomfields.controller;

import br.com.nuvemcustomfields.i18n.Messages;
import br.com.nuvemcustomfields.service.AdminStoreService;
import br.com.nuvemcustomfields.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class PersonalizedOrdersExportController {
    private final AdminStoreService stores;
    private final ReportService reports;
    private final Messages messages;

    public PersonalizedOrdersExportController(AdminStoreService stores,ReportService reports,Messages messages) {
        this.stores=stores;this.reports=reports;this.messages=messages;
    }

    @GetMapping("/admin/dashboard/export.csv")
    public void export(HttpSession session,HttpServletResponse response) throws IOException {
        var summary=reports.dashboard(stores.requireCurrentStore(session));
        if(!summary.ordersAvailable()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,messages.get("admin.dashboard.export.unavailable"));
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition","attachment; filename=pedidos-personalizados.csv");
        response.setHeader("Cache-Control","no-store");
        var out=response.getWriter();
        out.write('\uFEFF');
        out.write(List.of("order","date","item","productId","product","quantity","field","answer").stream()
                .map(key->cell(messages.get("admin.dashboard.export."+key))).collect(Collectors.joining(";"))+"\r\n");
        for(var order:summary.personalizedOrders()) for(var item:order.items()) for(var field:item.fieldGroups()) {
            String answer=field.files().isEmpty()?field.value():field.files().stream().map(file->file.name()).collect(Collectors.joining("; "));
            out.write(List.of(String.valueOf(order.number()),order.createdAt(),String.valueOf(item.position()),item.productId(),item.name(),
                    String.valueOf(item.quantity()),field.label(),answer).stream().map(PersonalizedOrdersExportController::cell)
                    .collect(Collectors.joining(";"))+"\r\n");
        }
    }

    private static String cell(String value) {
        String text=value==null?"":value;
        String trimmed=text.stripLeading();
        if((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0))>=0) || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) text="'"+text;
        return "\""+text.replace("\"","\"\"")+"\"";
    }
}
