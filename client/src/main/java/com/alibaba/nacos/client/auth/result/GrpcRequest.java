package com.alibaba.nacos.client.auth.result;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.auth.AuthGrpcRequest;
import com.alibaba.nacos.client.auth.AuthGrpcResponse;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.grpc.GrpcConnection;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrpcRequest implements RequestManager {
    
    private static final Logger GRPC_LOGGER = LoggerFactory.getLogger(GrpcRequest.class);
    
    private static final Pattern EXCLUDE_PROTOCOL_PATTERN = Pattern.compile("(?<=\\w{1,5}://)(.*)");
    
    private GrpcConnection grpcConnection;
    
    @Override
    public Map<String, String> getResponse(Properties properties) {
        String server = properties.getProperty(ResultConstant.SERVER, StringUtils.EMPTY);
        RpcClient.ServerInfo serverInfo = resolveServerInfo(server);
        grpcConnection = new GrpcConnection(serverInfo, null);
        String username = properties.getProperty(PropertyKeyConst.USERNAME);
        String password = properties.getProperty(PropertyKeyConst.PASSWORD);
        Request request = new AuthGrpcRequest();
        request.putHeader(ResultConstant.USERNAME, username);
        request.putHeader(ResultConstant.PASSWORD, password);
        
        try {
            AuthGrpcResponse response = (AuthGrpcResponse) grpcConnection.request(request, 3000L);
            if (ResponseCode.SUCCESS.getCode() != response.getResultCode()) {
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            Map<String, String> map = new HashMap<>();
            if (StringUtils.isNotBlank(response.getAccessToken())) {
                
                map.put(ResultConstant.ACCESSTOKEN, response.getAccessToken());
                map.put(ResultConstant.TOKENTTL, String.valueOf(response.getTokenTtl()));
                return map;
            } else {
                GRPC_LOGGER.info("[NacosClientAuthService] ACCESS_TOKEN is empty from response");
            }
            return map;
        } catch (Exception e) {
            GRPC_LOGGER.error("[ NacosClientAuthService] login grpc request failed" + "errorMsg: {}", e.getMessage());
            return null;
        }
    }
    
    @SuppressWarnings("PMD.UndefineMagicConstantRule")
    private RpcClient.ServerInfo resolveServerInfo(String serverAddress) {
        Matcher matcher = EXCLUDE_PROTOCOL_PATTERN.matcher(serverAddress);
        if (matcher.find()) {
            serverAddress = matcher.group(1);
        }
        
        String[] ipPortTuple = serverAddress.split(Constants.COLON, 2);
        int serverPort;
        if (ipPortTuple.length > 1 && StringUtils.isNotBlank(ipPortTuple[1])) {
            serverPort = Integer.parseInt(ipPortTuple[1]);
        } else {
            serverPort = Integer.parseInt(System.getProperty("nacos.server.port", "8848"));
        }
        return new RpcClient.ServerInfo(ipPortTuple[0], serverPort);
    }
    
}
