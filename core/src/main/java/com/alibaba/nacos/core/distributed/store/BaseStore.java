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

package com.alibaba.nacos.core.distributed.store;

import com.alibaba.nacos.core.executor.ExecutorFactory;
import org.apache.commons.lang3.BooleanUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
abstract class BaseStore implements Store {

    private CommandAnalyzer commandAnalyzer;

    private final ForkJoinPool executor;

    private final int openParller = 50;

    public BaseStore(String name) {
        executor = ExecutorFactory.newForkJoinPool(name);
    }

    @Override
    public synchronized final void initCommandAnalyze(CommandAnalyzer analyzer) {
        if (Objects.isNull(commandAnalyzer)) {
            commandAnalyzer = analyzer;
        }
    }

    @Override
    public boolean operate(Record data, String command) throws Exception {
        checkAnalyzer();
        return commandAnalyzer.analyze(command).apply(data);
    }

    @Override
    public boolean batchOperate(Map data) throws Exception {
        checkAnalyzer();
        List<Boolean> result = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Record>> entry : ((Map<String, ArrayList<Record>>) data).entrySet()) {
            final Function<Record, Boolean> function = commandAnalyzer.analyze(entry.getKey());
            final ArrayList<Record> records = entry.getValue();
            final Stream<Record> stream = records.size() > openParller ? records.parallelStream() : records.stream();
            List<Boolean> subResult = stream
                    .map(record -> {
                        try {
                            return function.apply(record);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
            result.add(BooleanUtils.and(subResult.toArray(new Boolean[0])));
        }
        return BooleanUtils.and(result.toArray(new Boolean[0]));
    }

    private void checkAnalyzer() {
        if (Objects.isNull(commandAnalyzer)) {
            throw new NullPointerException("CommandAnalyzer is null, please set CommandAnalyzer");
        }
    }

    private static class OperateJob extends RecursiveAction {

        private final ArrayList<Record> records;
        private final Function<Record, Boolean> function;

        public OperateJob(ArrayList<Record> records, Function<Record, Boolean> function) {
            this.records = records;
            this.function = function;
        }

        @Override
        protected void compute() {

        }
    }
}
