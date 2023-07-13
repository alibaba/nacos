/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.naming.core.v2.service;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.constants.Constants;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * {@link ClientOperationService} unit tests.
 *
 * @author blake.qiu
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientOperationServiceTest {
    
    @Test
    public void getPublishInfoAutoGenerateInsId() {
        ClientOperationServiceProxy clientOperationService = new ClientOperationServiceProxy(null, null);
        Instance instance = new Instance();
        String customInsId = "1.1.1.1_custom";
        instance.setInstanceId(customInsId);
        InstancePublishInfo publishInfo1 = clientOperationService.getPublishInfo(instance);
        assert customInsId.equals(publishInfo1.getExtendDatum().get(Constants.INSTANCE_ID));
        
        InstancePublishInfo publishInfo2 = clientOperationService.getPublishInfo(new Instance());
        String insId = (String) publishInfo2.getExtendDatum().get(Constants.INSTANCE_ID);
        assert StringUtils.isNotEmpty(insId);
    }
}
