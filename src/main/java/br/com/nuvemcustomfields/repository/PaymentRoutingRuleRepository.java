package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.PaymentRoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentRoutingRuleRepository extends JpaRepository<PaymentRoutingRule, Long> {
    Optional<PaymentRoutingRule> findByCountryCodeIgnoreCase(String countryCode);
}
