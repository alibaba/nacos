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
package com.alibaba.nacos.common.util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * IO related tool methods
 *
 * @author nacos
 */
public class IoUtils {

    public static byte[] tryDecompress(InputStream raw) throws Exception {
        GZIPInputStream gis = null;
        ByteArrayOutputStream out = null;
        try {
            gis = new GZIPInputStream(raw);
            out = new ByteArrayOutputStream();
            IOUtils.copy(gis, out);
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                out.close();
            }
            if (gis != null) {
                gis.close();
            }
        }

        return null;
    }

    static private BufferedReader toBufferedReader(Reader reader) {
        return reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(
            reader);
    }

    public static void writeStringToFile(File file, String data, String encoding)
        throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data.getBytes(encoding));
            os.flush();
        } finally {
            if (null != os) {
                os.close();
            }
        }
    }

    static public List<String> readLines(Reader input) throws IOException {
        BufferedReader reader = toBufferedReader(input);
        List<String> list = new ArrayList<String>();
        String line = null;
        for (; ; ) {
            line = reader.readLine();
            if (null != line) {
                if (StringUtils.isNotEmpty(line)) {
                    list.add(line.trim());
                }
            } else {
                break;
            }
        }
        return list;
    }

}

