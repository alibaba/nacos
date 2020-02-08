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
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.LogProcessor;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;

import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosStateMachine extends AbstractStateMachine {

    public NacosStateMachine(JRaftServer server, LogProcessor processor) {
        super(server, processor);
    }

    @Override
    public void onApply(Iterator iter) {
        int index = 0;
        int applied = 0;
        try {
            while (iter.hasNext()) {
                Log log = null;
                NacosClosure closure = null;
                try {
                    if (iter.done() != null) {
                        closure = (NacosClosure) iter.done();
                        log = closure.getLog();
                    }
                    else {
                        final ByteBuffer data = iter.getData();
                        log = JSON.parseObject(data.array(), NLog.class);
                    }

                    Loggers.RAFT.info("receive datum : {}", JSON.toJSONString(log));
                    processor.onApply(log);
                }
                catch (Throwable e) {
                    index++;
                    throw e;
                }
                applied++;
                index++;
                iter.next();
            }

        }
        catch (Throwable t) {
            Loggers.RAFT.error("StateMachine meet critical error: {}.", ExceptionUtil.getAllExceptionMsg(t));
            iter.setErrorAndRollback(index - applied, new Status(RaftError.ESTATEMACHINE,
                    "StateMachine meet critical error: %s.", t.getMessage()));
        }
    }

}
