/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.oidc.authenticate;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import com.alibaba.nacos.plugin.auth.impl.oidc.token.JwtTokenValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ResponseType;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles OIDC Authorization Code flow for user login.
 *
 * @author WangzJi
 */
public class AuthorizationCodeHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationCodeHandler.class);
    
    private static volatile AuthorizationCodeHandler instance;
    
    private final OidcAuthConfig config;
    
    private final JwtTokenValidator tokenValidator;
    
    private final OidcUserMapper userMapper;
    
    private final SecureRandom secureRandom;
    
    /**
     * State expiration time in milliseconds (10 minutes).
     */
    private static final long STATE_EXPIRATION_MS = 10 * 60 * 1000L;
    
    /**
     * HMAC algorithm for state signing.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    private AuthorizationCodeHandler() {
        this.config = OidcAuthConfig.getInstance();
        this.tokenValidator = JwtTokenValidator.getInstance();
        this.userMapper = OidcUserMapper.getInstance();
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Get singleton instance.
     *
     * @return AuthorizationCodeHandler instance
     */
    public static AuthorizationCodeHandler getInstance() {
        if (instance == null) {
            synchronized (AuthorizationCodeHandler.class) {
                if (instance == null) {
                    instance = new AuthorizationCodeHandler();
                }
            }
        }
        return instance;
    }
    
    /**
     * Build the authorization URL for redirecting user to IdP.
     *
     * @param redirectUri callback URI after authentication
     * @return authorization URL
     * @throws AccessException if configuration is invalid
     */
    public String buildAuthorizationUrl(String redirectUri) throws AccessException {
        try {
            String authEndpoint = config.getAuthorizationEndpoint();
            if (StringUtils.isBlank(authEndpoint)) {
                throw new AccessException("Authorization endpoint not configured");
            }
            
            // Generate nonce for security
            String nonce = generateSecureToken();
            long expirationTime = System.currentTimeMillis() + STATE_EXPIRATION_MS;
            
            // Build self-contained signed state: base64(nonce.expTime.signature)
            // This eliminates the need for server-side state storage (cluster-friendly)
            String state = buildSignedState(nonce, expirationTime);
            
            // Build OIDC authentication request
            AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope(config.getScope().split(" ")),
                new ClientID(config.getClientId()),
                URI.create(redirectUri))
                .endpointURI(URI.create(authEndpoint))
                .state(new State(state))
                .nonce(new Nonce(nonce))
                .build();
            
            String authUrl = authRequest.toURI().toString();
            LOGGER.debug("Built authorization URL: {}", authUrl);
            return authUrl;
            
        } catch (AccessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to build authorization URL", e);
            throw new AccessException("Failed to initiate login: " + e.getMessage());
        }
    }
    
    /**
     * Exchange authorization code for tokens and authenticate user.
     *
     * @param code        authorization code from IdP
     * @param state       state parameter for CSRF verification
     * @param redirectUri the redirect URI used in the authorization request
     * @return authenticated OidcUser
     * @throws AccessException if authentication fails
     */
    public OidcUser exchangeCodeForUser(String code, String state, String redirectUri)
        throws AccessException {
        try {
            // Verify and decode state (self-contained, no cache lookup needed)
            StateData stateData = verifyAndDecodeState(state);
            if (stateData == null) {
                throw new AccessException("Invalid or expired state parameter");
            }
            
            // Exchange code for tokens
            OIDCTokens tokens = exchangeCodeForTokens(code, redirectUri);
            
            // Validate ID token
            String idTokenString = tokens.getIDTokenString();
            JWTClaimsSet claims = tokenValidator.validate(idTokenString);
            
            // Verify nonce matches (protects against token replay attacks)
            String tokenNonce = (String) claims.getClaim("nonce");
            
            if (tokenNonce == null) {
                String message = "Nonce not present in ID token";
                if (config.isStrictNonceValidation()) {
                    LOGGER.error("{} - Strict validation enabled, rejecting authentication",
                        message);
                    throw new AccessException(message
                        + ". Set 'nacos.core.auth.plugin.oidc.strict-nonce-validation=false' "
                        + "if your IdP doesn't support nonce.");
                } else {
                    LOGGER.warn("{} - Strict validation disabled, allowing authentication. "
                        + "This reduces protection against replay attacks.", message);
                }
            } else if (!stateData.nonce.equals(tokenNonce)) {
                String message = String.format("Nonce mismatch: expected %s, got %s",
                    stateData.nonce, tokenNonce);
                LOGGER.error("{} - Possible token replay attack detected", message);
                throw new AccessException(message);
            }
            
            // Map claims to user
            OidcUser user = userMapper.mapToUser(claims);
            user.setToken(tokens.getAccessToken().getValue());
            
            LOGGER.info("User authenticated via authorization code: {}", user.getUsername());
            return user;
            
        } catch (AccessException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to exchange code for tokens", e);
            throw new AccessException("Authentication failed: " + e.getMessage());
        }
    }
    
    /**
     * Exchange authorization code for OIDC tokens.
     *
     * @param code        authorization code
     * @param redirectUri redirect URI
     * @return OIDC tokens
     * @throws Exception if exchange fails
     */
    private OIDCTokens exchangeCodeForTokens(String code, String redirectUri) throws Exception {
        String tokenEndpoint = config.getTokenEndpoint();
        if (StringUtils.isBlank(tokenEndpoint)) {
            throw new AccessException("Token endpoint not configured");
        }
        
        // Build token request
        AuthorizationCode authCode = new AuthorizationCode(code);
        AuthorizationGrant grant = new AuthorizationCodeGrant(authCode, URI.create(redirectUri));
        
        // Client authentication
        ClientAuthentication clientAuth = new ClientSecretBasic(
            new ClientID(config.getClientId()),
            new Secret(config.getClientSecret()));
        
        // Send token request
        TokenRequest tokenRequest = new TokenRequest(
            URI.create(tokenEndpoint),
            clientAuth,
            grant);
        
        TokenResponse tokenResponse =
            OIDCTokenResponseParser.parse(tokenRequest.toHTTPRequest().send());
        
        if (!tokenResponse.indicatesSuccess()) {
            String error = tokenResponse.toErrorResponse().getErrorObject().getDescription();
            LOGGER.error("Token exchange failed: {}", error);
            throw new AccessException("Token exchange failed: " + error);
        }
        
        OIDCTokenResponse oidcResponse = (OIDCTokenResponse) tokenResponse.toSuccessResponse();
        return oidcResponse.getOIDCTokens();
    }
    
    /**
     * Generate a secure random token for state/nonce.
     *
     * @return base64-encoded random token
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Build a signed self-contained state parameter.
     * Format: base64(nonce.expirationTime.signature)
     * This eliminates the need for server-side state storage (cluster-friendly).
     *
     * @param nonce          the nonce value
     * @param expirationTime the expiration timestamp
     * @return signed state string
     */
    private String buildSignedState(String nonce, long expirationTime) {
        String payload = nonce + "." + expirationTime;
        String signature = hmacSign(payload);
        String stateContent = payload + "." + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            stateContent.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Verify and decode a signed state parameter.
     *
     * @param state the state parameter from callback
     * @return StateData if valid, null otherwise
     */
    private StateData verifyAndDecodeState(String state) {
        try {
            String decoded =
                new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 3) {
                LOGGER.warn("Invalid state format: expected 3 parts, got {}", parts.length);
                return null;
            }
            
            String nonce = parts[0];
            long expTime = Long.parseLong(parts[1]);
            String signature = parts[2];
            
            // Verify signature
            String payload = nonce + "." + expTime;
            if (!hmacVerify(payload, signature)) {
                LOGGER.warn("State signature verification failed");
                return null;
            }
            
            // Verify expiration time
            if (System.currentTimeMillis() > expTime) {
                LOGGER.warn("State has expired");
                return null;
            }
            
            return new StateData(nonce, expTime);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid expiration time in state: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid base64 encoding in state: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to decode state: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Sign a payload using HMAC-SHA256.
     *
     * @param payload the payload to sign
     * @return base64-encoded signature
     */
    private String hmacSign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                getSigningKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign payload", e);
        }
    }
    
    /**
     * Verify HMAC signature.
     *
     * @param payload   the original payload
     * @param signature the signature to verify
     * @return true if signature is valid
     */
    private boolean hmacVerify(String payload, String signature) {
        String expectedSignature = hmacSign(payload);
        return expectedSignature.equals(signature);
    }
    
    /**
     * Get the signing key for HMAC operations.
     * Uses client secret as the signing key.
     *
     * @return signing key
     */
    private String getSigningKey() {
        String clientSecret = config.getClientSecret();
        if (StringUtils.isBlank(clientSecret)) {
            throw new IllegalStateException("Client secret is required for state signing");
        }
        return clientSecret;
    }
    
    /**
     * Build logout URL for RP-initiated logout.
     *
     * @param idToken     ID token for logout hint
     * @param redirectUri post-logout redirect URI
     * @return logout URL or null if not supported
     */
    public String buildLogoutUrl(String idToken, String redirectUri) {
        String endSessionEndpoint = config.getEndSessionEndpoint();
        if (StringUtils.isBlank(endSessionEndpoint)) {
            return null;
        }
        
        StringBuilder logoutUrl = new StringBuilder(endSessionEndpoint);
        logoutUrl.append(OidcConstants.QUERY_STRING_SEPARATOR);
        
        if (StringUtils.isNotBlank(idToken)) {
            logoutUrl.append("id_token_hint=").append(idToken);
        }
        
        if (StringUtils.isNotBlank(redirectUri)) {
            char lastChar = logoutUrl.charAt(logoutUrl.length() - 1);
            if (lastChar != OidcConstants.QUERY_STRING_SEPARATOR.charAt(0)) {
                logoutUrl.append("&");
            }
            logoutUrl.append("post_logout_redirect_uri=").append(redirectUri);
        }
        
        logoutUrl.append("&client_id=").append(config.getClientId());
        
        return logoutUrl.toString();
    }
    
    /**
     * State data for CSRF protection.
     */
    private static class StateData {
        
        final String nonce;
        
        final long expirationTime;
        
        StateData(String nonce, long expirationTime) {
            this.nonce = nonce;
            this.expirationTime = expirationTime;
        }
    }
}
