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
 * $Id: JSONArray.java,v 1.1 2006/04/15 14:10:48 platform Exp $
 * Created on 2006-4-10
 */
package com.alibaba.nacos.client.logger.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * A JSON array. JSONObject supports java.util.List interface.
 *
 * @author FangYidong<fangyidong   @   yahoo.com.cn>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JSONArray extends ArrayList implements JSONAware, JSONStreamAware {
    private static final long serialVersionUID = 3957988303675231981L;

    /**
     * Constructs an empty JSONArray.
     */
    public JSONArray() {
        super();
    }

    /**
     * Constructs a JSONArray containing the elements of the specified collection, in the order they are returned by the
     * collection's iterator.
     *
     * @param c the collection whose elements are to be placed into this JSONArray
     */
    public JSONArray(Collection c) {
        super(c);
    }

    /**
     * Encode a list into JSON text and write it to out. If this list is also a JSONStreamAware or a JSONAware,
     * JSONStreamAware and JSONAware specific behaviours will be ignored at this top level.
     *
     * @param collection
     * @param out
     * @see com.alibaba.nacos.client.logger.json.JSONValue#writeJSONString(Object, Writer)
     */
    public static void writeJSONString(Collection collection, Writer out) throws IOException {
        if (collection == null) {
            out.write("null");
            return;
        }

        boolean first = true;
        Iterator iter = collection.iterator();

        out.write('[');
        while (iter.hasNext()) {
            if (first) {
                first = false;
            } else {
                out.write(',');
            }
            Object value = iter.next();
            if (value == null) {
                out.write("null");
                continue;
            }

            JSONValue.writeJSONString(value, out);
        }
        out.write(']');
    }

    public void writeJSONString(Writer out) throws IOException {
        writeJSONString(this, out);
    }

    /**
     * Convert a list to JSON text. The result is a JSON array. If this list is also a JSONAware, JSONAware specific
     * behaviours will be omitted at this top level.
     *
     * @param collection
     * @return JSON text, or "null" if list is null.
     * @see com.alibaba.nacos.client.logger.json.JSONValue#toJSONString(Object)
     */
    public static String toJSONString(Collection collection) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(collection, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(byte[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(byte[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(short[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(short[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(int[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(int[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(long[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(long[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(float[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(float[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(double[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(double[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(boolean[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                out.write(String.valueOf(array[i]));
            }

            out.write("]");
        }
    }

    public static String toJSONString(boolean[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(char[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[\"");
            out.write(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.write("\",\"");
                out.write(String.valueOf(array[i]));
            }

            out.write("\"]");
        }
    }

    public static String toJSONString(char[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public static void writeJSONString(Object[] array, Writer out) throws IOException {
        if (array == null) {
            out.write("null");
        } else if (array.length == 0) {
            out.write("[]");
        } else {
            out.write("[");
            JSONValue.writeJSONString(array[0], out);

            for (int i = 1; i < array.length; i++) {
                out.write(",");
                JSONValue.writeJSONString(array[i], out);
            }

            out.write("]");
        }
    }

    public static String toJSONString(Object[] array) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(array, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    public String toJSONString() {
        return toJSONString(this);
    }

    /**
     * Returns a string representation of this array. This is equivalent to calling {@link JSONArray#toJSONString()}.
     */
    public String toString() {
        return toJSONString();
    }
}
