package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.utils.StringPool;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigChangeBatchListenRequestHandlerTest extends TestCase {

    @InjectMocks
    private ConfigChangeBatchListenRequestHandler configQueryRequestHandler;

    @InjectMocks
    private ConfigChangeListenContext configChangeListenContext;

    private RequestMeta requestMeta;


    @Before
    public void setUp() {
        configQueryRequestHandler = new ConfigChangeBatchListenRequestHandler();
        ReflectionTestUtils.setField(configQueryRequestHandler, "configChangeListenContext", configChangeListenContext);
        requestMeta = new RequestMeta();
        requestMeta.setClientIp("1.1.1.1");
        Mockito.mockStatic(ConfigCacheService.class);

    }

    @Test
    public void testHandle() {
        String dataId="dataId";
        String group="group";
        String tenant="tenant";
        String groupKey = GroupKey2
                .getKey(dataId, group, tenant);
        groupKey = StringPool.get(groupKey);
        when(ConfigCacheService.isUptodate(eq(groupKey),Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(false);
        ConfigBatchListenRequest configChangeListenRequest = new ConfigBatchListenRequest();
        configChangeListenRequest.addConfigListenContext(group, dataId, tenant, " ");
        try {
            ConfigChangeBatchListenResponse configChangeBatchListenResponse = configQueryRequestHandler.handle(configChangeListenRequest
                    , requestMeta);
            boolean hasChange = false;
            for (ConfigChangeBatchListenResponse.ConfigContext changedConfig : configChangeBatchListenResponse.getChangedConfigs()) {
                if (changedConfig.getDataId().equals(dataId)) {
                    hasChange = true;
                    break;
                }
            }
            assertTrue(hasChange);
        } catch (NacosException e) {
            e.printStackTrace();
        }
    }
}