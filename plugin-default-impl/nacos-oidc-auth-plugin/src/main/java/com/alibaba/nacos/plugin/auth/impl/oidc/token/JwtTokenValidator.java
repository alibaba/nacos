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

package com.alibaba.nacos.plugin.auth.impl.oidc.token;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT Token validator for OAuth2/OIDC tokens.
 *
 * @author WangzJi
 */
public class JwtTokenValidator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenValidator.class);
    
    private static volatile JwtTokenValidator instance;
    
    private final OidcAuthConfig config;
    
    private final JwksProvider jwksProvider;
    
    private volatile ConfigurableJWTProcessor<SecurityContext> jwtProcessor;
    
    /**
     * Required claims for validation.
     */
    private static final Set<String> REQUIRED_CLAIMS =
        new HashSet<>(Arrays.asList("sub", "iss", "exp", "iat"));
    
    /**
     * Supported Algorithms for OIDC.
     */
    private static final Set<JWSAlgorithm> SUPPORTED_ALGORITHMS = new HashSet<>(Arrays.asList(
        JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
        JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
        JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512));
    
    private JwtTokenValidator() {
        this.config = OidcAuthConfig.getInstance();
        this.jwksProvider = JwksProvider.getInstance();
    }
    
    /**
     * Get singleton instance.
     *
     * @return JwtTokenValidator instance
     */
    public static JwtTokenValidator getInstance() {
        if (instance == null) {
            synchronized (JwtTokenValidator.class) {
                if (instance == null) {
                    instance = new JwtTokenValidator();
                }
            }
        }
        return instance;
    }
    
    /**
     * Validate a JWT token and return the claims.
     *
     * @param token JWT token string
     * @return validated JWT claims
     * @throws AccessException if validation fails
     */
    public JWTClaimsSet validate(String token) throws AccessException {
        if (StringUtils.isBlank(token)) {
            throw new AccessException("Token is empty");
        }
        
        try {
            // Ensure processor is initialized (lazy init)
            ConfigurableJWTProcessor<SecurityContext> processor = getJwtProcessor();
            
            // Process and validate the token (Parsing also happens inside process but we parse handled inside)
            // Note: process(String) parses it.
            JWTClaimsSet claims = processor.process(token, null);
            
            // Additional validation
            validateClaims(claims);
            
            LOGGER.debug("Token validated successfully for subject: {}", claims.getSubject());
            return claims;
            
        } catch (ParseException e) {
            LOGGER.warn("Failed to parse JWT token: {}", e.getMessage());
            throw new AccessException("Invalid token format");
        } catch (BadJOSEException e) {
            LOGGER.warn("JWT signature verification failed: {}", e.getMessage());
            // Try refreshing JWKS and retry once (key rotation scenario)
            return retryWithRefreshedJwks(token, e);
        } catch (JOSEException e) {
            LOGGER.warn("JWT processing error: {}", e.getMessage());
            throw new AccessException("Token processing error");
        } catch (AccessException e) {
            throw e;
        } catch (IllegalArgumentException | NullPointerException e) {
            LOGGER.error("Invalid token data: {}", e.getMessage(), e);
            throw new AccessException("Invalid token format: " + e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error during token validation: {} - {}",
                e.getClass().getSimpleName(), e.getMessage(), e);
            throw new AccessException("Token validation failed: " + e.getClass().getSimpleName());
        }
    }
    
    private ConfigurableJWTProcessor<SecurityContext> getJwtProcessor() throws AccessException {
        if (jwtProcessor == null) {
            synchronized (this) {
                if (jwtProcessor == null) {
                    try {
                        jwtProcessor = createJwtProcessor(jwksProvider.getJwkSet());
                    } catch (IOException e) {
                        throw new AccessException(
                            "Failed to initialize JWT processor: " + e.getMessage());
                    }
                }
            }
        }
        return jwtProcessor;
    }
    
    private ConfigurableJWTProcessor<SecurityContext> createJwtProcessor(JWKSet jwkSet) {
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        
        JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(
            SUPPORTED_ALGORITHMS,
            new ImmutableJWKSet<>(jwkSet));
        processor.setJWSKeySelector(keySelector);
        
        // Configure claims verifier
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
            new JWTClaimsSet.Builder()
                .issuer(config.getIssuerUri())
                .build(),
            REQUIRED_CLAIMS));
        
        return processor;
    }
    
    /**
     * Retry validation with refreshed JWKS (for key rotation scenarios).
     *
     * @param token original token
     * @param originalException original exception
     * @return validated claims
     * @throws AccessException if retry also fails
     */
    private JWTClaimsSet retryWithRefreshedJwks(String token, Exception originalException)
        throws AccessException {
        LOGGER.info("Retrying token validation with refreshed JWKS");
        
        try {
            // Refresh JWKS
            JWKSet jwkSet = jwksProvider.refreshJwkSet();
            
            // Recreate processor with new keys
            synchronized (this) {
                this.jwtProcessor = createJwtProcessor(jwkSet);
            }
            
            // Validate using new processor
            JWTClaimsSet claims = this.jwtProcessor.process(token, null);
            validateClaims(claims);
            
            LOGGER.info("Token validated successfully after JWKS refresh");
            return claims;
            
        } catch (Exception e) {
            LOGGER.warn("Token validation failed even after JWKS refresh: {}", e.getMessage());
            throw new AccessException("Token signature verification failed");
        }
    }
    
    /**
     * Perform additional claims validation.
     *
     * @param claims JWT claims
     * @throws AccessException if validation fails
     */
    private void validateClaims(JWTClaimsSet claims) throws AccessException {
        // Validate expiration
        Date expirationTime = claims.getExpirationTime();
        if (expirationTime == null || expirationTime.before(new Date())) {
            throw new AccessException("Token has expired");
        }
        
        // Validate not before (if present)
        Date notBeforeTime = claims.getNotBeforeTime();
        if (notBeforeTime != null && notBeforeTime.after(new Date())) {
            throw new AccessException("Token is not yet valid");
        }
        
        // Validate audience (if client ID is configured)
        String clientId = config.getClientId();
        if (StringUtils.isNotBlank(clientId)) {
            List<String> audience = claims.getAudience();
            if (audience != null && !audience.isEmpty() && !audience.contains(clientId)) {
                // Check if 'azp' (authorized party) matches
                String azp = (String) claims.getClaim("azp");
                if (!clientId.equals(azp)) {
                    String message = String.format(
                        "Token audience mismatch. Expected: %s, Got: %s, azp: %s",
                        clientId, audience, azp);
                    
                    if (config.isStrictAudienceValidation()) {
                        LOGGER.error("{} - Strict validation enabled, rejecting token. "
                            + "This token may be intended for a different client.", message);
                        throw new AccessException("Token audience validation failed");
                    } else {
                        LOGGER.warn("{} - Strict validation disabled, accepting token. "
                            + "Set 'nacos.core.auth.plugin.oidc.strict-audience-validation=true' for better security.",
                            message);
                    }
                }
            }
        }
        
        // Validate issuer
        String issuer = claims.getIssuer();
        String expectedIssuer = config.getIssuerUri();
        if (StringUtils.isNotBlank(expectedIssuer) && !expectedIssuer.equals(issuer)) {
            // Handle trailing slash difference
            String normalizedExpected = expectedIssuer.endsWith("/")
                ? expectedIssuer.substring(0, expectedIssuer.length() - 1)
                : expectedIssuer;
            String normalizedIssuer = issuer != null && issuer.endsWith("/")
                ? issuer.substring(0, issuer.length() - 1)
                : issuer;
            
            if (!normalizedExpected.equals(normalizedIssuer)) {
                throw new AccessException("Token issuer mismatch");
            }
        }
    }
    
    /**
     * Extract username from JWT claims.
     *
     * @param claims JWT claims
     * @return username
     */
    public String extractUsername(JWTClaimsSet claims) {
        String usernameClaim = config.getUsernameClaim();
        
        // Try configured claim first
        Object username = claims.getClaim(usernameClaim);
        if (username != null) {
            return username.toString();
        }
        
        // Fallback to common claims
        String preferredUsername = (String) claims.getClaim("preferred_username");
        if (StringUtils.isNotBlank(preferredUsername)) {
            return preferredUsername;
        }
        
        String email = (String) claims.getClaim("email");
        if (StringUtils.isNotBlank(email)) {
            return email;
        }
        
        // Last resort: use subject
        return claims.getSubject();
    }
    
    /**
     * Extract roles from JWT claims.
     *
     * @param claims JWT claims
     * @return list of roles
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(JWTClaimsSet claims) {
        String rolesClaim = config.getRolesClaim();
        
        // Try configured claim
        Object roles = claims.getClaim(rolesClaim);
        if (roles instanceof List) {
            return (List<String>) roles;
        }
        
        // Try realm_access.roles (Keycloak format)
        Object realmAccess = claims.getClaim("realm_access");
        if (realmAccess instanceof java.util.Map) {
            Object realmRoles = ((java.util.Map<String, Object>) realmAccess).get("roles");
            if (realmRoles instanceof List) {
                return (List<String>) realmRoles;
            }
        }
        
        // Try resource_access.<client_id>.roles (Keycloak format)
        Object resourceAccess = claims.getClaim("resource_access");
        if (resourceAccess instanceof java.util.Map) {
            String clientId = config.getClientId();
            if (StringUtils.isNotBlank(clientId)) {
                Object clientAccess =
                    ((java.util.Map<String, Object>) resourceAccess).get(clientId);
                if (clientAccess instanceof java.util.Map) {
                    Object clientRoles =
                        ((java.util.Map<String, Object>) clientAccess).get("roles");
                    if (clientRoles instanceof List) {
                        return (List<String>) clientRoles;
                    }
                }
            }
        }
        
        // Try groups claim (common in some providers)
        Object groups = claims.getClaim("groups");
        if (groups instanceof List) {
            return (List<String>) groups;
        }
        
        // Log warning when no roles found - helps diagnose IdP integration issues
        LOGGER.warn(
            "No roles found in JWT claims for user: {}. Checked claim paths: {}, realm_access.roles, "
                + "resource_access.{}.roles, groups. Token may be missing role information.",
            claims.getSubject(), rolesClaim, config.getClientId());
        return Collections.emptyList();
    }
    
    /**
     * Check if the claims indicate an admin user.
     *
     * @param claims JWT claims
     * @return true if admin
     */
    public boolean isAdmin(JWTClaimsSet claims) {
        List<String> roles = extractRoles(claims);
        String adminRole = config.getAdminRole();
        return roles.contains(adminRole);
    }
}
