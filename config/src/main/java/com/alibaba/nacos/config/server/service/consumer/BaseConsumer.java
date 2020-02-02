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

package com.alibaba.nacos.config.server.service.consumer;

import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.config.server.service.PersistService;
import com.alibaba.nacos.core.distributed.Datum;
import com.alibaba.nacos.core.utils.ResResultUtils;
import com.alibaba.nacos.core.utils.SerializeFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public abstract class BaseConsumer<T> implements ConfigConsumer {

    private SerializeFactory.Serializer serializer = SerializeFactory
            .getSerializer(SerializeFactory.JSON_INDEX);

    @Autowired
    protected PersistService persistService;

    @Override
    public ResResult<Boolean> onAccept(Datum data) {
        final byte[] source = data.getData();
        process(serializer.deSerialize(source, data.getClassName()));
        return ResResultUtils.success();
    }

    /**
     * The actual processing logic
     *
     * @param t data
     */
    protected abstract void process(T t);
}
