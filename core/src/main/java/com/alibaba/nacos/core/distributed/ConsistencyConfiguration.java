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

package com.alibaba.nacos.core.distributed;

import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.cluster.MemberManager;
import com.alibaba.nacos.core.distributed.distro.DistroProtocol;
import com.alibaba.nacos.core.distributed.raft.JRaftProtocol;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@Configuration
public class ConsistencyConfiguration {

    @Bean(value = "eventualAgreementProtocol")
    public APProtocol eventualAgreementProtocol(MemberManager memberManager) {
        final APProtocol protocol = getProtocol(APProtocol.class, () -> new DistroProtocol(memberManager));
        return protocol;
    }

    @Bean(value = "strongAgreementProtocol")
    public CPProtocol strongAgreementProtocol(MemberManager memberManager) {
        final CPProtocol protocol = getProtocol(CPProtocol.class, () -> new JRaftProtocol(memberManager));
        return protocol;
    }

    private <T> T getProtocol(Class<T> cls, Supplier<T> builder) {
        ServiceLoader<T> protocols = ServiceLoader
                .load(cls);

        // Select only the first implementation

        Iterator<T> iterator = protocols.iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        } else {
            return builder.get();
        }
    }

}
