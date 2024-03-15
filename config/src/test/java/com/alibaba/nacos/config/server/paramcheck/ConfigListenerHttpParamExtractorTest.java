/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.paramcheck;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.api.common.Constants.LINE_SEPARATOR;
import static com.alibaba.nacos.api.common.Constants.WORD_SEPARATOR;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)

public class ConfigListenerHttpParamExtractorTest {
    
    ConfigListenerHttpParamExtractor configListenerHttpParamExtractor;
    
    @Mock
    HttpServletRequest httpServletRequest;
    
    @Test
    public void testNormal() {
        String listenerConfigsString = getListenerConfigsString();
        Mockito.when(httpServletRequest.getParameter(eq("Listening-Configs"))).thenReturn(listenerConfigsString);
        configListenerHttpParamExtractor = new ConfigListenerHttpParamExtractor();
        configListenerHttpParamExtractor.extractParam(httpServletRequest);
    }
    
    @Test
    public void testError() {
        String listenerConfigsString = getErrorListenerConfigsString();
        Mockito.when(httpServletRequest.getParameter(eq("Listening-Configs"))).thenReturn(listenerConfigsString);
        configListenerHttpParamExtractor = new ConfigListenerHttpParamExtractor();
        try {
            configListenerHttpParamExtractor.extractParam(httpServletRequest);
            Assert.assertTrue(false);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            Assert.assertTrue(throwable instanceof IllegalArgumentException);
        }
    }
    
    private String getListenerConfigsString() {
        ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setDataId("2345678901");
        configInfo1.setGroup("1234445");
        configInfo1.setMd5("234567");
        configInfo1.setTenant("222345");
        ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setDataId("2345678902");
        configInfo2.setGroup("1234445");
        configInfo2.setMd5(null);
        configInfo2.setTenant(null);
        ConfigInfo configInfo3 = new ConfigInfo();
        configInfo3.setDataId("2345678903");
        configInfo3.setGroup("1234445");
        configInfo3.setMd5("12345");
        configInfo3.setTenant(null);
        ConfigInfo configInfo4 = new ConfigInfo();
        configInfo4.setDataId("234567844");
        configInfo4.setGroup("1234445");
        configInfo4.setMd5("12345");
        configInfo4.setTenant(null);
        List<ConfigInfo> configInfoList = Arrays.asList(configInfo4, configInfo3, configInfo2, configInfo1);
        StringBuilder sb = new StringBuilder();
        for (ConfigInfo configInfo : configInfoList) {
            sb.append(configInfo.getDataId()).append(WORD_SEPARATOR);
            sb.append(configInfo.getGroup()).append(WORD_SEPARATOR);
            if (StringUtils.isBlank(configInfo.getTenant())) {
                sb.append(configInfo.getMd5()).append(LINE_SEPARATOR);
            } else {
                sb.append(configInfo.getMd5()).append(WORD_SEPARATOR);
                sb.append(configInfo.getTenant()).append(LINE_SEPARATOR);
            }
        }
        
        return sb.toString();
        
    }
    
    private String getErrorListenerConfigsString() {
        ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setDataId("2345678901");
        
        List<ConfigInfo> configInfoList = Arrays.asList(configInfo1);
        StringBuilder sb = new StringBuilder();
        for (ConfigInfo configInfo : configInfoList) {
            sb.append(configInfo.getDataId()).append(WORD_SEPARATOR);
        }
        
        return sb.toString();
        
    }
}