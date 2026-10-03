package br.com.nuvemcustomfields.config;

import br.com.nuvemcustomfields.controller.LocalHomologationController;
import br.com.nuvemcustomfields.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LocalHomologationRegistrationTest {
    @Configuration(proxyBeanMethods=false)
    @Import({LocalHomologationController.class,LocalHomologationGuard.class})
    static class EntryConfiguration {}
    private ApplicationContextRunner runner(){return new ApplicationContextRunner().withUserConfiguration(EntryConfiguration.class)
        .withBean(StoreRepository.class,()->mock(StoreRepository.class))
        .withPropertyValues("environment=LOCAL_HOMOLOG","local.homologation.enabled=true",
                "local.homologation.access-key=local-key-12345678901234567890","payments.efi.sandbox=true",
                "spring.datasource.url=jdbc:mysql://127.0.0.1:3307/store_homolog");}
    @Test void explicitProductionProfilesAlwaysExcludeEntry(){
        for(var profiles:new String[]{"docker","prod,local-homolog","production,local-homolog"})
            runner().withPropertyValues("spring.profiles.active="+profiles).run(context->assertThat(context).doesNotHaveBean(LocalHomologationController.class));
    }
    @Test void propertyCanDisableEntryEvenWithLocalProfile(){runner().withPropertyValues("spring.profiles.active=local-homolog","local.homologation.enabled=false")
        .run(context->assertThat(context).doesNotHaveBean(LocalHomologationController.class));}
}
