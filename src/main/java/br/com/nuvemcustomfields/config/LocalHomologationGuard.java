package br.com.nuvemcustomfields.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;

/** Fail-closed: nunca usar este atalho de sessão contra o banco/provedor de produção. */
@Component
@Profile("local-homolog & !prod & !production")
@ConditionalOnProperty(name="local.homologation.enabled",havingValue="true")
public class LocalHomologationGuard {
    public static final long STORE_ID=990000000001L;
    public static final String STORE_TOKEN="local-homologation-fixture-not-an-oauth-token";
    private final String accessKey;
    public LocalHomologationGuard(Environment env){
        accessKey=env.getProperty("local.homologation.access-key","");
        String url=env.getProperty("spring.datasource.url","");
        boolean databaseSafe=false;
        try{
            if(url.startsWith("jdbc:mysql:")){
                URI db=URI.create(url.substring(5));
                databaseSafe= "127.0.0.1".equals(db.getHost()) && db.getPath()!=null && db.getPath().matches("/[A-Za-z0-9_]+_homolog");
            }
            // Apenas banco em memória, útil para testes automatizados deste perfil.
            else databaseSafe=url.startsWith("jdbc:h2:mem:local_homolog;");
        }catch(IllegalArgumentException ignored){ }
        if(!"LOCAL_HOMOLOG".equals(env.getProperty("environment")) || accessKey.length()<24 || accessKey.startsWith("SUBSTITUA") || !databaseSafe
                || !env.getProperty("payments.efi.sandbox",Boolean.class,false)
                || env.getProperty("payments.mercado-pago.enabled",Boolean.class,false)
                || env.getProperty("payments.paddle.enabled",Boolean.class,false)
                || env.getProperty("payments.creem.enabled",Boolean.class,false)
                || env.getProperty("nuvemshop.billing.enabled",Boolean.class,false))
            throw new IllegalStateException("Homologação local exige ambiente LOCAL_HOMOLOG, chave de pelo menos 24 caracteres, banco MySQL em 127.0.0.1 com nome terminado em _homolog e somente pagamentos Efí sandbox.");
    }
    public boolean accepts(String candidate){
        return candidate!=null && java.security.MessageDigest.isEqual(accessKey.getBytes(java.nio.charset.StandardCharsets.UTF_8),candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
