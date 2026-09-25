package br.com.nuvemcustomfields.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerPhoneTest {

    @Test
    void sendsOnlyAreaCodeAndPhoneNumberToEfi() {
        assertThat(AdminController.normalizeEfiPhone("(11) 99999-9999")).isEqualTo("11999999999");
        assertThat(AdminController.normalizeEfiPhone("(11) 3333-4444")).isEqualTo("1133334444");
        assertThat(AdminController.normalizeEfiPhone("+55 (11) 99999-9999")).isEqualTo("11999999999");
        assertThat(AdminController.normalizeEfiPhone("55 11 3333-4444")).isEqualTo("1133334444");
    }
}
