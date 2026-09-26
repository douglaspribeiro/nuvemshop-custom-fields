package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentCatalogPriceRepository extends JpaRepository<PaymentCatalogPrice, Long> {
    Optional<PaymentCatalogPrice> findByProviderAndEnvironmentAndCountryCodeIgnoreCaseAndPlan(
            PaymentProviderType provider, PaymentEnvironment environment, String countryCode, PlanType plan);
    List<PaymentCatalogPrice> findByProviderAndEnvironmentAndCountryCodeIgnoreCaseOrderByPlan(
            PaymentProviderType provider, PaymentEnvironment environment, String countryCode);
    List<PaymentCatalogPrice> findAllByOrderByCountryCodeAscPlanAsc();
}
