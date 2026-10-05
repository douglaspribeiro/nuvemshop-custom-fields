package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.dto.ImageOption;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class ImageOptions {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ImageOptions() {}
    public static List<ImageOption> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<ImageOption> options = JSON.readValue(json, new TypeReference<List<ImageOption>>() {});
            if (options == null || options.isEmpty() || options.size() > 25) throw new IllegalArgumentException();
            Set<String> labels = new HashSet<>();
            Set<String> ids = new HashSet<>();
            List<ImageOption> result = new ArrayList<>();
            for (var option : options) {
                String label = option.label() == null ? "" : option.label().strip();
                UUID.fromString(option.id());
                if (label.isEmpty() || label.length() > 100 || label.contains("\n") || label.contains("\r")
                        || !labels.add(label.toLowerCase(Locale.ROOT)) || !ids.add(option.id())) throw new IllegalArgumentException();
                result.add(new ImageOption(option.id(), label));
            }
            return List.copyOf(result);
        } catch (Exception e) { throw new IllegalArgumentException("image.options.invalid", e); }
    }
    public static String serialize(List<ImageOption> options) {
        try { return JSON.writeValueAsString(options); }
        catch (Exception e) { throw new IllegalArgumentException("image.options.invalid", e); }
    }
}
