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
package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.naming.core.IpAddress;

import java.util.List;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
public class NoneSelector extends com.alibaba.nacos.api.selector.AbstractSelector implements Selector {

    public NoneSelector() {
        this.setType(SelectorType.none.name());
    }

    @Override
    public List<IpAddress> select(String consumer, List<IpAddress> providers) {
        return providers;
    }
}
