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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.transaction.ConditionOnEmbedStoreType;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
@Conditional(ConditionOnEmbedStoreType.class)
public class DistributeClearJudge implements ClearJudgment {

    private volatile boolean can = false;

    @PostConstruct
    protected void init() {

        final MemberManager memberManager = ApplicationUtils.getBean(MemberManager.class);

        ApplicationUtils.getBean(CPProtocol.class)
                .protocolMetaData().subscribe(Constants.CONFIG_MODEL_RAFT_GROUP, com.alibaba.nacos.consistency.cp.Constants.LEADER_META_DATA, new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                final String currentLeader = String.valueOf(arg);
                can = Objects.equals(currentLeader, memberManager.self().getAddress());
            }
        });
    }

    @Override
    public boolean canExecute() {
        return can;
    }
}
