package br.com.nuvemcustomfields.entity;

public enum PlanType {
    FREE("FREE", true, false),
    FREE_GRATIS("Plano Grátis", false, false),
    PREMIUM("Essencial", true, true),
    PREMIUM_PLUS("Pro", true, true);

    private final String displayName;
    private final boolean selfService;
    private final boolean billable;

    PlanType(String displayName, boolean selfService, boolean billable) {
        this.displayName = displayName;
        this.selfService = selfService;
        this.billable = billable;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayTitle() {
        return displayName.startsWith("Plano ") ? displayName : "Plano " + displayName;
    }

    public boolean isSelfService() {
        return selfService;
    }

    public boolean isBillable() {
        return billable;
    }
}
