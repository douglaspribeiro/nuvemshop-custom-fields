package br.com.nuvemcustomfields.dto;

import java.util.List;

/**
 * Retrato do que a API da Nuvemshop diz estar associado a loja, cruzado com o que o app
 * tem configurado. Existe para nao depender de leitura de log quando um script nao carrega.
 */
public record ScriptDiagnostics(
        List<Long> configuredIds,
        List<InstalledScript> scripts,
        /** Configurados no app mas ausentes na loja: o motivo tipico de campo nao aparecer. */
        List<Long> missingIds,
        String error
) {

    public static ScriptDiagnostics failed(List<Long> configuredIds, String error) {
        return new ScriptDiagnostics(configuredIds, List.of(), configuredIds, error);
    }

    public boolean healthy() {
        return error == null && missingIds.isEmpty() && !configuredIds.isEmpty();
    }

    public record InstalledScript(
            Long id,
            String name,
            String status,
            String location,
            String event,
            boolean autoInstall,
            String src,
            /** Se este id esta entre os configurados no app; falso indica script orfao. */
            boolean configuredInApp
    ) {

        /** A API expoe active/testing/draft/legacy; so `active` carrega em loja normal. */
        public boolean loadsInProduction() {
            return "active".equalsIgnoreCase(status);
        }
    }
}
