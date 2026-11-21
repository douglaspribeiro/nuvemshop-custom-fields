package br.com.nuvemcustomfields.service;

import br.com.nuvemcustomfields.entity.MerchantInterfacePreference.Choice;
import br.com.nuvemcustomfields.properties.FeatureAccessProperties;
import br.com.nuvemcustomfields.repository.MerchantInterfacePreferenceRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantInterfaceService {
    private final FeatureAccessProperties features;
    private final MerchantInterfacePreferenceRepository preferences;

    public MerchantInterfaceService(FeatureAccessProperties features, MerchantInterfacePreferenceRepository preferences) {
        this.features = features;
        this.preferences = preferences;
    }

    public boolean isEligible(Long storeId) {
        return features.allows(storeId, FeatureAccessProperties.Feature.MERCHANT_INTERFACE_PREVIEW);
    }

    public Optional<Choice> getChoice(long storeId) {
        return preferences.findChoice(storeId);
    }

    @Transactional
    public boolean choose(long storeId, Choice choice) {
        if (!isEligible(storeId)) return false;
        preferences.setChoice(storeId, choice.name());
        return true;
    }
}
