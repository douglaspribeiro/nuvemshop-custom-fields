package br.com.nuvemcustomfields.dto;

import java.util.List;

public record NicheTemplate(String id, String name, String description, List<FieldTemplate> fields) {
}
