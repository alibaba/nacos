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
import com.alibaba.nacos.common.model.ResResult;
import com.alibaba.nacos.core.cluster.ServerNodeManager;
import com.alibaba.nacos.core.distributed.BizProcessor;
import com.alibaba.nacos.core.distributed.Config;
import com.alibaba.nacos.core.distributed.ConsistencyProtocol;
import com.alibaba.nacos.core.distributed.Datum;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.distributed.raft.RaftEvent;
import com.alibaba.nacos.core.notify.Event;
import com.alibaba.nacos.core.notify.NotifyManager;
import com.alibaba.nacos.core.notify.listener.Subscribe;
import com.alibaba.nacos.core.utils.SpringUtils;
import com.alipay.remoting.InvokeCallback;
import com.alipay.sofa.jraft.Node;
import com.alipay.sofa.jraft.entity.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
@ConditionalOnClass(name = {"com.alipay.sofa.jraft.NodeManager"})
@Component("RaftProtocol")
public class JRaftProtocol implements ConsistencyProtocol<RaftConfig> {

    private boolean isStart = false;

    private JRaftServer raftServer;

    private Node raftNode;

    private NacosStateMachine machine = new NacosStateMachine();

    private volatile Map<String, Object> metaData = new HashMap<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    @Override
    public void init(RaftConfig config) {

        NotifyManager.registerPublisher(RaftEvent::new, RaftEvent.class);

        this.raftServer = new JRaftServer(SpringUtils.getBean(ServerNodeManager.class), machine,
                new NacosAsyncProcessor(this));
        this.raftServer.init(config);
        this.raftServer.start();
        this.raftNode = this.raftServer.getNode();
        isStart = true;

        // There is only one consumer to ensure that the internal consumption
        // is sequential and there is no concurrent competition

        NotifyManager.registerSubscribe(new Subscribe<RaftEvent>() {
            @Override
            public void onEvent(RaftEvent event) {
                writeLock.lock();
                try {
                    final String leader = event.getLeader();
                    final long term = event.getTerm();
                    final List<String> raftClusterInfo = event.getRaftClusterInfo();
                    metaData.put("leader", leader);
                    metaData.put("term", term);
                    metaData.put("raftClusterInfo", raftClusterInfo);
                } finally {
                    writeLock.unlock();
                }
            }

            @Override
            public Class<? extends Event> subscribeType() {
                return RaftEvent.class;
            }
        });

    }

    @Override
    public Map<String, Object> protocolMetaData() {
        readLock.lock();
        try {
            return metaData;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <T> T metaData(String key) {
        return (T) metaData.get(key);
    }

    @Override
    public void registerBizProcessor(BizProcessor processor) {
        machine.registerBizProcessor(processor);
    }

    @Override
    public <T> T getData(String key) throws Exception {
        for (BizProcessor processor : machine.getProcessorMap().values()) {
            if (processor.interest(key)) {
                return processor.getData(key);
            }
        }
        return null;
    }

    @Override
    public boolean submit(Datum data) throws Exception {
        CompletableFuture<ResResult<Boolean>> future = submitAsync(data);
        return future.join().getData();
    }

    @Override
    public CompletableFuture<ResResult<Boolean>> submitAsync(Datum data) {
        final Throwable[] throwable = new Throwable[] { null };
        CompletableFuture<ResResult<Boolean>> future = new CompletableFuture<>();
        try {
            if (machine.isLeader()) {
                final Task task = new Task();
                task.setDone(new NacosClosure(data, status -> {
                    NacosClosure.NStatus nStatus = (NacosClosure.NStatus) status;
                    ResResult<Boolean> resResult = ResResult.<Boolean>builder()
                            .withCode(status.getCode()).withData(status.isOk())
                            .withErrMsg(status.getErrorMsg()).build();
                    if (Objects.nonNull(nStatus.getThrowable())) {
                        future.completeExceptionally(nStatus.getThrowable());
                    } else {
                        future.complete(resResult);
                    }
                }));
                task.setData(ByteBuffer.wrap(JSON.toJSONBytes(data)));
                raftNode.apply(task);
            } else {
                raftServer.getCliClientService().getRpcClient().invokeWithCallback(
                        raftServer.leaderIp(), data, new InvokeCallback() {
                            @Override
                            public void onResponse(Object o) {
                                ResResult<Boolean> resResult = (ResResult) o;
                                future.complete(resResult);
                            }

                            @Override
                            public void onException(Throwable e) {
                                throwable[0] = e;
                            }

                            @Override
                            public Executor getExecutor() {
                                return null;
                            }
                        }, 5000);
            }
        } catch (Throwable e) {
            throwable[0] = e;
        }
        if (Objects.nonNull(throwable[0])) {
            future.completeExceptionally(throwable[0]);
        }
        return future;
    }

    @Override
    public Class<? extends Config> configType() {
        return RaftConfig.class;
    }

    @Override
    public void shutdown() {
        if (isStart) {
            raftServer.shutdown();
        }
    }
}
