package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.RequestCallBack;
import com.alibaba.nacos.api.remote.request.ClientDetectionRequest;
import com.alibaba.nacos.api.remote.request.ConnectResetRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.remote.exception.ConnectionAlreadyClosedException;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.monitor.MetricsMonitor;
import com.alibaba.nacos.plugin.control.ControlManagerCenter;
import com.alibaba.nacos.plugin.control.Loggers;
import com.alibaba.nacos.plugin.control.connection.mse.MseConnectionControlRule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MseRuntimeConnectionEjector extends RuntimeConnectionEjector {
    
    public MseRuntimeConnectionEjector() {
    }
    
    public void doEject() {
        try {
            
            Map<String, Connection> connections = connectionManager.connections;
            MseConnectionControlRule connectionControlRule = (MseConnectionControlRule) ControlManagerCenter
                    .getInstance().getConnectionControlManager().getConnectionLimitRule();
            int totalCount = connections.size();
            Loggers.CONNECTION.info("Connection check task start");
            MetricsMonitor.getLongConnectionMonitor().set(totalCount);
            Set<Map.Entry<String, Connection>> entries = connections.entrySet();
            int currentSdkClientCount = connectionManager.currentSdkClientCount();
            int loadClient = this.getLoadClient();
            boolean isLoaderClient = loadClient >= 0;
            int currentMaxClient = isLoaderClient ? loadClient : connectionControlRule.getCountLimit();
            int expelCount = currentMaxClient < 0 ? 0 : Math.max(currentSdkClientCount - currentMaxClient, 0);
            
            Loggers.CONNECTION
                    .info("Long connection metrics detail ,Total count ={}, sdkCount={},clusterCount={}, currentLimit={}, toExpelCount={}",
                            totalCount, currentSdkClientCount, (totalCount - currentSdkClientCount),
                            currentMaxClient + (isLoaderClient ? "(loaderCount)" : ""), expelCount);
            
            List<String> expelClient = new LinkedList<>();
            
            Map<String, AtomicInteger> expelForIp = new HashMap<>(16);
            
            //1. calculate expel count  of ip.
            for (Map.Entry<String, Connection> entry : entries) {
                
                Connection client = entry.getValue();
                String appName = client.getMetaInfo().getAppName();
                String clientIp = client.getMetaInfo().getClientIp();
                if (client.getMetaInfo().isSdkSource() && !expelForIp.containsKey(clientIp)) {
                    //get limit for current ip.
                    int countLimitOfIp = connectionControlRule.getCountLimitOfIp(clientIp);
                    if (countLimitOfIp < 0) {
                        int countLimitOfApp = connectionControlRule.getCountLimitOfApp(appName);
                        countLimitOfIp = countLimitOfApp < 0 ? countLimitOfIp : countLimitOfApp;
                    }
                    if (countLimitOfIp < 0) {
                        countLimitOfIp = connectionControlRule.getCountLimitPerClientIpDefault();
                    }
                    
                    if (countLimitOfIp >= 0 && connectionManager.getConnectionForClientIp().containsKey(clientIp)) {
                        AtomicInteger currentCountIp = connectionManager.getConnectionForClientIp().get(clientIp);
                        if (currentCountIp != null && currentCountIp.get() > countLimitOfIp) {
                            expelForIp.put(clientIp, new AtomicInteger(currentCountIp.get() - countLimitOfIp));
                        }
                    }
                }
            }
            
            if (expelForIp.size() > 0) {
                Loggers.CONNECTION
                        .info("Check over limit for ip limit rule, over limit ip count={}", expelForIp.size());
                Loggers.CONNECTION.info("Over limit ip expel info, {}", expelForIp);
            }
            
            Set<String> outDatedConnections = new HashSet<>();
            long now = System.currentTimeMillis();
            //2.get expel connection for ip limit.
            for (Map.Entry<String, Connection> entry : entries) {
                Connection client = entry.getValue();
                String clientIp = client.getMetaInfo().getClientIp();
                AtomicInteger integer = expelForIp.get(clientIp);
                if (integer != null && integer.intValue() > 0) {
                    integer.decrementAndGet();
                    expelClient.add(client.getMetaInfo().getConnectionId());
                    expelCount--;
                } else if (now - client.getMetaInfo().getLastActiveTime() >= KEEP_ALIVE_TIME) {
                    outDatedConnections.add(client.getMetaInfo().getConnectionId());
                }
                
            }
            
            //3. if total count is still over limit.
            if (expelCount > 0) {
                for (Map.Entry<String, Connection> entry : entries) {
                    Connection client = entry.getValue();
                    if (!expelForIp.containsKey(client.getMetaInfo().clientIp) && client.getMetaInfo().isSdkSource()
                            && expelCount > 0) {
                        expelClient.add(client.getMetaInfo().getConnectionId());
                        expelCount--;
                        outDatedConnections.remove(client.getMetaInfo().getConnectionId());
                    }
                }
            }
            
            String serverIp = null;
            String serverPort = null;
            if (StringUtils.isNotBlank(redirectAddress) && redirectAddress.contains(Constants.COLON)) {
                String[] split = redirectAddress.split(Constants.COLON);
                serverIp = split[0];
                serverPort = split[1];
            }
            
            for (String expelledClientId : expelClient) {
                try {
                    Connection connection = connectionManager.getConnection(expelledClientId);
                    if (connection != null) {
                        ConnectResetRequest connectResetRequest = new ConnectResetRequest();
                        connectResetRequest.setServerIp(serverIp);
                        connectResetRequest.setServerPort(serverPort);
                        connection.asyncRequest(connectResetRequest, null);
                        Loggers.CONNECTION
                                .info("Send connection reset request , connection id = {},recommendServerIp={}, recommendServerPort={}",
                                        expelledClientId, connectResetRequest.getServerIp(),
                                        connectResetRequest.getServerPort());
                    }
                    
                } catch (ConnectionAlreadyClosedException e) {
                    connectionManager.unregister(expelledClientId);
                } catch (Exception e) {
                    Loggers.CONNECTION
                            .error("Error occurs when expel connection, expelledClientId:{}", expelledClientId, e);
                }
            }
            
            //4.client active detection.
            Loggers.CONNECTION.info("Out dated connection ,size={}", outDatedConnections.size());
            if (CollectionUtils.isNotEmpty(outDatedConnections)) {
                Set<String> successConnections = new HashSet<>();
                final CountDownLatch latch = new CountDownLatch(outDatedConnections.size());
                for (String outDateConnectionId : outDatedConnections) {
                    try {
                        Connection connection = connectionManager.getConnection(outDateConnectionId);
                        if (connection != null) {
                            ClientDetectionRequest clientDetectionRequest = new ClientDetectionRequest();
                            connection.asyncRequest(clientDetectionRequest, new RequestCallBack() {
                                @Override
                                public Executor getExecutor() {
                                    return null;
                                }
                                
                                @Override
                                public long getTimeout() {
                                    return 1000L;
                                }
                                
                                @Override
                                public void onResponse(Response response) {
                                    latch.countDown();
                                    if (response != null && response.isSuccess()) {
                                        connection.freshActiveTime();
                                        successConnections.add(outDateConnectionId);
                                    }
                                }
                                
                                @Override
                                public void onException(Throwable e) {
                                    latch.countDown();
                                }
                            });
                            
                            Loggers.CONNECTION.info("[{}]send connection active request ", outDateConnectionId);
                        } else {
                            latch.countDown();
                        }
                        
                    } catch (ConnectionAlreadyClosedException e) {
                        latch.countDown();
                    } catch (Exception e) {
                        Loggers.CONNECTION.error("[{}]Error occurs when check client active detection ,error={}",
                                outDateConnectionId, e);
                        latch.countDown();
                    }
                }
                
                latch.await(3000L, TimeUnit.MILLISECONDS);
                Loggers.CONNECTION.info("Out dated connection check successCount={}", successConnections.size());
                
                for (String outDateConnectionId : outDatedConnections) {
                    if (!successConnections.contains(outDateConnectionId)) {
                        Loggers.CONNECTION.info("[{}]Unregister Out dated connection....", outDateConnectionId);
                        connectionManager.unregister(outDateConnectionId);
                    }
                }
            }
            
            //reset loader client
            if (isLoaderClient) {
                setLoadClient(-1);
                setRedirectAddress(null);
            }
            
            Loggers.CONNECTION.info("Connection check task end");
            
        } catch (Throwable e) {
            Loggers.CONNECTION.error("Error occurs during connection check... ", e);
        }
    }
    
    @Override
    public String getName() {
        return "mse";
    }
}
