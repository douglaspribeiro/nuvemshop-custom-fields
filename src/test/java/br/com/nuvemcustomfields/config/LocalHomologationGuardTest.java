package br.com.nuvemcustomfields.config;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class LocalHomologationGuardTest {
    private MockEnvironment environment(){return new MockEnvironment().withProperty("environment","LOCAL_HOMOLOG")
        .withProperty("local.homologation.access-key","local-key-12345678901234567890")
        .withProperty("spring.datasource.url","jdbc:mysql://127.0.0.1:3307/my_test_homolog?useSSL=false")
        .withProperty("payments.efi.sandbox","true");}
    @Test void acceptsOnlyCorrectKeyAndLocalDatabase(){var guard=new LocalHomologationGuard(environment());assertThat(guard.accepts("wrong")).isFalse();assertThat(guard.accepts(null)).isFalse();assertThat(guard.accepts("local-key-12345678901234567890")).isTrue();}
    @Test void rejectsProductionDatabaseAndProviders(){
        for(var value:new String[]{"jdbc:mysql://137.131.163.33:3306/app_custom_fields","jdbc:mysql://127.0.0.1:3307/app_custom_fields","jdbc:h2:file:/tmp/store_homolog"})
            assertThatThrownBy(()->new LocalHomologationGuard(environment().withProperty("spring.datasource.url",value))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new LocalHomologationGuard(environment().withProperty("payments.efi.sandbox","false"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new LocalHomologationGuard(environment().withProperty("environment","DOCKER"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new LocalHomologationGuard(environment().withProperty("local.homologation.access-key","short"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new LocalHomologationGuard(environment().withProperty("payments.paddle.enabled","true"))).isInstanceOf(IllegalStateException.class);
    }
}
