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

package com.alibaba.nacos.core.distributed.raft.jraft;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.core.distributed.BizProcessor;
import com.alibaba.nacos.core.distributed.Datum;
import com.alibaba.nacos.core.distributed.NDatum;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosStateMachine extends BaseStateMachine {

    @Override
    public void onApply(Iterator iter) {
        int index = 0;
        int applied = 0;
        try {
            while (iter.hasNext()) {
                Status status = Status.OK();
                Datum datum = null;
                NacosClosure closure = null;
                try {
                    if (iter.done() != null) {
                        closure = (NacosClosure) iter.done();
                        datum = closure.getDatum();
                    }
                    else {
                        final ByteBuffer data = iter.getData();
                        datum = JSON.parseObject(data.array(), NDatum.class);
                    }
                    // For each transaction, according to the different processing of
                    // the key to the callback interface

                    for (Map.Entry<String, BizProcessor> entry : processorMap.entrySet()) {
                        final BizProcessor processor = entry.getValue();
                        if (processor.interest(datum.getKey())) {
                            try {
                                processor.onApply(datum);
                            }
                            catch (Throwable e) {
                                status = new Status(RaftError.UNKNOWN,
                                        "Exception handling within a transaction : %s",
                                        e);
                                Loggers.RAFT.error("BizProcessor when onApply has some error",
                                        e);
                            }
                        }
                    }
                }
                catch (Throwable e) {
                    index++;
                    throw e;
                }
                if (Objects.nonNull(closure)) {
                    closure.run(status);
                }
                applied++;
                index++;
                iter.next();
            }
        }
        catch (Throwable t) {
            t.printStackTrace();
            iter.setErrorAndRollback(index - applied, new Status(RaftError.ESTATEMACHINE,
                    "StateMachine meet critical error: %s.", t.getMessage()));
        }
    }

}
