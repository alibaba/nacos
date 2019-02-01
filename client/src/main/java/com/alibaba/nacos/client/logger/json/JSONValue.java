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
 * $Id: JSONValue.java,v 1.1 2006/04/15 14:37:04 platform Exp $
 * Created on 2006-4-15
 */
package com.alibaba.nacos.client.logger.json;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

import com.alibaba.nacos.client.logger.json.parser.JSONParser;
import com.alibaba.nacos.client.logger.json.parser.ParseException;

/**
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JSONValue {
    /**
     * Parse JSON text into java object from the input source. Please use parseWithException() if you don't want to
     * ignore the exception.
     *
     * @param in
     * @return Instance of the following: com.alibaba.nacos.client.logger.jsonJSONObject,
     * com.alibaba.nacos.client.logger.jsonJSONArray, java.lang.String, java.lang.Number, java.lang.Boolean, null
     * @see com.alibaba.nacos.client.logger.jsonparser.JSONParser#parse(Reader)
     * @see #parseWithException(Reader)
     * @deprecated this method may throw an {@code Error} instead of returning {@code null}; please use {@link
     * JSONValue#parseWithException(Reader)} instead
     */
    public static Object parse(Reader in) {
        try {
            JSONParser parser = new JSONParser();
            return parser.parse(in);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse JSON text into java object from the given string. Please use parseWithException() if you don't want to
     * ignore the exception.
     *
     * @param s
     * @return Instance of the following: com.alibaba.nacos.client.logger.jsonJSONObject,
     * com.alibaba.nacos.client.logger.jsonJSONArray, java.lang.String, java.lang.Number, java.lang.Boolean, null
     * @see com.alibaba.nacos.client.logger.jsonparser.JSONParser#parse(Reader)
     * @see #parseWithException(Reader)
     * @deprecated this method may throw an {@code Error} instead of returning {@code null}; please use {@link
     * JSONValue#parseWithException(String)} instead
     */
    public static Object parse(String s) {
        StringReader in = new StringReader(s);
        return parse(in);
    }

    /**
     * Parse JSON text into java object from the input source.
     *
     * @param in
     * @return Instance of the following: com.alibaba.nacos.client.logger.jsonJSONObject,
     * com.alibaba.nacos.client.logger.jsonJSONArray, java.lang.String, java.lang.Number, java.lang.Boolean, null
     * @throws IOException
     * @throws ParseException
     * @see com.alibaba.nacos.client.logger.jsonparser.JSONParser
     */
    public static Object parseWithException(Reader in) throws IOException, ParseException {
        JSONParser parser = new JSONParser();
        return parser.parse(in);
    }

    public static Object parseWithException(String s) throws ParseException {
        JSONParser parser = new JSONParser();
        return parser.parse(s);
    }

    /**
     * Encode an object into JSON text and write it to out.
     * <p>
     * If this object is a Map or a List, and it's also a JSONStreamAware or a JSONAware, JSONStreamAware or JSONAware
     * will be considered firstly.
     * <p>
     * DO NOT call this method from writeJSONString(Writer) of a class that implements both JSONStreamAware and (Map or
     * List) with "this" as the first parameter, use JSONObject.writeJSONString(Map, Writer) or
     * JSONArray.writeJSONString(List, Writer) instead.
     *
     * @param value
     * @param writer
     * @see com.alibaba.nacos.client.logger.jsonJSONObject#writeJSONString(Map, Writer)
     * @see com.alibaba.nacos.client.logger.jsonJSONArray#writeJSONString(List, Writer)
     */
    public static void writeJSONString(Object value, Writer out) throws IOException {
        if (value == null) {
            out.write("null");
            return;
        }

        if (value instanceof String) {
            out.write('\"');
            out.write(escape((String)value));
            out.write('\"');
            return;
        }

        if (value instanceof Double) {
            if (((Double)value).isInfinite() || ((Double)value).isNaN()) {
                out.write("null");
            } else {
                out.write(value.toString());
            }
            return;
        }

        if (value instanceof Float) {
            if (((Float)value).isInfinite() || ((Float)value).isNaN()) {
                out.write("null");
            } else {
                out.write(value.toString());
            }
            return;
        }

        if (value instanceof Number) {
            out.write(value.toString());
            return;
        }

        if (value instanceof Boolean) {
            out.write(value.toString());
            return;
        }

        if ((value instanceof JSONStreamAware)) {
            ((JSONStreamAware)value).writeJSONString(out);
            return;
        }

        if ((value instanceof JSONAware)) {
            out.write(((JSONAware)value).toJSONString());
            return;
        }

        if (value instanceof Map) {
            JSONObject.writeJSONString((Map)value, out);
            return;
        }

        if (value instanceof Collection) {
            JSONArray.writeJSONString((Collection)value, out);
            return;
        }

        if (value instanceof byte[]) {
            JSONArray.writeJSONString((byte[])value, out);
            return;
        }

        if (value instanceof short[]) {
            JSONArray.writeJSONString((short[])value, out);
            return;
        }

        if (value instanceof int[]) {
            JSONArray.writeJSONString((int[])value, out);
            return;
        }

        if (value instanceof long[]) {
            JSONArray.writeJSONString((long[])value, out);
            return;
        }

        if (value instanceof float[]) {
            JSONArray.writeJSONString((float[])value, out);
            return;
        }

        if (value instanceof double[]) {
            JSONArray.writeJSONString((double[])value, out);
            return;
        }

        if (value instanceof boolean[]) {
            JSONArray.writeJSONString((boolean[])value, out);
            return;
        }

        if (value instanceof char[]) {
            JSONArray.writeJSONString((char[])value, out);
            return;
        }

        if (value instanceof Object[]) {
            JSONArray.writeJSONString((Object[])value, out);
            return;
        }

        out.write(value.toString());
    }

    /**
     * Convert an object to JSON text.
     * <p>
     * If this object is a Map or a List, and it's also a JSONAware, JSONAware will be considered firstly.
     * <p>
     * DO NOT call this method from toJSONString() of a class that implements both JSONAware and Map or List with "this"
     * as the parameter, use JSONObject.toJSONString(Map) or JSONArray.toJSONString(List) instead.
     *
     * @param value
     * @return JSON text, or "null" if value is null or it's an NaN or an INF number.
     * @see com.alibaba.nacos.client.logger.json.JSONObject#toJSONString(Map)
     */
    public static String toJSONString(Object value) {
        final StringWriter writer = new StringWriter();

        try {
            writeJSONString(value, writer);
            return writer.toString();
        } catch (IOException e) {
            // This should never happen for a StringWriter
            throw new RuntimeException(e);
        }
    }

    /**
     * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters (U+0000 through U+001F).
     *
     * @param s
     * @return
     */
    public static String escape(String s) {
        if (s == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        escape(s, sb);
        return sb.toString();
    }

    /**
     * @param s  - Must not be null.
     * @param sb
     */
    static void escape(String s, StringBuffer sb) {
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                default:
                    //Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if (isUnicodeChar(ch)) {
                        String ss = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < FOUR - ss.length(); k++) {
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                    } else {
                        sb.append(ch);
                    }
            }
        }//for
    }

    private static boolean isUnicodeChar(char ch) {
        return (ch >= '\u0000' && ch <= '\u001F') || (ch >= '\u007F' && ch <= '\u009F')
            || (ch >= '\u2000' && ch <= '\u20FF');
    }

    private static int FOUR = 4;

}
