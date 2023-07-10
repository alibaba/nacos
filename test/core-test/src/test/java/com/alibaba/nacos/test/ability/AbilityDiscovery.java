package com.alibaba.nacos.test.ability;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.request.SetupAckRequest;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.common.ability.AbstractAbilityControlManager;
import com.alibaba.nacos.common.ability.discover.NacosAbilityManagerHolder;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import com.alibaba.nacos.common.remote.client.ServerRequestHandler;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RequestFilters;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.core.remote.RequestHandlerRegistry;
import com.alibaba.nacos.test.ability.component.TestServerAbilityControlManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@SuppressWarnings("all")
public class AbilityDiscovery {
    
    @LocalServerPort
    private int port;
    
    @Resource
    private RequestHandlerRegistry requestHandlerRegistry;
    
    @Resource
    private RequestFilters filters;
    
    @Resource
    private ConnectionManager connectionManager;
    
    private RpcClient client;
    
    private ConfigService configService;
    
    private AbstractAbilityControlManager oldInstance;
    
    /**
     * test server judge client abilities
     */
    private volatile boolean serverSuccess = false;
    
    private volatile boolean clientSuccess = false;
    
    private Field abstractAbilityControlManager;
    
    private Field registryHandlerFields;
    
    private Field currentConnField;
    
    @Before
    public void setup() throws NoSuchFieldException, IllegalAccessException, NacosException {
        // load class
        oldInstance = NacosAbilityManagerHolder.getInstance();
        
        // replace
        abstractAbilityControlManager = NacosAbilityManagerHolder.class
                .getDeclaredField("abstractAbilityControlManager");
        abstractAbilityControlManager.setAccessible(true);
        abstractAbilityControlManager.set(NacosAbilityManagerHolder.class, new TestServerAbilityControlManager());
        
        // get registry field
        registryHandlerFields = RequestHandlerRegistry.class.getDeclaredField("registryHandlers");
        registryHandlerFields.setAccessible(true);
        
        // currentConn
        currentConnField = RpcClient.class.getDeclaredField("currentConnection");
        currentConnField.setAccessible(true);
        
        // init config service
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + port);
        configService = NacosFactory.createConfigService(properties);
    
        // init client
        client = RpcClientFactory.createClient(UUID.randomUUID().toString(), ConnectionType.GRPC, new HashMap<>());
        client.serverListFactory(new ServerListFactory() {
            @Override
            public String genNextServer() {
                return "127.0.0.1:" + port;
            }
        
            @Override
            public String getCurrentServer() {
                return "127.0.0.1:" + port;
            }
        
            @Override
            public List<String> getServerList() {
                return Collections.singletonList("127.0.0.1:" + port);
            }
        });
        // connect to server
        client.start();
    }
    
    @Test
    public void testClientDiscovery() throws NacosException {
        // client judge ability
        Assert.assertEquals(client.getConnectionAbility(AbilityKey.TEST_1), AbilityStatus.SUPPORTED);
        Assert.assertEquals(client.getConnectionAbility(AbilityKey.TEST_2), AbilityStatus.NOT_SUPPORTED);
    }
    
    @Test
    public void testServerDiscoveryAndJudge() throws Exception {
        Map<String, RequestHandler> handlers = (Map<String, RequestHandler>) registryHandlerFields
                .get(requestHandlerRegistry);
        
        // set handler
        RequestHandler oldRequestHandler = handlers.remove(ConfigQueryRequest.class.getSimpleName());
        handlers.put(ConfigQueryRequest.class.getSimpleName(), new ClientRequestHandler(filters));
        configService.getConfig("test", "DEFAULT_GROUP", 2000);
        // wait server invoke
        Thread.sleep(3000);
        Assert.assertTrue(serverSuccess);
        // recover
        handlers.remove(ConfigQueryRequest.class.getSimpleName());
        handlers.put(ConfigQueryRequest.class.getSimpleName(), oldRequestHandler);
    }
    
    @Test
    public void testClientJudge() throws Exception {
        // register
        client.registerServerRequestHandler(new ServerRequestHandler() {
            @Override
            public Response requestReply(Request request, Connection connection) {
                if (connection.getConnectionAbility(AbilityKey.TEST_1).equals(AbilityStatus.SUPPORTED) && connection
                        .getConnectionAbility(AbilityKey.TEST_2).equals(AbilityStatus.NOT_SUPPORTED)) {
                    clientSuccess = true;
                }
                return new Response(){};
            }
        });
        
        // get id
        Connection conn = (Connection) currentConnField.get(client);
        
        com.alibaba.nacos.core.remote.Connection connection = connectionManager.getConnection(conn.getConnectionId());
        try {
            connection.request(new SetupAckRequest(), 2000L);
        } catch (NacosException e) {
            // nothing to do
        }
        
        // wait client react
        Thread.sleep(4000);
        Assert.assertTrue(clientSuccess);
    }
    
    @After
    public void recover() throws IllegalAccessException, NacosException {
        abstractAbilityControlManager.set(NacosAbilityManagerHolder.class, oldInstance);
        client.shutdown();
    }
    
    /**
     * just to test ability
     */
    class ClientRequestHandler extends RequestHandler<ConfigQueryRequest, ConfigQueryResponse> {
        
        public ClientRequestHandler(RequestFilters requestFilters) throws NoSuchFieldException, IllegalAccessException {
            Field declaredField = RequestHandler.class.getDeclaredField("requestFilters");
            declaredField.setAccessible(true);
            declaredField.set(this, requestFilters);
        }
        
        @Override
        public ConfigQueryResponse handle(ConfigQueryRequest request, RequestMeta meta) throws NacosException {
            if (meta.getConnectionAbility(AbilityKey.TEST_1).equals(AbilityStatus.SUPPORTED) && meta
                    .getConnectionAbility(AbilityKey.TEST_2).equals(AbilityStatus.NOT_SUPPORTED)) {
                serverSuccess = true;
            }
            return new ConfigQueryResponse();
        }
    }
}
