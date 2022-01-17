package com.alibaba.nacos.console.controller;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.auth.common.AuthConfigs;
import com.alibaba.nacos.common.codec.Base64;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.auth.RoleInfo;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.security.nacos.JwtTokenManager;
import com.alibaba.nacos.console.security.nacos.roles.NacosRoleServiceImpl;
import com.alibaba.nacos.console.security.nacos.users.NacosUser;
import com.alibaba.nacos.console.security.nacos.users.NacosUserDetailsServiceImpl;
import com.alibaba.nacos.console.utils.OidcUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/auth/oidc")
public class OidcAuthController {

    // TODO: get the domain dynamically
    private static final String OIDC_CALLBACK_URL = "http://localhost:8848/nacos/v1/auth/oidc/callback";

    NacosRestTemplate restTemplate = HttpClientBeanHolder.getNacosRestTemplate(Loggers.AUTH);

    @Autowired
    JwtTokenManager tokenManager;

    @Autowired
    private NacosUserDetailsServiceImpl userDetailsService;

    @Autowired
    private NacosRoleServiceImpl roleService;

    @Autowired
    private AuthConfigs authConfigs;

    /**
     *
     * @return list of oidps in configured
     */
    @GetMapping("/list")
    public List<Map<String, String>> list() {
        List<Map<String, String>> oidpList = new ArrayList<>();
        List<String> oidpListFromConfig = EnvUtil.getProperty("oidps", List.class);
        oidpListFromConfig.forEach(
                oidp -> {
                    Map<String, String> curOidp = new HashMap<>();
                    curOidp.put("key", oidp);
                    curOidp.put("name", OidcUtil.getName(oidp));
                    oidpList.add(curOidp);
                }
        );
        return oidpList;
    }

    /**
     *
     * @param oidpId oidp id used to auth
     * @param response a redirect response
     */
    @GetMapping("/init")
    public Object init(@RequestParam("oidpId") String oidpId, HttpServletResponse response) {

        String authUrl = OidcUtil.getClientId(oidpId);

        if (authUrl == null) {
            Loggers.AUTH.error("oidpId {} not found", oidpId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("oidpId not found");
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(OidcUtil.getAuthUrl(oidpId));
        uriBuilder.queryParam("response_type", "code");
        uriBuilder.queryParam("client_id", authUrl);
        uriBuilder.queryParam("redirect_uri", OIDC_CALLBACK_URL);

        String state = null;
        try {
            state = new ObjectMapper().writeValueAsString(new OidcState(oidpId));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failed to serialize state");
        }

        if(state != null) {
            byte[] stateBase64 = Base64.encodeBase64(state.getBytes());
            uriBuilder.queryParam("state", new String(stateBase64));
        }
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", uriBuilder.encode().toUriString());
        return null;
    }

    @GetMapping("callback")
    public Object callback(@RequestParam("code") String code, @RequestParam("state") String state, HttpServletRequest request, HttpServletResponse response) {

        state = new String(Base64.decodeBase64(state.getBytes()));

        OidcState oidcState = null;
        try {
            oidcState = new ObjectMapper().readValue(state, OidcState.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("error when deserializing state json");
        }

        UriComponentsBuilder tokenUriBuilder = UriComponentsBuilder.fromHttpUrl(OidcUtil.getTokenExchangeUrl(oidcState.getOidpId()));
        tokenUriBuilder.queryParam("grant_type", "authorization_code");
        tokenUriBuilder.queryParam("client_id", OidcUtil.getClientId(oidcState.getOidpId()));
        tokenUriBuilder.queryParam("client_secret", OidcUtil.getClientSecret(oidcState.getOidpId()));
        tokenUriBuilder.queryParam("code", code);
        tokenUriBuilder.queryParam("redirect_uri", OIDC_CALLBACK_URL);

        Header tokenHeader = Header.newInstance();
        tokenHeader.addParam("Accept", "application/json");
        tokenHeader.addParam("Content-Type", "application/json");

        OidcTokenResponse oidcTokenResponse = null;
        try {
            HttpRestResult<String> tokenResult = restTemplate.postJson(tokenUriBuilder.toUriString(), tokenHeader, "", String.class);
            oidcTokenResponse = new ObjectMapper().readValue(tokenResult.getData(), OidcTokenResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error when get authentication code");
        }

        // TODO: move the code follow to a method
        OidcUserInfo oidcUserInfo = null;
        UriComponentsBuilder userInfoUriBuilder = UriComponentsBuilder.fromHttpUrl(OidcUtil.getUserInfoUrl(oidcState.getOidpId()));
        Header userInfoHeader = Header.newInstance();
        userInfoHeader.addParam("Authorization", "Bearer " + oidcTokenResponse.getAccessToken());
        try {
            HttpRestResult<String> userInfoResult = restTemplate.get(userInfoUriBuilder.toUriString(), userInfoHeader, null,String.class);
            oidcUserInfo = new ObjectMapper().readValue(userInfoResult.getData(), OidcUserInfo.class);
        } catch (Exception e) {
            // Loggers.AUTH.error("userInfoResult: {}", userInfoResult);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error when getting user info");
        }

        // TODO: get the according user ID field by jsonpath parser
        String username = oidcUserInfo.getUserInfo().get("login");

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(String.format("user %s is not a nacos user", username));
        }

        String token = tokenManager.createToken(username);
        Authentication authentication = tokenManager.getAuthentication(token);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        NacosUser user = new NacosUser();
        user.setUserName(username);
        user.setToken(token);
        List<RoleInfo> roleInfoList = roleService.getRoles(username);
        if (roleInfoList != null) {
            for (RoleInfo roleInfo : roleInfoList) {
                if (roleInfo.getRole().equals(NacosRoleServiceImpl.GLOBAL_ADMIN_ROLE)) {
                    user.setGlobalAdmin(true);
                    break;
                }
            }
        }
        request.getSession().setAttribute(RequestUtil.NACOS_USER_KEY, user);

        ObjectNode result = JacksonUtils.createEmptyJsonNode();
        result.put(Constants.ACCESS_TOKEN, user.getToken());
        result.put(Constants.TOKEN_TTL, authConfigs.getTokenValidityInSeconds());
        result.put(Constants.GLOBAL_ADMIN, user.isGlobalAdmin());
        result.put(Constants.USERNAME, user.getUserName());

        String resultToken = null;
        try {
            resultToken = new ObjectMapper().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error when serializing the result to json");
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

        private final Map<String, String> state = new HashMap<>();

        public OidcState(){}

        public OidcState(String oidpId) {
            this.oidpId = oidpId;
        }

        public String getOidpId() {
            return oidpId;
        }

        public String setOidpId(String oidpId) {
            return this.oidpId = oidpId;
        }

        @JsonAnyGetter
        public Map<String, String> getState() {
            return state;
        }
    }

    static class OidcTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("exprie_in")
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

    static class OidcUserInfo {
        private final Map<String, String> userInfo = new HashMap<>();

        @JsonAnyGetter
        public Map<String, String> getUserInfo() {
            return userInfo;
        }

        @JsonAnySetter
        public void setUserInfo(String key, String value) {
            userInfo.put(key, value);
        }
    }

}
