package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.FieldForm;
import br.com.nuvemcustomfields.dto.NicheTemplate;
import br.com.nuvemcustomfields.entity.PersonalizationField;
import br.com.nuvemcustomfields.entity.PersonalizationRule;
import br.com.nuvemcustomfields.entity.Store;
import br.com.nuvemcustomfields.repository.PersonalizationFieldRepository;
import br.com.nuvemcustomfields.repository.PersonalizationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class PersonalizationAdminService {

    private final PersonalizationRuleRepository ruleRepository;
    private final PersonalizationFieldRepository fieldRepository;

    public PersonalizationAdminService(
            PersonalizationRuleRepository ruleRepository,
            PersonalizationFieldRepository fieldRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.fieldRepository = fieldRepository;
    }

    public List<PersonalizationRule> listRules(Long storeId) {
        return ruleRepository.findByStoreIdOrderByProductNameAsc(storeId);
    }

    public boolean hasRule(Long storeId, Long productId) {
        return ruleRepository.findByStoreIdAndProductId(storeId, productId).isPresent();
    }

    @Transactional
    public PersonalizationRule ensureRule(Long storeId, Long productId, String productName) {
        PersonalizationRule rule = ruleRepository.findByStoreIdAndProductId(storeId, productId)
                .orElseGet(PersonalizationRule::new);
        rule.setStoreId(storeId);
        rule.setProductId(productId);
        if (productName != null && !productName.isBlank()) {
            rule.setProductName(productName.strip());
        }
        return ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public PersonalizationRule requireRuleWithFields(Long storeId, Long productId) {
        PersonalizationRule rule = ruleRepository.findWithFieldsByStoreIdAndProductId(storeId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Regra de personalizacao nao encontrada."));
        rule.getFields().sort(Comparator.comparing(PersonalizationField::getSortOrder).thenComparing(PersonalizationField::getId));
        return rule;
    }

    @Transactional
    public void addField(Long storeId, Long productId, FieldForm form) {
        PersonalizationRule rule = ruleRepository.findByStoreIdAndProductId(storeId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Regra de personalizacao nao encontrada."));
        PersonalizationField field = new PersonalizationField();
        applyForm(field, form);
        field.setRule(rule);
        fieldRepository.save(field);
    }

    @Transactional
    public void updateField(Long storeId, Long productId, Long fieldId, FieldForm form) {
        PersonalizationRule rule = requireRuleWithFields(storeId, productId);
        PersonalizationField field = rule.getFields().stream()
                .filter(candidate -> candidate.getId().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Campo nao encontrado para esta loja/produto."));
        applyForm(field, form);
        fieldRepository.save(field);
    }

    @Transactional
    public void deleteField(Long storeId, Long productId, Long fieldId) {
        int deleted = fieldRepository.deleteByIdAndStoreIdAndProductId(fieldId, storeId, productId);
        if (deleted == 0) {
            throw new IllegalArgumentException("Campo nao encontrado para esta loja/produto.");
        }
    }

    @Transactional
    public void deleteRule(Long storeId, Long productId) {
        ruleRepository.deleteByStoreIdAndProductId(storeId, productId);
    }

    @Transactional
    public int applyTemplate(Store store, Long productId, String productName, NicheTemplate template, PlanLimitService planLimitService) {
        PersonalizationRule rule = ensureRule(store.getStoreId(), productId, productName);
        int created = 0;
        int sortOrder = 0;
        for (var fieldTemplate : template.fields()) {
            if (!planLimitService.canAddField(store, rule.getId())) {
                break;
            }
            PersonalizationField field = new PersonalizationField();
            applyForm(field, fieldTemplate.toForm(sortOrder++));
            field.setRule(rule);
            fieldRepository.save(field);
            created++;
        }
        return created;
    }

    private void applyForm(PersonalizationField field, FieldForm form) {
        field.setLabel(form.getLabel().strip());
        field.setFieldType(form.getFieldType());
        field.setRequired(form.isRequired());
        field.setMaxLength(form.getMaxLength());
        field.setPlaceholder(form.getPlaceholder() == null || form.getPlaceholder().isBlank() ? null : form.getPlaceholder().strip());
        field.setValidationPattern(form.getValidationPattern() == null || form.getValidationPattern().isBlank()
                ? null
                : form.getValidationPattern().strip());
        field.setOptionsText(form.getOptionsText() == null || form.getOptionsText().isBlank()
                ? null
                : form.getOptionsText().strip());
        field.setSortOrder(form.getSortOrder() == null ? 0 : form.getSortOrder());
    }
}
