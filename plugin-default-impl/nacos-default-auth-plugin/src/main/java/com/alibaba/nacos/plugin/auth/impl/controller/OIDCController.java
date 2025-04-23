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

package com.alibaba.nacos.plugin.auth.impl.controller;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.common.codec.Base64;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCClient;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCConfigs;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCProvider;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCService;
import com.alibaba.nacos.plugin.auth.impl.oidc.OIDCState;
import com.alibaba.nacos.plugin.auth.impl.users.NacosUser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.openid.connect.sdk.AuthenticationRequest;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Open ID Connect Endpoint.
 *
 * @author Roiocam
 */
@SuppressWarnings("checkstyle:abbreviationaswordinname")
@RestController
@RequestMapping(OIDCController.PATH)
public class OIDCController {
    
    public static final String PATH = "/v1/auth/oidc";
    
    public static final String CALLBACK_PATH = PATH + "/callback";
    
    private final OIDCClient oidcClient;
    
    private final OIDCService oidcService;
    
    @Autowired
    public OIDCController(OIDCClient oidcClient, OIDCService oidcService) {
        this.oidcClient = oidcClient;
        this.oidcService = oidcService;
    }
    
    private static String buildRedirectUriWithPayload(String origin, String resultCode, String result) {
        // split the origin URL into base URL and hash route
        int hashIndex = origin.indexOf('#');
        String baseUrl = hashIndex != -1 ? origin.substring(0, hashIndex) : origin;
        String hashRoute = hashIndex != -1 ? origin.substring(hashIndex) : "";
        
        // build redirect url with hash route and token
        UriComponentsBuilder redirectUriBuilder = UriComponentsBuilder.fromUriString(baseUrl);
        if (!hashRoute.isEmpty()) {
            StringBuilder sb = new StringBuilder(hashRoute);
            if (hashRoute.contains("?")) {
                sb.append("&");
            } else {
                sb.append("?");
            }
            hashRoute = sb.append(resultCode).append("=").append(result).toString();
        }
        return redirectUriBuilder.build().toUriString() + hashRoute;
    }
    
    /**
     * Get current OIDC provider.
     */
    @GetMapping("/provider")
    public OIDCProvider getProvider() {
        String providerKey = OIDCConfigs.getProvider();
        OIDCProvider provider = new OIDCProvider();
        provider.setName(OIDCConfigs.getNameByKey(providerKey));
        provider.setKey(providerKey);
        return provider;
        
    }
    
    /**
     * Start Open ID Connect Authentication Flow.
     *
     * @param response HttpServletResponse
     * @param session  HttpSession
     * @throws IOException IOException
     */
    @GetMapping("/start")
    public void startAuthentication(@RequestParam("origin") String origin, HttpServletResponse response,
            HttpSession session) throws IOException {
        
        String callbackUri = ServletUriComponentsBuilder.fromCurrentContextPath().path(CALLBACK_PATH).toUriString();
        
        AuthenticationRequest authRequest = oidcClient.createAuthenticationRequest(callbackUri, origin);
        
        session.setAttribute(AuthConstants.OIDC_STATE, authRequest.getState().getValue());
        session.setAttribute(AuthConstants.OIDC_NONCE, authRequest.getNonce().getValue());
        
        // Redirect to Authentication endpoint
        response.sendRedirect(authRequest.toURI().toString());
    }
    
    /**
     * Authorization server callback this interface, process the authorization code and exchange token.
     *
     * @param code          Authorization Code
     * @param returnedState State returned by the authorization server
     * @param response      HttpServletResponse
     * @param session       HttpSession
     * @throws IOException IOException
     */
    @GetMapping("/callback")
    public void callback(@RequestParam("code") String code, @RequestParam("state") String returnedState,
            HttpServletResponse response, HttpSession session) throws IOException {
        
        // Check if the state is valid
        String originalState = (String) session.getAttribute(AuthConstants.OIDC_STATE);
        if (originalState == null) {
            String missingOrigin = ServletUriComponentsBuilder.fromCurrentContextPath().path(AuthConstants.LOGIN_PAGE)
                    .toUriString();
            String uriString = buildRedirectUriWithPayload(missingOrigin, AuthConstants.OIDC_PARAM_MSG,
                    "Session expired");
            response.sendRedirect(uriString);
            return;
        }
        String json = Base64URL.from(originalState).decodeToString();
        OIDCState state = JacksonUtils.toObj(json, OIDCState.class);
        if (!originalState.equals(returnedState)) {
            String uriString = buildRedirectUriWithPayload(state.getOrigin(), AuthConstants.OIDC_PARAM_MSG,
                    "Invalid state");
            response.sendRedirect(uriString);
            return;
        }
        
        // Exchange the authorization code for the information
        String callbackUri = ServletUriComponentsBuilder.fromCurrentContextPath().path(CALLBACK_PATH).toUriString();
        UserInfo userInfo = oidcClient.getUserInfo(new AuthorizationCode(code), callbackUri, state.getNonce());
        
        // Extract the username from the user info
        String preferredUsername = userInfo.getPreferredUsername();
        NacosUser nacosUser;
        try {
            nacosUser = oidcService.getUser(preferredUsername);
        } catch (AccessException e) {
            String uriString = buildRedirectUriWithPayload(state.getOrigin(), AuthConstants.OIDC_PARAM_MSG,
                    "User not found");
            response.sendRedirect(uriString);
            return;
        }
        
        session.setAttribute(AuthConstants.NACOS_USER_KEY, nacosUser);
        session.setAttribute(com.alibaba.nacos.plugin.auth.constant.Constants.Identity.IDENTITY_ID,
                nacosUser.getUserName());
        
        response.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.TOKEN_PREFIX + nacosUser.getToken());
        
        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put(Constants.ACCESS_TOKEN, nacosUser.getToken());
        result.put(Constants.TOKEN_TTL, oidcService.getTokenTtlInSeconds(nacosUser.getToken()));
        result.put(Constants.GLOBAL_ADMIN, nacosUser.isGlobalAdmin());
        result.put(Constants.USERNAME, nacosUser.getUserName());
        
        byte[] resultCodedBytes = Base64.encodeBase64(result.toString().getBytes(StandardCharsets.UTF_8));
        
        String uriString = buildRedirectUriWithPayload(state.getOrigin(), AuthConstants.OIDC_PARAM_TOKEN,
                new String(resultCodedBytes, StandardCharsets.UTF_8));
        
        response.sendRedirect(uriString);
    }
    
}
