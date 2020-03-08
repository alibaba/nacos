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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.core.distributed.raft.utils.JLog;
import com.alibaba.nacos.core.utils.ExceptionUtil;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.Iterator;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.error.RaftError;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NacosStateMachine extends AbstractStateMachine {

    private Serializer serializer;

    public NacosStateMachine(Serializer serializer, JRaftServer server, LogProcessor4CP processor) {
        super(server, processor);
        this.serializer = serializer;
    }

    @Override
    public void onApply(Iterator iter) {
        int index = 0;
        int applied = 0;
        try {
            while (iter.hasNext()) {
                JLog log = null;
                NacosClosure closure = null;
                Status status = Status.OK();
                try {
                    if (iter.done() != null) {
                        closure = (NacosClosure) iter.done();
                        log = closure.getLog();
                    } else {
                        final ByteBuffer data = iter.getData();
                        log = serializer.deSerialize(data.array(), JLog.class);
                    }

                    Loggers.RAFT.debug("receive log : {}", log);

                    // read request

                    if (Objects.equals(JLog.JLogOperaton.READ_OPERATION, log.getOperaton())) {
                        raftRead(closure, log);
                    } else {
                        try {

                            boolean result = processor.onApply(log);

                            if (Objects.nonNull(closure)) {
                                closure.setObject(result);
                            }

                        } catch (Throwable t) {

                            // TODO 这里的处理需要考虑下，如果业务层能够处理错误避免一致性问题，则处理，
                            //  否则还是需要抛出交给状态机
                            status = new Status(RaftError.UNKNOWN, t.getMessage());
                            processor.onError(t);
                        }
                    }
                } catch (Throwable e) {
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

        } catch (Throwable t) {
            Loggers.RAFT.error("StateMachine meet critical error: {}.", ExceptionUtil.getAllExceptionMsg(t));
            iter.setErrorAndRollback(index - applied, new Status(RaftError.ESTATEMACHINE,
                    "StateMachine meet critical error: %s.", t.getMessage()));
        }
    }

    private void raftRead(NacosClosure closure, JLog log) {
        final GetRequest request = GetRequest.builder()
                .biz(log.getBiz())
                .ctx(log.getData())
                .build();
        GetResponse<Object> result = processor.getData(request);
        if (Objects.nonNull(closure)) {
            closure.setObject(result);
        }
    }

}
