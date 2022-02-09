/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.common.codec.Base64;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.console.security.nacos.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.console.service.OidcService;
import com.alibaba.nacos.console.utils.OidcUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC auth controller.
 *
 * @author Kicey
 */
@RestController
@RequestMapping("/v1/auth/oidc")
public class OidcAuthController {
    
    @Autowired
    private OidcService oidcService;
    
    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;
    
    /**
     * List all OIDC IDP configurations.
     *
     * @return OIDC IDP configurations
     */
    @GetMapping("/list")
    public List<Map<String, String>> list() {
        List<Map<String, String>> oidpList = new ArrayList<>();
        List<String> oidpListFromConfig = OidcUtil.getOidpList();
        oidpListFromConfig.forEach(oidp -> {
            Map<String, String> curOidp = new HashMap<>(4);
            curOidp.put("key", oidp);
            curOidp.put("name", OidcUtil.getName(oidp));
            oidpList.add(curOidp);
        });
        return oidpList;
    }
    
    /**
     * Initiate OIDC authentication flow.
     * A state object will be initialized here, which will be base64-encoded, passed to the IDP and eventually relayed to the callback endpoint.
     *
     * @param id       id of the IDP
     * @param origin   origin of the request from browser
     * @param response response object
     */
    @GetMapping("/init")
    public Object init(@RequestParam("id") String id, @RequestParam("origin") String origin, HttpServletResponse response) {
        
        String authUrl = OidcUtil.getAuthUrl(id);
        if (authUrl == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(String.format("OIDC IDP %s not found in config", id));
        }
        
        String clientId = OidcUtil.getClientId(id);
        
        List<String> scopes = OidcUtil.getScopes(id);

        String host = OidcUtil.getExposedHost();
        if (host == null) {
            host = origin;
        }
        String redirectUrl = host + OidcUtil.CALLBACK_PATH;
        
        String state;
        try {
            state = new ObjectMapper().writeValueAsString(new OidcState().setOidpId(id).setRedirectUrl(redirectUrl));
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failed to serialize state");
        }
        
        byte[] stateBase64bytes = Base64.encodeBase64(state.getBytes());
        String stateBase64 = new String(stateBase64bytes);
        
        String authInitUri = oidcService.completeAuthUriWithParameters(authUrl, clientId, redirectUrl,
                scopes, stateBase64);
        
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", authInitUri);
        return null;
    }
    
    /**
     * Handle callback from OIDC IDP after a successful authentication.
     *
     * @param code     authorization code
     * @param state    base64-encoded state object
     * @param request  request object
     * @param response response object
     */
    @GetMapping("/callback")
    public Object callback(@RequestParam("code") String code, @RequestParam("state") String state,
            HttpServletRequest request, HttpServletResponse response) {
        state = new String(Base64.decodeBase64(state.getBytes()));
        OidcState oidcState;
        try {
            oidcState = new ObjectMapper().readValue(state, OidcState.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failed to get the state");
        }
        String oidp = oidcState.getOidpId();
        String redirectUrl = oidcState.getRedirectUrl();
        
        RestResult<String> tokenResult;
        try {
            tokenResult = oidcService.exchangeTokenWithCodeThroughPostForm(oidp, code, redirectUrl);
        } catch (SocketTimeoutException e) {
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                    .body("timeout when exchanging token, please try again");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failed to exchange token");
        }
        
        OidcTokenResponse oidcTokenResponse;
        try {
            oidcTokenResponse = new ObjectMapper().readValue(tokenResult.getData(), OidcTokenResponse.class);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failed to process returned token");
        }
        
        String accessToken = oidcTokenResponse.getAccessToken();
        
        String userInfoJson;
        try {
            HttpRestResult<String> userInfoResult = oidcService.getUserinfoWithAccessToken(oidp, accessToken);
            userInfoJson = userInfoResult.getData();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("failed to get user info");
        }
        
        String username;
        try {
            username = OidcService.getUsernameFromUserinfo(oidp, userInfoJson);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("failed to get username from the userinfo through jsonpath");
        }
        
        try {
            userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(String.format("user %s is not a nacos user", username));
        }
        
        String resultToken;
        try {
            resultToken = oidcService.createNacosInternalToken(username, request);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("failed to create a nacos internal token");
        }
        
        UriComponentsBuilder redirectUriBuilder = UriComponentsBuilder.fromPath(EnvUtil.getContextPath());
        redirectUriBuilder.queryParam("token", resultToken);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", redirectUriBuilder.toUriString());
        return null;
    }
    
    static class OidcState {
        
        @JsonProperty("oidp_id")
        private String oidpId;

        @JsonProperty("redirect_url")
        private String redirectUrl;
        
        private final Map<String, String> state = new HashMap<>();
        
        public String getOidpId() {
            return oidpId;
        }
        
        public OidcState setOidpId(String oidpId) {
            this.oidpId = oidpId;
            return this;
        }

        public String getRedirectUrl() {
            return redirectUrl;
        }
        
        public OidcState setRedirectUrl(String redirectUrl) {
            this.redirectUrl = redirectUrl;
            return this;
        }
        
        @JsonAnyGetter
        public Map<String, String> getState() {
            return state;
        }
    }
    
    /**
     * The response including OIDC token returned from the OIDC IDP.
     *
     * @author Kicey
     */
    public static class OidcTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("refresh_token")
        private String refreshToken;
        
        @JsonProperty("id_token")
        private String idToken;
        
        private final Map<String, String> others = new HashMap<>();
        
        @JsonAnyGetter
        public Map<String, String> getOthers() {
            return others;
        }
        
        @JsonAnySetter
        public void setOthers(String key, String value) {
            others.put(key, value);
        }
        
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
        
        public String getIdToken() {
            return idToken;
        }
        
        public void setIdToken(String idToken) {
            this.idToken = idToken;
        }
    }
}
