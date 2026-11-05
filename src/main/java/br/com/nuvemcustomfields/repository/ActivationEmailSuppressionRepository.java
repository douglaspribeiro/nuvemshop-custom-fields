package br.com.nuvemcustomfields.repository;

import br.com.nuvemcustomfields.entity.ActivationEmailSuppression;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationEmailSuppressionRepository extends JpaRepository<ActivationEmailSuppression, Long> { }
