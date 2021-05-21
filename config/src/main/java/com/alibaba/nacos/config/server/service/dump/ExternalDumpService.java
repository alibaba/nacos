/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * External dump service.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Conditional(ConditionOnExternalStorage.class)
@Component
@DependsOn({"rpcConfigChangeNotifier"})
public class ExternalDumpService extends DumpService {
    
    /**
     * Here you inject the dependent objects constructively, ensuring that some of the dependent functionality is
     * initialized ahead of time.
     *
     * @param persistService {@link PersistService}
     * @param memberManager  {@link ServerMemberManager}
     */
    public ExternalDumpService(PersistService persistService, ServerMemberManager memberManager) {
        super(persistService, memberManager);
    }
    
    @PostConstruct
    @Override
    protected void init() throws Throwable {
        dumpOperate(processor, dumpAllProcessor, dumpAllBetaProcessor, dumpAllTagProcessor);
    }
    
    @Override
    protected boolean canExecute() {
        return memberManager.isFirstIp();
    }
}
