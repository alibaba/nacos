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

package com.alibaba.nacos.core.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.consistency.ProtocolMetaData;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.utils.Commons;
import com.alibaba.nacos.core.utils.RestResultUtils;
import java.io.Serializable;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@RequestMapping(Commons.NACOS_CORE_CONTEXT + "/protocol")
@SuppressWarnings("all")
public class ProtocolController {

    @Autowired
    private CPProtocol cpProtocol;

    @Autowired
    private APProtocol apProtocol;

    @GetMapping
    public RestResult<DataContainer> protocolMetaData() {
        final DataContainer container = new DataContainer();
        container.setAp(Pair.with("AP", apProtocol.protocolMetaData()));
        container.setCp(Pair.with("CP", cpProtocol.protocolMetaData()));
        return RestResultUtils.success(container);
    }

    private static class DataContainer implements Serializable {

        private Pair<String, ProtocolMetaData> cp;
        private Pair<String, ProtocolMetaData> ap;

        public Pair<String, ProtocolMetaData> getCp() {
            return cp;
        }

        public void setCp(Pair<String, ProtocolMetaData> cp) {
            this.cp = cp;
        }

        public Pair<String, ProtocolMetaData> getAp() {
            return ap;
        }

        public void setAp(Pair<String, ProtocolMetaData> ap) {
            this.ap = ap;
        }
    }

}
