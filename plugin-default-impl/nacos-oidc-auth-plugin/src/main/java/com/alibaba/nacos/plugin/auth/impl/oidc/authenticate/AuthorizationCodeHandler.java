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
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcProviderMetadataProvider;
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
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles OIDC Authorization Code flow for user login.
 *
 * @author WangzJi
 */
public class AuthorizationCodeHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationCodeHandler.class);
    
    private final OidcAuthPluginConfig config;
    
    private final OidcProviderMetadataProvider metadataProvider;
    
    private final JwtTokenValidator tokenValidator;
    
    private final OidcUserMapper userMapper;
    
    private final SecureRandom secureRandom;
    
    /**
     * State expiration time in milliseconds (10 minutes).
     */
    private static final long STATE_EXPIRATION_MS = 10 * 60 * 1000L;
    
    /**
     * Transaction cookie max-age in seconds (10 minutes).
     */
    public static final int TRANSACTION_COOKIE_MAX_AGE_SECONDS = 600;
    
    /**
     * HMAC algorithm for state signing.
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    /**
     * Hash algorithm for key derivation and state fingerprinting.
     */
    private static final String SHA256_ALGORITHM = "SHA-256";
    
    /**
     * Symmetric encryption algorithm for browser-bound transaction cookies.
     */
    private static final String AES_ALGORITHM = "AES";
    
    /**
     * Authenticated encryption transformation for transaction cookies.
     */
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    
    /**
     * AES-GCM initialization vector length in bytes.
     */
    private static final int GCM_IV_LENGTH_BYTES = 12;
    
    /**
     * AES-GCM authentication tag length in bits.
     */
    private static final int GCM_TAG_LENGTH_BITS = 128;
    
    /**
     * Domain-separation prefix for deriving the transaction cookie encryption key.
     */
    private static final String TX_ENCRYPTION_KEY_PREFIX = "nacos-oidc-tx-key-v1:";
    
    public AuthorizationCodeHandler(OidcAuthPluginConfig config,
        OidcProviderMetadataProvider metadataProvider, JwtTokenValidator tokenValidator,
        OidcUserMapper userMapper) {
        this(config, metadataProvider, tokenValidator, userMapper, new SecureRandom());
    }
    
    AuthorizationCodeHandler(OidcAuthPluginConfig config,
        OidcProviderMetadataProvider metadataProvider, JwtTokenValidator tokenValidator,
        OidcUserMapper userMapper, SecureRandom secureRandom) {
        this.config = config;
        this.metadataProvider = metadataProvider;
        this.tokenValidator = tokenValidator;
        this.userMapper = userMapper;
        this.secureRandom = secureRandom;
    }
    
    /**
     * Build the authorization URL for redirecting user to IdP.
     *
     * @param redirectUri callback URI after authentication
     * @return authorization URL
     * @throws AccessException if configuration is invalid
     */
    public String buildAuthorizationUrl(String redirectUri) throws AccessException {
        return createAuthorizationRequest(redirectUri).getAuthorizationUrl();
    }
    
    /**
     * Create an OIDC authorization request with PKCE (S256) and browser-bound transaction data.
     *
     * @param redirectUri callback URI after authentication
     * @return authorization request data containing URL, state, code verifier, and encrypted cookie
     * @throws AccessException if configuration is invalid
     */
    public AuthorizationRequestData createAuthorizationRequest(String redirectUri)
        throws AccessException {
        try {
            String authEndpoint = metadataProvider.getMetadata().getAuthorizationEndpoint();
            if (StringUtils.isBlank(authEndpoint)) {
                throw new AccessException("Authorization endpoint not configured");
            }
            
            // Generate nonce and PKCE code verifier for security
            String nonce = generateSecureToken();
            long expirationTime = System.currentTimeMillis() + STATE_EXPIRATION_MS;
            
            // Build self-contained signed state: base64(nonce.expTime.signature)
            // This eliminates the need for server-side state storage (cluster-friendly)
            String state = buildSignedState(nonce, expirationTime);
            CodeVerifier codeVerifier = new CodeVerifier(generateSecureToken());
            String txCookieValue =
                encryptTransactionCookie(state, codeVerifier.getValue(), expirationTime);
            
            // Build OIDC authentication request with PKCE S256
            AuthenticationRequest authRequest = new AuthenticationRequest.Builder(
                new ResponseType("code"),
                new Scope(config.getScope().split(" ")),
                new ClientID(config.getClientId()),
                URI.create(redirectUri))
                .endpointURI(URI.create(authEndpoint))
                .state(new State(state))
                .nonce(new Nonce(nonce))
                .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
                .build();
            
            String authUrl = authRequest.toURI().toString();
            LOGGER.debug("Built authorization URL: {}", authUrl);
            return new AuthorizationRequestData(
                authUrl, state, codeVerifier.getValue(), txCookieValue);
            
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
        return exchangeCodeForUserInternal(code, state, redirectUri, null, false);
    }
    
    /**
     * Exchange authorization code for tokens and authenticate user with browser-bound transaction
     * verification and PKCE code verifier submission.
     *
     * @param code              authorization code from IdP
     * @param state             state parameter for CSRF verification
     * @param redirectUri       the redirect URI used in the authorization request
     * @param transactionCookie encrypted browser-bound transaction cookie from login initiation
     * @return authenticated OidcUser
     * @throws AccessException if authentication or transaction verification fails
     */
    public OidcUser exchangeCodeForUser(String code, String state, String redirectUri,
        String transactionCookie) throws AccessException {
        return exchangeCodeForUserInternal(code, state, redirectUri, transactionCookie, true);
    }
    
    private OidcUser exchangeCodeForUserInternal(String code, String state, String redirectUri,
        String transactionCookie, boolean requireTransactionCookie) throws AccessException {
        try {
            // Verify and decode state (self-contained, no cache lookup needed)
            StateData stateData = verifyAndDecodeState(state);
            if (stateData == null) {
                throw new AccessException("Invalid or expired state parameter");
            }
            
            String codeVerifier = null;
            if (requireTransactionCookie) {
                if (StringUtils.isBlank(transactionCookie)) {
                    throw new AccessException("Missing login transaction cookie");
                }
                codeVerifier = verifyAndDecodeTransactionCookie(transactionCookie, state);
                if (codeVerifier == null) {
                    throw new AccessException("Invalid or expired login transaction");
                }
            }
            
            // Exchange code for tokens
            OIDCTokens tokens = exchangeCodeForTokens(code, redirectUri, codeVerifier);
            
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
                        + ". Set 'nacos.plugin.auth.oidc.strict-nonce-validation=false' "
                        + "if your IdP doesn't support nonce.");
                } else {
                    LOGGER.warn("{} - Strict validation disabled, allowing authentication. "
                        + "This reduces protection against replay attacks.", message);
                }
            } else if (!MessageDigest.isEqual(
                stateData.nonce.getBytes(StandardCharsets.UTF_8),
                tokenNonce.getBytes(StandardCharsets.UTF_8))) {
                String message = String.format("Nonce mismatch: expected %s, got %s",
                    stateData.nonce, tokenNonce);
                LOGGER.error("{} - Possible token replay attack detected", message);
                throw new AccessException(message);
            }
            
            // Map claims to user
            OidcUser user = userMapper.mapToUser(claims);
            user.setToken(tokens.getAccessToken().getValue());
            user.setIdToken(idTokenString);
            
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
     * @param code         authorization code
     * @param redirectUri  redirect URI
     * @param codeVerifier optional PKCE code verifier
     * @return OIDC tokens
     * @throws Exception if exchange fails
     */
    private OIDCTokens exchangeCodeForTokens(String code, String redirectUri, String codeVerifier)
        throws Exception {
        String tokenEndpoint = metadataProvider.getMetadata().getTokenEndpoint();
        if (StringUtils.isBlank(tokenEndpoint)) {
            throw new AccessException("Token endpoint not configured");
        }
        
        // Build token request
        AuthorizationCode authCode = new AuthorizationCode(code);
        AuthorizationGrant grant = StringUtils.isNotBlank(codeVerifier)
            ? new AuthorizationCodeGrant(authCode, URI.create(redirectUri),
                new CodeVerifier(codeVerifier))
            : new AuthorizationCodeGrant(authCode, URI.create(redirectUri));
        
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
     * Verify HMAC signature using constant-time comparison.
     *
     * @param payload   the original payload
     * @param signature the signature to verify
     * @return true if signature is valid
     */
    private boolean hmacVerify(String payload, String signature) {
        if (signature == null) {
            return false;
        }
        String expectedSignature = hmacSign(payload);
        return MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Encrypt and bind the state fingerprint, PKCE code verifier, and expiration time into an
     * opaque browser transaction cookie value using AES-GCM.
     */
    private String encryptTransactionCookie(String state, String codeVerifier,
        long expirationTime) {
        try {
            String stateFingerprint = sha256Base64Url(state);
            String plaintext = stateFingerprint + "." + codeVerifier + "." + expirationTime;
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, deriveTransactionKey(),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(stateFingerprint.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to protect login transaction cookie", e);
        }
    }
    
    /**
     * Decrypt and verify the browser-bound transaction cookie against the callback state.
     *
     * @param transactionCookie cookie value sent by browser
     * @param state             callback state parameter
     * @return PKCE code verifier if valid, null otherwise
     */
    private String verifyAndDecodeTransactionCookie(String transactionCookie, String state) {
        try {
            if (StringUtils.isBlank(transactionCookie) || StringUtils.isBlank(state)) {
                return null;
            }
            byte[] combined = Base64.getUrlDecoder().decode(transactionCookie);
            int minLength = GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8);
            if (combined.length <= minLength) {
                LOGGER.warn("Invalid login transaction cookie length");
                return null;
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);
            
            String expectedFingerprint = sha256Base64Url(state);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, deriveTransactionKey(),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            cipher.updateAAD(expectedFingerprint.getBytes(StandardCharsets.UTF_8));
            String plaintext =
                new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            String[] parts = plaintext.split("\\.");
            if (parts.length != 3) {
                LOGGER.warn("Invalid login transaction payload format");
                return null;
            }
            if (!MessageDigest.isEqual(
                expectedFingerprint.getBytes(StandardCharsets.UTF_8),
                parts[0].getBytes(StandardCharsets.UTF_8))) {
                LOGGER.warn("Login transaction state binding mismatch");
                return null;
            }
            String codeVerifier = parts[1];
            if (StringUtils.isBlank(codeVerifier)) {
                LOGGER.warn("Missing PKCE code verifier in login transaction");
                return null;
            }
            long expTime = Long.parseLong(parts[2]);
            if (System.currentTimeMillis() > expTime) {
                LOGGER.warn("Login transaction cookie has expired");
                return null;
            }
            return codeVerifier;
        } catch (Exception e) {
            LOGGER.warn("Failed to verify login transaction cookie: {}", e.getMessage());
            return null;
        }
    }
    
    private SecretKeySpec deriveTransactionKey() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
        byte[] keyBytes = digest.digest(
            (TX_ENCRYPTION_KEY_PREFIX + getSigningKey()).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }
    
    private String sha256Base64Url(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
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
        String endSessionEndpoint;
        try {
            endSessionEndpoint = metadataProvider.getMetadata().getEndSessionEndpoint();
        } catch (Exception e) {
            LOGGER.warn("Failed to discover OIDC logout endpoint: {}", e.getMessage());
            return null;
        }
        if (StringUtils.isBlank(endSessionEndpoint)) {
            return null;
        }
        
        StringBuilder logoutUrl = new StringBuilder(endSessionEndpoint);
        logoutUrl.append(OidcConstants.QUERY_STRING_SEPARATOR);
        
        if (StringUtils.isNotBlank(idToken)) {
            logoutUrl.append("id_token_hint=")
                .append(URLEncoder.encode(idToken, StandardCharsets.UTF_8));
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
     * Immutable authorization request bundle carrying PKCE and browser transaction cookie data.
     */
    public static final class AuthorizationRequestData {
        
        private final String authorizationUrl;
        
        private final String state;
        
        private final String codeVerifier;
        
        private final String transactionCookieValue;
        
        public AuthorizationRequestData(String authorizationUrl, String state, String codeVerifier,
            String transactionCookieValue) {
            this.authorizationUrl = authorizationUrl;
            this.state = state;
            this.codeVerifier = codeVerifier;
            this.transactionCookieValue = transactionCookieValue;
        }
        
        public String getAuthorizationUrl() {
            return authorizationUrl;
        }
        
        public String getState() {
            return state;
        }
        
        public String getCodeVerifier() {
            return codeVerifier;
        }
        
        public String getTransactionCookieValue() {
            return transactionCookieValue;
        }
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
