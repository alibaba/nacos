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
import com.alibaba.nacos.plugin.auth.impl.oidc.authenticate.AuthorizationCodeHandler;
import com.alibaba.nacos.plugin.auth.impl.oidc.config.OidcAuthConfig;
import com.alibaba.nacos.plugin.auth.impl.oidc.constant.OidcConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.identity.OidcUserMapper.OidcUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
    
    private volatile AuthorizationCodeHandler authHandler;
    
    private volatile OidcAuthConfig config;
    
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
            initializeIfNeeded();
            
            // Build callback URL
            String callbackUrl = buildCallbackUrl(request);
            
            // Get authorization URL
            String authUrl = authHandler.buildAuthorizationUrl(callbackUrl);
            
            LOGGER.info("Redirecting to IdP for authentication");
            response.sendRedirect(authUrl);
            
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
        
        try {
            initializeIfNeeded();
            
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
            
            // Exchange code for tokens and get user
            OidcUser user = authHandler.exchangeCodeForUser(code, state, callbackUrl);
            
            LOGGER.info("OIDC authentication successful for user: {}", user.getUsername());
            
            // Set cookies for token delivery (cluster-friendly, no server-side storage)
            // Frontend will read cookies and sync to localStorage, then clear them
            String contextPath = request.getContextPath();
            String cookiePath = StringUtils.isBlank(contextPath) ? "/" : contextPath + "/";
            
            // Set accessToken cookie (frontend readable for sync to localStorage)
            Cookie accessTokenCookie = new Cookie("accessToken", user.getToken());
            accessTokenCookie.setHttpOnly(false); // Allow frontend to read
            accessTokenCookie.setSecure(isHttps(request));
            accessTokenCookie.setPath(cookiePath);
            accessTokenCookie.setMaxAge(COOKIE_EXPIRATION_SECONDS);
            response.addCookie(accessTokenCookie);
            
            // Set username cookie (URL encoded)
            Cookie usernameCookie = new Cookie("username",
                URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8));
            usernameCookie.setHttpOnly(false);
            usernameCookie.setSecure(isHttps(request));
            usernameCookie.setPath(cookiePath);
            usernameCookie.setMaxAge(COOKIE_EXPIRATION_SECONDS);
            response.addCookie(usernameCookie);
            
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
        
        try {
            initializeIfNeeded();
            
            // If redirect requested and IdP supports RP-initiated logout
            if (redirect) {
                String postLogoutUri = buildBaseUrl(request);
                String logoutUrl = authHandler.buildLogoutUrl(idToken, postLogoutUri);
                
                if (StringUtils.isNotBlank(logoutUrl)) {
                    LOGGER.info("Redirecting to IdP for logout");
                    response.sendRedirect(logoutUrl);
                    return null;
                }
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
            initializeIfNeeded();
            
            Map<String, Object> configInfo = new HashMap<>(8);
            configInfo.put("enabled", config.isValid());
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
     * Initialize components lazily.
     */
    private void initializeIfNeeded() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    config = OidcAuthConfig.getInstance();
                    authHandler = AuthorizationCodeHandler.getInstance();
                }
            }
        }
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
}
