package br.com.nuvemcustomfields.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Ordered named colors, stored in options_text for COLOR_SELECT only. */
public final class ColorOptions {
    public record Option(String label, String color) {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private ColorOptions() {}
    public static List<Option> parse(String value) {
        try {
            List<Option> options = JSON.readValue(value == null ? "[]" : value, new TypeReference<List<Option>>() {});
            if (options == null || options.isEmpty() || options.size() > 25) throw new IllegalArgumentException();
            Set<String> names = new HashSet<>();
            List<Option> result = new ArrayList<>();
            for (var option : options) {
                String label = option.label() == null ? "" : option.label().strip();
                if (label.isEmpty() || label.length() > 100 || label.chars().anyMatch(Character::isISOControl)
                        || !names.add(label.toLowerCase(Locale.ROOT)) || option.color() == null
                        || !option.color().matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
                result.add(new Option(label, option.color().toUpperCase(Locale.ROOT)));
            }
            return List.copyOf(result);
        } catch (Exception error) { throw new IllegalArgumentException("color.options.invalid", error); }
    }
    public static String normalize(String value) {
        try { return JSON.writeValueAsString(parse(value)); }
        catch (Exception error) { throw new IllegalArgumentException("color.options.invalid", error); }
    }
}
