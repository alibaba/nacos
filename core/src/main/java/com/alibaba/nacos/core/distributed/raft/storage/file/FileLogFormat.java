/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.distributed.raft.storage.file;

import com.alipay.sofa.jraft.util.CrcUtil;

import java.io.IOException;
import java.util.Locale;
import java.util.zip.CRC32;

final class FileLogFormat {
    
    static final short VERSION = 1;
    
    static final int RECORD_MAGIC = 0x4e4c5231;
    
    static final int SEGMENT_MAGIC = 0x4e4c5331;
    
    static final int MANIFEST_MAGIC = 0x4e4c4d31;
    
    static final int RECORD_HEADER_SIZE = 16;
    
    static final int RECORD_OVERHEAD = RECORD_HEADER_SIZE + Long.BYTES;
    
    static final int SEGMENT_HEADER_SIZE = 40;
    
    static final int MANIFEST_HEADER_SIZE = 52;
    
    static final int SEGMENT_META_SIZE = 40;
    
    static final int INDEX_ENTRY_SIZE = 12;
    
    private static final int FILE_ID_WIDTH = 20;
    
    private FileLogFormat() {
    }
    
    static int crc32(byte[] bytes, int offset, int length) {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes, offset, length);
        return (int) crc32.getValue();
    }
    
    static long crc64(byte[] bytes, int offset, int length) {
        return CrcUtil.crc64(bytes, offset, length);
    }
    
    static String segmentName(long segmentId) {
        return String.format(Locale.ROOT, "%0" + FILE_ID_WIDTH + "d.seg", segmentId);
    }
    
    static String indexName(long segmentId) {
        return String.format(Locale.ROOT, "%0" + FILE_ID_WIDTH + "d.idx", segmentId);
    }
    
    static Long parseFileId(String name, String suffix) throws IOException {
        if (name.length() != FILE_ID_WIDTH + suffix.length() || !name.endsWith(suffix)) {
            return null;
        }
        for (int i = 0; i < FILE_ID_WIDTH; i++) {
            char digit = name.charAt(i);
            if (digit < '0' || digit > '9') {
                return null;
            }
        }
        try {
            return Long.parseLong(name.substring(0, FILE_ID_WIDTH));
        } catch (NumberFormatException e) {
            throw new IOException("File ID overflow: " + name, e);
        }
    }
}
