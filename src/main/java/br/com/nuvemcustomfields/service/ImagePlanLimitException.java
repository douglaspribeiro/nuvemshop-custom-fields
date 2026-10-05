package br.com.nuvemcustomfields.service;

public class ImagePlanLimitException extends IllegalArgumentException {
    private final Object[] arguments;
    public ImagePlanLimitException(String messageKey, Object... arguments) {
        super(messageKey); this.arguments = arguments;
    }
    public Object[] arguments() { return arguments; }
}
