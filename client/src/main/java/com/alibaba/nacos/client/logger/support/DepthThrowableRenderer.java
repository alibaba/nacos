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
/*
 * Copyright 2014 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.alibaba.nacos.client.logger.support;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;

import org.apache.log4j.spi.ThrowableRenderer;

/**
 * 针对 Log4j 1.2.16 及以上版本，提供对异常栈的深度控制
 *
 * @author zhuyong 2014年9月19日 上午10:31:48
 */
public final class DepthThrowableRenderer implements ThrowableRenderer {

    private int depth = -1;

    public DepthThrowableRenderer(int depth) {
        this.depth = depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public String[] doRender(final Throwable throwable) {
        return render(throwable, depth);
    }

    /**
     * Render throwable using Throwable.printStackTrace.
     *
     * @param throwable throwable, may not be null.
     * @param depth     stack depth
     * @return string representation.
     */
    public static String[] render(final Throwable throwable, final int depth) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            throwable.printStackTrace(pw);
        } catch (RuntimeException ex) {
        }
        pw.flush();
        LineNumberReader reader = new LineNumberReader(new StringReader(sw.toString()));
        ArrayList<String> lines = new ArrayList<String>();
        try {
            String line = reader.readLine();
            int count = 0;
            while (line != null && (depth == -1 || count++ <= depth)) {
                lines.add(line);
                line = reader.readLine();
            }
        } catch (IOException ex) {
            if (ex instanceof InterruptedIOException) {
                Thread.currentThread().interrupt();
            }
            lines.add(ex.toString());
        }
        String[] tempRep = new String[lines.size()];
        lines.toArray(tempRep);
        return tempRep;
    }
}
