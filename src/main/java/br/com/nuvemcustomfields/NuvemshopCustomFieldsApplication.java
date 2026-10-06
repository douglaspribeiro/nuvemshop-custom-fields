package br.com.nuvemcustomfields;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NuvemshopCustomFieldsApplication {

    public static void main(String[] args) {
        SpringApplication.run(NuvemshopCustomFieldsApplication.class, args);
    }
}
