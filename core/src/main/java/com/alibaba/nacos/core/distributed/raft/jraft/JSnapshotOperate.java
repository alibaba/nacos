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
import com.alipay.sofa.jraft.Closure;
import com.alipay.sofa.jraft.entity.LocalFileMetaOutter;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotReader;
import com.alipay.sofa.jraft.storage.snapshot.SnapshotWriter;
import com.google.protobuf.ByteString;

/**
 * Custom snapshot operation interface
 * Discovery via SPI
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public interface JSnapshotOperate {

    /**
     * do snapshot save operation
     *
     * @param writer {@link SnapshotWriter}
     * @param done {@link Closure}
     */
    void onSnapshotSave(SnapshotWriter writer, Closure done);

    /**
     * do snapshot load operation
     *
     * @param reader {@link SnapshotReader}
     * @return operation label
     */
    boolean onSnapshotLoad(SnapshotReader reader);

    /**
     * Metadata information for snapshot files
     *
     * @param metadata meta data
     * @param <T> type
     * @return {@link LocalFileMetaOutter.LocalFileMeta}
     */
    default <T> LocalFileMetaOutter.LocalFileMeta buildMetadata(final T metadata) {
        return metadata == null ? null
                : LocalFileMetaOutter.LocalFileMeta.newBuilder()
                .setUserMeta(ByteString.copyFrom(JSON.toJSONBytes(metadata)))
                .build();
    }

}
