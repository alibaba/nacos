/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.auth.impl.oidc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.ErrorObject;
import com.nimbusds.oauth2.sdk.GeneralException;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.ResponseType.Value;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.http.ServletUtils;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.AuthenticationErrorResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest.Builder;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.UserInfoErrorResponse;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.UserInfoSuccessResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Open ID Connect Client for handling Open ID Connect authentication requests and responses.
 *
 * @author Roiocam
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
@Component
public class OIDCClient {
    
    private static final ResponseType RESPONSE_TYPE = new ResponseType(Value.CODE);
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OIDCClient.class);
    
    private final OIDCConfiguration config;
    
    public OIDCClient() {
        this.config = OIDCConfigurations.getConfiguration(OIDCConfigurations.getProvider());
    }
    
    /**
     * Create an Open ID Connect Authentication Request.
     *
     * @param callbackUrl Callback url for Authorization Server
     * @return AuthenticationRequest
     */
    public AuthenticationRequest createAuthenticationRequest(String callbackUrl) {
        // 生成随机 state 和 nonce，并存入 Session，防止 CSRF 和重放攻击
        State state = new State();
        Nonce nonce = new Nonce();
        OIDCProviderMetadata providerMetadata = getProviderMetadata();
        return new Builder(RESPONSE_TYPE,           // 授权码模式
                getScope(),                         // 请求 scope，例如 "openid profile email"
                getClientId(),                              // 客户端 ID
                URI.create(callbackUrl))                              // 重定向 URI
                .endpointURI(providerMetadata.getAuthorizationEndpointURI())         // 授权端点
                .state(state)                                           // 防 CSRF
                .nonce(nonce)                                           // 防重放
                .build();
    }
    
    /**
     * Extract the authorization code from the callback request.
     *
     * @param callbackRequest HttpServletRequest
     * @return AuthorizationCode
     */
    public AuthorizationCode getAuthorizationCode(HttpServletRequest callbackRequest) {
        LOGGER.debug("Retrieving authorization code from callback request's query parameters: {}",
                callbackRequest.getQueryString());
        AuthenticationResponse authResponse;
        try {
            HTTPRequest request = ServletUtils.createHTTPRequest(callbackRequest);
            authResponse = AuthenticationResponseParser.parse(request.getURL().toURI(), request.getQueryParameters());
        } catch (ParseException | URISyntaxException | IOException e) {
            throw new IllegalStateException("Error while parsing callback request", e);
        }
        if (authResponse instanceof AuthenticationErrorResponse) {
            ErrorObject error = ((AuthenticationErrorResponse) authResponse).getErrorObject();
            throw new IllegalStateException("Authentication request failed: " + error.toJSONObject());
        }
        AuthorizationCode authorizationCode = ((AuthenticationSuccessResponse) authResponse).getAuthorizationCode();
        LOGGER.debug("Authorization code: {}", authorizationCode.getValue());
        return authorizationCode;
    }
    
    /**
     * Get UserInfo from Authorization Server by exchange the authorization code.
     *
     * @param authorizationCode Authorization Code
     * @param callbackUrl       Callback URL
     * @return UserInfo
     */
    public UserInfo getUserInfo(AuthorizationCode authorizationCode, String callbackUrl) {
        OIDCProviderMetadata providerMetadata = getProviderMetadata();
        LOGGER.debug("Getting user info for authorization code");
        OIDCTokens oidcTokens = getOidcTokens(authorizationCode, callbackUrl);
        
        UserInfo userInfo;
        try {
            userInfo = new UserInfo(oidcTokens.getIDToken().getJWTClaimsSet());
        } catch (java.text.ParseException e) {
            throw new IllegalStateException("Parsing ID token failed", e);
        }
        if (((userInfo.getName() == null) && (userInfo.getPreferredUsername() == null))) {
            UserInfoResponse userInfoResponse = getUserInfoResponse(providerMetadata.getUserInfoEndpointURI(),
                    oidcTokens.getBearerAccessToken());
            if (userInfoResponse instanceof UserInfoErrorResponse) {
                ErrorObject errorObject = ((UserInfoErrorResponse) userInfoResponse).getErrorObject();
                if (errorObject == null || errorObject.getCode() == null) {
                    throw new IllegalStateException("UserInfo request failed: No error code returned "
                            + "(identity provider not reachable - check network proxy setting 'http.nonProxyHosts' in 'sonar.properties')");
                } else {
                    throw new IllegalStateException("UserInfo request failed: " + errorObject.toJSONObject());
                }
            }
            userInfo = ((UserInfoSuccessResponse) userInfoResponse).getUserInfo();
        }
        
        LOGGER.debug("User info: {}", userInfo.toJSONObject());
        return userInfo;
    }
    
    /**
     * Get OIDC Token from Authorization Server by exchange the authorization code.
     *
     * @param authorizationCode Authorization Code
     * @param callbackUrl       Callback URL
     * @return OIDCTokens
     */
    private OIDCTokens getOidcTokens(AuthorizationCode authorizationCode, String callbackUrl) {
        OIDCProviderMetadata providerMetadata = getProviderMetadata();
        TokenResponse tokenResponse = getTokenResponse(providerMetadata.getTokenEndpointURI(), authorizationCode,
                callbackUrl);
        if (tokenResponse instanceof TokenErrorResponse) {
            ErrorObject errorObject = ((TokenErrorResponse) tokenResponse).getErrorObject();
            if (errorObject == null || errorObject.getCode() == null) {
                throw new IllegalStateException("Token request failed: No error code returned "
                        + "(identity provider not reachable - check network proxy setting 'http.nonProxyHosts' in 'sonar.properties')");
            } else {
                throw new IllegalStateException("Token request failed: " + errorObject.toJSONObject());
            }
        }
        OIDCTokens oidcTokens = ((OIDCTokenResponse) tokenResponse).getOIDCTokens();
        if (isIdTokenSigned()) {
            validateIdToken(providerMetadata.getIssuer(), providerMetadata.getJWKSetURI(), oidcTokens.getIDToken());
        }
        return oidcTokens;
    }
    
    private TokenResponse getTokenResponse(URI tokenEndpointURI, AuthorizationCode authorizationCode,
            String callbackUrl) {
        try {
            TokenRequest request = new TokenRequest(tokenEndpointURI,
                    new ClientSecretBasic(getClientId(), getClientSecret()),
                    new AuthorizationCodeGrant(authorizationCode, new URI(callbackUrl)));
            HTTPResponse response = request.toHTTPRequest().send();
            LOGGER.debug("Token response content: {}", response.getContent());
            return OIDCTokenResponseParser.parse(response);
        } catch (URISyntaxException | ParseException e) {
            throw new IllegalStateException("Retrieving access token failed", e);
        } catch (IOException e) {
            throw new IllegalStateException("Retrieving access token failed: "
                    + "Identity provider not reachable - check network proxy setting 'http.nonProxyHosts' in 'sonar.properties'");
        }
    }
    
    private void validateIdToken(Issuer issuer, URI jwkSetURI, JWT idToken) {
        LOGGER.debug("Validating ID token with {} and key set from from {}", getIdTokenSignAlgorithm(), jwkSetURI);
        try {
            IDTokenValidator validator = createValidator(issuer, jwkSetURI.toURL());
            validator.validate(idToken, null);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid JWK set URL", e);
        } catch (BadJOSEException e) {
            throw new IllegalStateException("Invalid ID token", e);
        } catch (JOSEException e) {
            throw new IllegalStateException("Validating ID token failed", e);
        }
    }
    
    private IDTokenValidator createValidator(Issuer issuer, URL jwkSetUrl) {
        return new IDTokenValidator(issuer, getClientId(), getIdTokenSignAlgorithm(), jwkSetUrl);
    }
    
    private UserInfoResponse getUserInfoResponse(URI userInfoEndpointURI, BearerAccessToken accessToken) {
        LOGGER.debug("Retrieving user info from {}", userInfoEndpointURI);
        try {
            UserInfoRequest request = new UserInfoRequest(userInfoEndpointURI, accessToken);
            HTTPResponse response = request.toHTTPRequest().send();
            LOGGER.debug("UserInfo response content: {}", response.getContent());
            return UserInfoResponse.parse(response);
        } catch (ParseException e) {
            throw new IllegalStateException("Retrieving user information failed", e);
        } catch (IOException e) {
            throw new IllegalStateException("Retrieving user information failed: "
                    + "Identity provider not reachable - check network proxy setting 'http.nonProxyHosts' in 'sonar.properties'");
        }
    }
    
    private OIDCProviderMetadata getProviderMetadata() {
        LOGGER.debug("Retrieving provider metadata from {}", config.getIssuerUri());
        try {
            return OIDCProviderMetadata.resolve(new Issuer(config.getIssuerUri()));
        } catch (IOException | GeneralException e) {
            if (e instanceof GeneralException && e.getMessage().contains("issuer doesn't match")) {
                throw new IllegalStateException("Retrieving OpenID Connect provider metadata failed: "
                        + "Issuer URL in provider metadata doesn't match the issuer URI specified in plugin configuration");
            } else {
                throw new IllegalStateException("Retrieving OpenID Connect provider metadata failed", e);
            }
        }
    }
    
    private Scope getScope() {
        return Scope.parse(config.getScope());
    }
    
    private ClientID getClientId() {
        return new ClientID(config.getClientId());
    }
    
    private Secret getClientSecret() {
        String secret = config.getClientSecret();
        return secret == null ? new Secret("") : new Secret(secret);
    }
    
    private boolean isIdTokenSigned() {
        return config.getIdTokenSignAlgorithm() != null;
    }
    
    private JWSAlgorithm getIdTokenSignAlgorithm() {
        String algorithmName = config.getIdTokenSignAlgorithm();
        return algorithmName == null ? null : new JWSAlgorithm(algorithmName);
    }
    
}
