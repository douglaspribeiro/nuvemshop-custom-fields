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
            boolean configuredInApp,
            /** Versao que a loja realmente carrega. */
            String currentVersion,
            /** Versao enviada e ainda nao publicada; se maior que a current, o upload nao entrou. */
            String draftVersion
    ) {

        /** A API expoe active/testing/draft/legacy; so `active` carrega em loja normal. */
        public boolean loadsInProduction() {
            return "active".equalsIgnoreCase(status);
        }

        /**
         * Upload feito sem publicar: a loja segue na versao antiga. Foi a causa de um
         * ciclo inteiro de debug, por isso e destacado na tela.
         */
        public boolean hasUnpublishedDraft() {
            return draftVersion != null
                    && !draftVersion.isBlank()
                    && !draftVersion.equals(currentVersion);
        }
    }
}
