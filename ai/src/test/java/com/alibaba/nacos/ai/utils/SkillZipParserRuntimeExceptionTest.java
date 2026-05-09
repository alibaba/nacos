/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression tests for security-limit exception type in {@link SkillZipParser}.
 *
 * <p>Before the fix, security-limit violations inside {@code unzipToEntries}
 * ({@link SkillZipParser}'s {@code MAX_ZIP_ENTRIES} and
 * {@code MAX_TOTAL_UNCOMPRESSED_BYTES} guards) were signalled by throwing
 * {@link IOException}, which semantically implies an underlying I/O failure
 * rather than invalid user input. The fix switches them to
 * {@link NacosRuntimeException} with {@link ErrorCode#PARAMETER_VALIDATE_ERROR},
 * mirroring the pattern already applied to {@code AgentSpecZipParser}.
 *
 * <p>These tests lock in that contract:
 * <ul>
 *   <li>White-box: reflectively invoke the private {@code unzipToEntries} and
 *       assert the thrown exception is exactly a {@link NacosRuntimeException}
 *       (not an {@link IOException}) with the expected {@code errCode}.</li>
 *   <li>Black-box: go through the public {@link SkillZipParser#parseSkillFromZip(byte[], String)}
 *       entry point and verify the outer catch still maps the runtime exception
 *       into a {@link NacosApiException} so the HTTP layer behaviour is unchanged.</li>
 * </ul>
 *
 * @author nacos
 */
class SkillZipParserRuntimeExceptionTest {
    
    private static final String NAMESPACE_ID = "test-ns";
    
    private static final String VALID_SKILL_MD =
        "---\nname: sample\ndescription: sample skill\n---\n\nbody\n";
    
    // -------- White-box: exact exception type + errCode on the private method --------
    
    @Test
    void testUnzipTooManyEntriesThrowsNacosRuntimeException() throws Exception {
        // Build a zip containing more than MAX_ZIP_ENTRIES (=500) entries.
        byte[] zipBytes = buildZipWithEntryCount(600);
        
        Throwable cause = invokeUnzipToEntriesExpectingThrow(zipBytes);
        
        assertInstanceOf(NacosRuntimeException.class, cause,
            "Entry-count guard must throw NacosRuntimeException (not IOException); actual: "
                + cause.getClass().getName());
        assertFalse(cause instanceof IOException,
            "NacosRuntimeException is a RuntimeException and must not be an IOException");
        NacosRuntimeException nre = (NacosRuntimeException) cause;
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), nre.getErrCode(),
            "Expected errCode PARAMETER_VALIDATE_ERROR for security-limit violation");
        assertTrue(nre.getMessage().contains("too many entries"),
            "Exception message should describe the entry-count violation, got: "
                + nre.getMessage());
    }
    
    @Test
    void testUnzipUncompressedSizeExceedsThrowsNacosRuntimeException() throws Exception {
        // Build a zip whose decompressed size is > MAX_TOTAL_UNCOMPRESSED_BYTES (50 MB).
        // 60 entries of 1 MB of zeros decompresses to 60 MB but compresses very small.
        byte[] zipBytes = buildZipWithUncompressedSize(60);
        
        Throwable cause = invokeUnzipToEntriesExpectingThrow(zipBytes);
        
        assertInstanceOf(NacosRuntimeException.class, cause,
            "Uncompressed-size guard must throw NacosRuntimeException (not IOException); actual: "
                + cause.getClass().getName());
        assertFalse(cause instanceof IOException,
            "NacosRuntimeException is a RuntimeException and must not be an IOException");
        NacosRuntimeException nre = (NacosRuntimeException) cause;
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(), nre.getErrCode(),
            "Expected errCode PARAMETER_VALIDATE_ERROR for security-limit violation");
        assertTrue(nre.getMessage().contains("decompressed size exceeds limit"),
            "Exception message should describe the decompressed-size violation, got: "
                + nre.getMessage());
    }
    
    // -------- Black-box: public entry still yields NacosApiException for the HTTP layer --------
    
    @Test
    void testParseSkillFromZipTooManyEntriesStillMappedToNacosApiException() throws IOException {
        byte[] zipBytes = buildZipWithEntryCount(600);
        
        try {
            SkillZipParser.parseSkillFromZip(zipBytes, NAMESPACE_ID);
            fail("Expected the too-many-entries zip to be rejected, but parsing succeeded");
        } catch (NacosApiException e) {
            // The outer catch (Exception e) inside parseSkillFromZip must still catch the runtime
            // exception and translate it into NacosApiException, so the HTTP layer is unaffected.
            assertTrue(e.getMessage().contains("too many entries"),
                "NacosApiException should propagate the underlying message, got: "
                    + e.getMessage());
        }
    }
    
    @Test
    void testParseSkillFromZipUncompressedSizeExceedsStillMappedToNacosApiException()
        throws IOException {
        byte[] zipBytes = buildZipWithUncompressedSize(60);
        
        try {
            SkillZipParser.parseSkillFromZip(zipBytes, NAMESPACE_ID);
            fail("Expected the zip-bomb zip to be rejected, but parsing succeeded");
        } catch (NacosApiException e) {
            assertTrue(e.getMessage().contains("decompressed size exceeds limit"),
                "NacosApiException should propagate the underlying message, got: "
                    + e.getMessage());
        }
    }
    
    // -------- Helpers --------
    
    /**
     * Reflectively invoke {@code SkillZipParser#unzipToEntries(byte[])} and return the root cause
     * of the wrapping {@link InvocationTargetException}, or fail the test if no exception was thrown.
     */
    private static Throwable invokeUnzipToEntriesExpectingThrow(byte[] zipBytes) throws Exception {
        Method unzip = SkillZipParser.class.getDeclaredMethod("unzipToEntries", byte[].class);
        unzip.setAccessible(true);
        try {
            unzip.invoke(null, (Object) zipBytes);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            assertNotNull(cause, "InvocationTargetException must carry a cause");
            return cause;
        }
        fail("Expected unzipToEntries to throw, but it returned normally");
        return null;
    }
    
    private static byte[] buildZipWithEntryCount(int entryCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "SKILL.md", VALID_SKILL_MD.getBytes(StandardCharsets.UTF_8));
            byte[] small = "x".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < entryCount; i++) {
                addZipEntry(zos, "assets/entry_" + i + ".txt", small);
            }
        }
        return baos.toByteArray();
    }
    
    private static byte[] buildZipWithUncompressedSize(int oneMbEntries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addZipEntry(zos, "SKILL.md", VALID_SKILL_MD.getBytes(StandardCharsets.UTF_8));
            byte[] oneMegabyteOfZeros = new byte[1024 * 1024];
            for (int i = 0; i < oneMbEntries; i++) {
                addZipEntry(zos, "assets/blob_" + i + ".bin", oneMegabyteOfZeros);
            }
        }
        return baos.toByteArray();
    }
    
    private static void addZipEntry(ZipOutputStream zos, String name, byte[] data)
        throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
