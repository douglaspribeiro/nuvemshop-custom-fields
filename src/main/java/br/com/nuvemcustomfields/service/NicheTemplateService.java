package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.FieldTemplate;
import br.com.nuvemcustomfields.dto.NicheTemplate;
import br.com.nuvemcustomfields.entity.FieldType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NicheTemplateService {

    private final List<NicheTemplate> templates = List.of(
            new NicheTemplate(
                    "camiseta",
                    "Camiseta",
                    "Nome e numero para uniformes, times e presentes.",
                    List.of(
                            new FieldTemplate("Nome na camisa", FieldType.TEXT, true, 20, "Ex: Douglas", null, null),
                            new FieldTemplate("Numero", FieldType.NUMBER, true, 2, "Ex: 10", "^[0-9]{1,2}$", null)
                    )
            ),
            new NicheTemplate(
                    "caneca",
                    "Caneca",
                    "Texto curto para gravacao ou presente personalizado.",
                    List.of(
                            new FieldTemplate("Nome para gravar", FieldType.TEXT, true, 30, "Ex: Mariana", null, null),
                            new FieldTemplate("Mensagem curta", FieldType.TEXT, false, 60, "Ex: Feliz aniversario", null, null)
                    )
            ),
            new NicheTemplate(
                    "convite",
                    "Convite",
                    "Dados essenciais para convites e papelaria personalizada.",
                    List.of(
                            new FieldTemplate("Nome do evento", FieldType.TEXT, true, 40, "Ex: Aniversario da Ana", null, null),
                            new FieldTemplate("Data do evento", FieldType.TEXT, true, 20, "Ex: 12/10/2026", "^[0-9]{2}/[0-9]{2}/[0-9]{4}$", null),
                            new FieldTemplate("Mensagem", FieldType.TEXTAREA, false, 200, "Texto adicional", null, null)
                    )
            )
    );

    public List<NicheTemplate> listTemplates() {
        return templates;
    }

    public NicheTemplate requireTemplate(String templateId) {
        return templates.stream()
                .filter(template -> template.id().equals(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Template de nicho nao encontrado."));
    }
}
