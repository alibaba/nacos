package com.alibaba.nacos.console.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The response including OIDC token returned from the oidp.
 *
 * @author Kicey
 */
public class OidcTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("exprie_in")
    private String expireIn;
    
    @JsonProperty("expires_in")
    private String expiresIn;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    private String scope;
    
    @JsonProperty("id_token")
    private String idToken;
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public String getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(String expiresIn) {
        this.expiresIn = expiresIn;
    }
    
    public String getExpireIn() {
        return expireIn;
    }
    
    public void setExpireIn(String expireIn) {
        this.expireIn = expireIn;
    }
    
    public String getTokenType() {
        return tokenType;
    }
    
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
    }
    
    public String getIdToken() {
        return idToken;
    }
    
    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
