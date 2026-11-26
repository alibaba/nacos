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

package com.alibaba.nacos.plugin.auth.impl.oidc.controller;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.oidc.OidcAuthPluginService;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler;
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler.AuthorizationRequestData;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OIDC login controller.
 * Handles the OIDC Authorization Code flow for user authentication.
 *
 * @author WangzJi
 */
@RestController
@RequestMapping("/v1/auth/oidc")
@SuppressWarnings("PMD")
public class OidcLoginController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(OidcLoginController.class);
    
    /**
     * Cookie expiration time in seconds (60 seconds).
     * Short-lived: frontend reads and syncs to localStorage, then clears cookies.
     */
    private static final int COOKIE_EXPIRATION_SECONDS = 60;
    
    /**
     * HttpOnly cookie name binding the login state and PKCE verifier to the browser.
     */
    static final String OIDC_TX_COOKIE_NAME = "nacos_oidc_tx";
    
    /**
     * HttpOnly cookie name storing the ID token for RP-initiated logout hint.
     */
    static final String OIDC_ID_TOKEN_COOKIE_NAME = "nacos_oidc_id_token";
    
    /**
     * Safe maximum length for storing an ID token in an HTTP cookie.
     */
    private static final int MAX_ID_TOKEN_COOKIE_LENGTH = 3800;
    
    private final OidcAuthPluginService pluginService;
    
    public OidcLoginController(OidcAuthPluginService pluginService) {
        this.pluginService = pluginService;
    }
    
    /**
     * Initiate OIDC login - redirects user to IdP.
     *
     * @param request  HTTP request
     * @param response HTTP response
     * @throws IOException if redirect fails
     */
    @Since("3.2.0")
    @GetMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Build callback URL
            String callbackUrl = buildCallbackUrl(request);
            
            // Create authorization request with PKCE and browser-bound transaction cookie
            AuthorizationRequestData authRequest =
                pluginService.createAuthorizationRequest(callbackUrl);
            addHttpOnlyCookie(response, OIDC_TX_COOKIE_NAME,
                authRequest.getTransactionCookieValue(), buildCookiePath(request),
                isHttps(request), AuthorizationCodeHandler.TRANSACTION_COOKIE_MAX_AGE_SECONDS);
            
            LOGGER.info("Redirecting to IdP for authentication");
            response.sendRedirect(authRequest.getAuthorizationUrl());
            
        } catch (AccessException e) {
            LOGGER.error("Failed to initiate OIDC login: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Failed to initiate login: " + e.getMessage());
        }
    }
    
    /**
     * OIDC callback - handles the authorization code response from IdP.
     *
     * @param code     authorization code
     * @param state    state parameter for CSRF verification
     * @param error    error code if authentication failed
     * @param errorDescription error description
     * @param request  HTTP request
     * @param response HTTP response
     * @return authentication result with access token
     */
    @Since("3.2.0")
    @GetMapping("/callback")
    public Result<Map<String, Object>> callback(
        @RequestParam(required = false) String code,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String error,
        @RequestParam(name = "error_description", required = false) String errorDescription,
        HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        
        String cookiePath = buildCookiePath(request);
        boolean secure = isHttps(request);
        String txCookie = getCookieValue(request, OIDC_TX_COOKIE_NAME);
        // Always expire the single-use transaction cookie before any redirect
        clearHttpOnlyCookie(response, OIDC_TX_COOKIE_NAME, cookiePath, secure);
        
        try {
            // Check for error response from IdP
            if (StringUtils.isNotBlank(error)) {
                LOGGER.warn("OIDC authentication error: {} - {}", error, errorDescription);
                String errorMsg = errorDescription != null ? errorDescription : error;
                String errorRedirectUrl = buildBaseUrl(request) + "/#/login?error="
                    + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
                response.sendRedirect(errorRedirectUrl);
                return null;
            }
            
            // Validate required parameters
            if (StringUtils.isBlank(code)) {
                String errorRedirectUrl = buildBaseUrl(request) + "/#/login?error="
                    + URLEncoder.encode("Missing authorization code", StandardCharsets.UTF_8);
                response.sendRedirect(errorRedirectUrl);
                return null;
            }
            if (StringUtils.isBlank(state)) {
                String errorRedirectUrl = buildBaseUrl(request) + "/#/login?error="
                    + URLEncoder.encode("Missing state parameter", StandardCharsets.UTF_8);
                response.sendRedirect(errorRedirectUrl);
                return null;
            }
            
            // Build callback URL (must match the one used in login)
            String callbackUrl = buildCallbackUrl(request);
            
            // Exchange code for tokens and get user (with browser-bound transaction & PKCE)
            OidcUser user =
                pluginService.exchangeCodeForUser(code, state, callbackUrl, txCookie);
            
            LOGGER.info("OIDC authentication successful for user: {}", user.getUsername());
            
            // Set cookies for token delivery (cluster-friendly, no server-side storage)
            // Frontend will read cookies and sync to localStorage, then clear them
            
            // Set accessToken cookie (frontend readable for sync to localStorage)
            Cookie accessTokenCookie = new Cookie("accessToken", user.getToken());
            accessTokenCookie.setHttpOnly(false); // Allow frontend to read
            accessTokenCookie.setSecure(secure);
            accessTokenCookie.setPath(cookiePath);
            accessTokenCookie.setMaxAge(COOKIE_EXPIRATION_SECONDS);
            response.addCookie(accessTokenCookie);
            
            // Set username cookie (URL encoded)
            Cookie usernameCookie = new Cookie("username",
                URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8));
            usernameCookie.setHttpOnly(false);
            usernameCookie.setSecure(secure);
            usernameCookie.setPath(cookiePath);
            usernameCookie.setMaxAge(COOKIE_EXPIRATION_SECONDS);
            response.addCookie(usernameCookie);
            
            // Set globalAdmin cookie (frontend readable for sync to localStorage)
            Cookie globalAdminCookie =
                new Cookie("globalAdmin", String.valueOf(user.isGlobalAdmin()));
            globalAdminCookie.setHttpOnly(false);
            globalAdminCookie.setSecure(secure);
            globalAdminCookie.setPath(cookiePath);
            globalAdminCookie.setMaxAge(COOKIE_EXPIRATION_SECONDS);
            response.addCookie(globalAdminCookie);
            
            // Store ID token in HttpOnly cookie for RP-initiated logout hint when safe in size
            String idToken = user.getIdToken();
            if (StringUtils.isNotBlank(idToken) && idToken.length() <= MAX_ID_TOKEN_COOKIE_LENGTH
                && isSafeCookieValue(idToken)) {
                addHttpOnlyCookie(response, OIDC_ID_TOKEN_COOKIE_NAME, idToken, cookiePath, secure,
                    -1);
            } else {
                if (StringUtils.isNotBlank(idToken)) {
                    LOGGER.warn(
                        "Skipping id_token cookie storage (length={}): exceeds limit or unsafe",
                        idToken.length());
                }
                clearHttpOnlyCookie(response, OIDC_ID_TOKEN_COOKIE_NAME, cookiePath, secure);
            }
            
            // Redirect to home page (no parameters in URL)
            String successRedirectUrl = buildBaseUrl(request) + "/#/";
            response.sendRedirect(successRedirectUrl);
            return null;
            
        } catch (AccessException e) {
            LOGGER.warn("OIDC callback failed: {}", e.getMessage());
            String errorRedirectUrl = buildBaseUrl(request) + "/#/login?error="
                + URLEncoder.encode(e.getErrMsg(), StandardCharsets.UTF_8);
            response.sendRedirect(errorRedirectUrl);
            return null;
        } catch (Exception e) {
            LOGGER.error("OIDC callback error", e);
            String errorRedirectUrl = buildBaseUrl(request) + "/#/login?error="
                + URLEncoder.encode("Authentication failed: " + e.getMessage(),
                    StandardCharsets.UTF_8);
            response.sendRedirect(errorRedirectUrl);
            return null;
        }
    }
    
    /**
     * Logout - clears session and optionally redirects to IdP logout.
     *
     * @param idToken  optional ID token for logout hint
     * @param redirect whether to redirect to IdP for RP-initiated logout
     * @param request  HTTP request
     * @param response HTTP response
     * @return logout result
     * @throws IOException if redirect fails
     */
    @Since("3.2.0")
    @RequestMapping(value = "/logout", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<String> logout(
        @RequestParam(required = false) String idToken,
        @RequestParam(defaultValue = "false") boolean redirect,
        HttpServletRequest request,
        HttpServletResponse response) throws IOException {
        
        String cookiePath = buildCookiePath(request);
        boolean secure = isHttps(request);
        String effectiveIdToken = StringUtils.isNotBlank(idToken)
            ? idToken : getCookieValue(request, OIDC_ID_TOKEN_COOKIE_NAME);
        clearHttpOnlyCookie(response, OIDC_ID_TOKEN_COOKIE_NAME, cookiePath, secure);
        
        try {
            // If redirect requested, redirect to IdP logout endpoint or fall back to login page
            if (redirect) {
                String postLogoutUri = buildBaseUrl(request);
                String logoutUrl = pluginService.buildLogoutUrl(effectiveIdToken, postLogoutUri);
                
                if (StringUtils.isNotBlank(logoutUrl)) {
                    LOGGER.info("Redirecting to IdP for logout");
                    response.sendRedirect(logoutUrl);
                } else {
                    LOGGER.info("IdP end_session_endpoint unavailable, redirecting to login page");
                    response.sendRedirect(postLogoutUri + "/#/login");
                }
                return null;
            }
            
            LOGGER.info("User logged out");
            return Result.success("Logged out successfully");
            
        } catch (Exception e) {
            LOGGER.error("Logout error", e);
            return Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Logout failed", null);
        }
    }
    
    /**
     * Get OIDC configuration info (for frontend).
     * Console uses this to detect OIDC mode and hide user/role/permission management.
     *
     * @return OIDC configuration
     */
    @Since("3.2.0")
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        try {
            Map<String, Object> configInfo = new HashMap<>(8);
            configInfo.put("enabled", pluginService.isConfigurationValid());
            configInfo.put("authType", "oidc");
            configInfo.put("loginUrl", "/v1/auth/oidc/login");
            // When OIDC is enabled, user/role/permission management is handled by IdP
            configInfo.put("userManagementEnabled", false);
            configInfo.put("roleManagementEnabled", false);
            configInfo.put("permissionManagementEnabled", false);
            
            return Result.success(configInfo);
            
        } catch (Exception e) {
            LOGGER.error("Failed to get OIDC config", e);
            return Result.failure(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Failed to get configuration", null);
        }
    }
    
    /**
     * Build the callback URL from the current request.
     *
     * @param request HTTP request
     * @return callback URL
     */
    private String buildCallbackUrl(HttpServletRequest request) {
        String baseUrl = buildBaseUrl(request);
        return baseUrl + "/v1/auth/oidc/callback";
    }
    
    /**
     * Build base URL from request.
     *
     * @param request HTTP request
     * @return base URL
     */
    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        // Include port if non-standard
        boolean isNonStandardHttpPort = OidcConstants.HTTP_PROTOCOL.equals(scheme)
            && serverPort != OidcConstants.DEFAULT_HTTP_PORT;
        boolean isNonStandardHttpsPort = OidcConstants.HTTPS_PROTOCOL.equals(scheme)
            && serverPort != OidcConstants.DEFAULT_HTTPS_PORT;
        if (isNonStandardHttpPort || isNonStandardHttpsPort) {
            url.append(":").append(serverPort);
        }
        
        url.append(contextPath);
        return url.toString();
    }
    
    /**
     * Check if the request is using HTTPS.
     *
     * @param request HTTP request
     * @return true if HTTPS
     */
    private boolean isHttps(HttpServletRequest request) {
        return OidcConstants.HTTPS_PROTOCOL.equals(request.getScheme());
    }
    
    private String buildCookiePath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        if (StringUtils.isBlank(contextPath)) {
            return "/";
        }
        return contextPath.endsWith("/") ? contextPath : contextPath + "/";
    }
    
    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
    
    private void addHttpOnlyCookie(HttpServletResponse response, String name, String value,
        String cookiePath, boolean secure, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path(cookiePath);
        if (maxAgeSeconds >= 0) {
            builder.maxAge(maxAgeSeconds);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
    
    private void clearHttpOnlyCookie(HttpServletResponse response, String name, String cookiePath,
        boolean secure) {
        addHttpOnlyCookie(response, name, "", cookiePath, secure, 0);
    }
    
    private boolean isSafeCookieValue(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c >= 0x7F || c == ';' || c == ',' || c == '"' || c == '\\') {
                return false;
            }
        }
        return true;
    }
}
