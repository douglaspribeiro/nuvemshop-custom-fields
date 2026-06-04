package br.com.nuvemcustomfields.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NuvemshopTokenResponse(
        @JsonAlias("access_token") String accessToken,
        String scope,
        @JsonAlias({"user_id", "store_id"}) Long storeId
) {
}
