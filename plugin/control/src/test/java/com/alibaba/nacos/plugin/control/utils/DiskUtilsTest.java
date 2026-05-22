/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.utils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskUtilsTest {
    
    private static final String TMP_PATH =
        EnvUtils.getNacosHome() + File.separator + "data" + File.separator + "tmp" + File.separator;
    
    private static File testFile;
    
    @BeforeAll
    static void setup() throws IOException {
        testFile = Files.createTempFile("nacostmp", ".ut").toFile();
        ;
    }
    
    @AfterAll
    static void tearDown() throws IOException {
        testFile.deleteOnExit();
    }
    
    @Test
    void testReadFile() {
        assertNotNull(DiskUtils.readFile(testFile));
        assertNull(DiskUtils.readFile(new File("missing-file")));
    }
    
    @Test
    void testWriteFile() {
        assertTrue(
            DiskUtils.writeFile(testFile, "unit test".getBytes(StandardCharsets.UTF_8), false));
        assertEquals("unit test", DiskUtils.readFile(testFile));
        assertFalse(DiskUtils.writeFile(testFile.getParentFile(), "unit test".getBytes(
            StandardCharsets.UTF_8), false));
    }
    
    @Test
    void testDeleteQuietly() throws IOException {
        File tmpFile = Files.createTempFile(UUID.randomUUID().toString(), ".ut").toFile();
        DiskUtils.deleteQuietly(tmpFile);
        assertFalse(tmpFile.exists());
    }
    
    @Test
    void testReadFileConcurrentlyReturnsCorrectContent() throws Exception {
        // Two distinct payloads, each large enough that decoding is non-trivial. If readFile shares a
        // CharsetDecoder instance across threads, concurrent decodes will corrupt each other and the
        // returned string will not equal the file's actual content.
        File fileA = Files.createTempFile("nacos-disk-utils-concurrent-a", ".txt").toFile();
        File fileB = Files.createTempFile("nacos-disk-utils-concurrent-b", ".txt").toFile();
        fileA.deleteOnExit();
        fileB.deleteOnExit();
        String contentA = repeat("alpha-rule-", 1024);
        String contentB = repeat("beta-rule-", 1024);
        Files.write(fileA.toPath(), contentA.getBytes(StandardCharsets.UTF_8));
        Files.write(fileB.toPath(), contentB.getBytes(StandardCharsets.UTF_8));
        
        int threads = 16;
        int iterationsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Void>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                final boolean readA = i % 2 == 0;
                final File target = readA ? fileA : fileB;
                final String expected = readA ? contentA : contentB;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String actual = DiskUtils.readFile(target);
                        assertEquals(expected, actual,
                            "concurrent readFile returned corrupted content for "
                                + target.getName());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
    
    @Test
    void testReadFileWithMultiByteUtf8Content() throws IOException {
        // Regression: readFile must round-trip non-ASCII UTF-8 content.
        File f = Files.createTempFile("nacos-disk-utils-utf8", ".txt").toFile();
        f.deleteOnExit();
        String content = "限流规则-中文内容-αβγ-🚀";
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        assertEquals(content, DiskUtils.readFile(f));
    }
    
    @Test
    void testReadFileSequentialCallsAreIndependent() throws IOException {
        // Regression: a previously shared static CharsetDecoder kept state across calls. Each call must
        // observe a clean decoder regardless of what the previous call read.
        File f = Files.createTempFile("nacos-disk-utils-sequential", ".txt").toFile();
        f.deleteOnExit();
        String first = "first-payload-数据A";
        String second = "second-payload-数据B";
        Files.write(f.toPath(), first.getBytes(StandardCharsets.UTF_8));
        assertEquals(first, DiskUtils.readFile(f));
        Files.write(f.toPath(), second.getBytes(StandardCharsets.UTF_8));
        assertEquals(second, DiskUtils.readFile(f));
    }
    
    @Test
    void testReadFileWithMultiByteUtf8AcrossChunkBoundary() throws IOException {
        // Reproduces the corruption that happens when a multi-byte UTF-8 character straddles the
        // 4096-byte read-buffer boundary. The decode loop reads the first chunk, the decoder
        // reports UNDERFLOW because the trailing bytes are an incomplete UTF-8 sequence, and the
        // unconsumed bytes are dropped if the buffer is cleared instead of compacted.
        //
        // Layout (UTF-8): 4094 ASCII bytes + 3-byte CJK ('中', E4 B8 AD) + 200 ASCII bytes.
        // The CJK character occupies bytes 4094..4096, so its trailing byte falls in the second
        // chunk. With buffer.clear() the leading two bytes are discarded and the second chunk
        // starts at the orphaned continuation byte AD, producing a malformed sequence and
        // truncating the rest of the file. With buffer.compact() the leading bytes are preserved
        // and the character round-trips cleanly.
        File f = Files.createTempFile("nacos-disk-utils-chunk-boundary", ".txt").toFile();
        f.deleteOnExit();
        StringBuilder content = new StringBuilder(4094 + 1 + 200);
        for (int i = 0; i < 4094; i++) {
            content.append('a');
        }
        content.append('中'); // '中', encodes to E4 B8 AD in UTF-8
        for (int i = 0; i < 200; i++) {
            content.append('b');
        }
        String expected = content.toString();
        byte[] encoded = expected.getBytes(StandardCharsets.UTF_8);
        // Sanity-check the layout so future edits to the test cannot accidentally make it pass.
        assertEquals(4094 + 3 + 200, encoded.length);
        assertEquals((byte) 0xe4, encoded[4094]);
        assertEquals((byte) 0xb8, encoded[4095]);
        assertEquals((byte) 0xad, encoded[4096]);
        Files.write(f.toPath(), encoded);
        
        assertEquals(expected, DiskUtils.readFile(f));
    }
    
    private static String repeat(String token, int times) {
        StringBuilder sb = new StringBuilder(token.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(token).append(i);
        }
        return sb.toString();
    }
}
