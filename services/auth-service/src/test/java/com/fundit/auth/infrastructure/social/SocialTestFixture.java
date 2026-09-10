package com.fundit.auth.infrastructure.social;

final class SocialTestFixture {

    private SocialTestFixture() {
    }

    static OAuthProperties.Provider config(String tokenUri, String userInfoUri) {
        var config = new OAuthProperties.Provider();
        config.setClientId("test-client-id");
        config.setClientSecret("test-client-secret");
        config.setRedirectUri("https://app.fundit.kr/oauth");
        config.setTokenUri(tokenUri);
        config.setUserInfoUri(userInfoUri);
        return config;
    }
}
