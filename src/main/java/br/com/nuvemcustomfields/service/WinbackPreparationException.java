package br.com.nuvemcustomfields.service;

/** A known dispatch prerequisite, with a message safe to display in the backoffice. */
public class WinbackPreparationException extends IllegalStateException {
    public WinbackPreparationException(String message) {
        super(message);
    }
}
