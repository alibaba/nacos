package com.alibaba.nacos.client.auth.result;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.client.naming.utils.UtilAndComs.HTTP;
import static com.alibaba.nacos.client.naming.utils.UtilAndComs.webContext;

public class HttpRequest implements RequestManager {
    
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger(HttpRequest.class);
    
    private static final String LOGIN_URL = "/v1/auth/users/login";
    
    private final NacosRestTemplate nacosRestTemplate;
    
    public HttpRequest(NacosRestTemplate nacosRestTemplate) {
        this.nacosRestTemplate = nacosRestTemplate;
    }
    
    @Override
    public Map<String, String> getResponse(Properties properties) {
        
        String contextPath = ContextPathUtil
                .normalizeContextPath(properties.getProperty(PropertyKeyConst.CONTEXT_PATH, webContext));
        String server = properties.getProperty(ResultConstant.SERVER, StringUtils.EMPTY);
        String url = HTTP + server + contextPath + LOGIN_URL;
        
        if (server.contains(Constants.HTTP_PREFIX)) {
            url = server + contextPath + LOGIN_URL;
        }
        
        Map<String, String> params = new HashMap<String, String>(2);
        Map<String, String> bodyMap = new HashMap<String, String>(2);
        params.put(PropertyKeyConst.USERNAME, properties.getProperty(PropertyKeyConst.USERNAME, StringUtils.EMPTY));
        bodyMap.put(PropertyKeyConst.PASSWORD, properties.getProperty(PropertyKeyConst.PASSWORD, StringUtils.EMPTY));
        try {
            HttpRestResult<String> restResult = nacosRestTemplate
                    .postForm(url, Header.EMPTY, Query.newInstance().initParams(params), bodyMap, String.class);
            if (!restResult.ok()) {
                SECURITY_LOGGER.error("login failed: {}", JacksonUtils.toJson(restResult));
                return null;
            }
            JsonNode obj = JacksonUtils.toObj(restResult.getData());
            Map<String, String> map = new HashMap<>();
            
            if (obj.has(Constants.ACCESS_TOKEN)) {
                map.put(ResultConstant.ACCESSTOKEN, obj.get(Constants.ACCESS_TOKEN).asText());
                map.put(ResultConstant.TOKENTTL, obj.get(Constants.TOKEN_TTL).asText());
                String t = obj.get(Constants.TOKEN_TTL).asText();
                int a = Integer.parseInt(t);
            } else {
                SECURITY_LOGGER.info("[NacosClientAuthService] ACCESS_TOKEN is empty from response");
            }
            return map;
        } catch (Exception e) {
            SECURITY_LOGGER.error("[ NacosClientAuthService] login http request failed"
                    + " url: {}, params: {}, bodyMap: {}, errorMsg: {}", url, params, bodyMap, e.getMessage());
            return null;
        }
    }
}
