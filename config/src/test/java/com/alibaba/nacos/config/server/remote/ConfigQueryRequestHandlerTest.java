package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.DiskUtil;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;

import static com.alibaba.nacos.api.common.Constants.ENCODE;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
public class ConfigQueryRequestHandlerTest {

    @InjectMocks
    private ConfigQueryRequestHandler configQueryRequestHandler;
    @Mock
    private PersistService persistService;
    @Mock
    private File file;


    @Before
    public void init() throws IOException {
        EnvUtil.setEnvironment(new StandardEnvironment());
        Mockito.mockStatic(ConfigCacheService.class);
        Mockito.mockStatic(PropertyUtil.class);
        Mockito.mockStatic(FileUtils.class);
        Mockito.mockStatic(DiskUtil.class);
        when(DiskUtil.targetFile(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(file);
        when(FileUtils.readFileToString(file,ENCODE)).thenReturn("content");
        when(file.exists()).thenReturn(true);
        ReflectionTestUtils.setField(configQueryRequestHandler, "persistService", persistService);
    }
    @Test
    public void testHandle() throws IOException, NacosException {
        ConfigQueryRequest configQueryRequest=new ConfigQueryRequest();
        configQueryRequest.setDataId("dataId");
        configQueryRequest.setGroup("group");
        final String groupKey = GroupKey2
                .getKey(configQueryRequest.getDataId(), configQueryRequest.getGroup(), configQueryRequest.getTenant());
        when(ConfigCacheService.tryReadLock(groupKey)).thenReturn(1);

        CacheItem cacheItem=new CacheItem(groupKey);
        cacheItem.setMd5("1");
        cacheItem.setLastModifiedTs(1L);
        when(ConfigCacheService.getContentCache(Mockito.any())).thenReturn(cacheItem);

        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        ConfigQueryResponse response = configQueryRequestHandler.handle(configQueryRequest, requestMeta);
        Assert.assertEquals(response.getContent(),"content");
    }


}