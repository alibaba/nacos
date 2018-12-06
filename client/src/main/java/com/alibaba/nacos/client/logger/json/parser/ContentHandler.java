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
package com.alibaba.nacos.client.logger.json.parser;

import java.io.IOException;

/**
 * A simplified and stoppable SAX-like content handler for stream processing of JSON text.
 *
 * @author FangYidong<fangyidong   @   yahoo.com.cn>
 * @see org.xml.sax.ContentHandler
 * @see com.alibaba.nacos.client.logger.json.parser.JSONParser#parse(java.io.Reader, ContentHandler, boolean)
 */
public interface ContentHandler {
    /**
     * Receive notification of the beginning of JSON processing. The parser will invoke this method only once.
     *
     * @throws ParseException - JSONParser will stop and throw the same exception to the caller when receiving this
     *                        exception.
     * @throws IOException
     */
    void startJSON() throws ParseException, IOException;

    /**
     * Receive notification of the end of JSON processing.
     *
     * @throws ParseException
     * @throws IOException
     */
    void endJSON() throws ParseException, IOException;

    /**
     * Receive notification of the beginning of a JSON object.
     *
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException - JSONParser will stop and throw the same exception to the caller when receiving this
     *                        exception.
     * @throws IOException
     * @see #endJSON
     */
    boolean startObject() throws ParseException, IOException;

    /**
     * Receive notification of the end of a JSON object.
     *
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException
     * @throws IOException
     * @see #startObject
     */
    boolean endObject() throws ParseException, IOException;

    /**
     * Receive notification of the beginning of a JSON object entry.
     *
     * @param key - Key of a JSON object entry.
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException
     * @throws IOException
     * @see #endObjectEntry
     */
    boolean startObjectEntry(String key) throws ParseException, IOException;

    /**
     * Receive notification of the end of the value of previous object entry.
     *
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException
     * @throws IOException
     * @see #startObjectEntry
     */
    boolean endObjectEntry() throws ParseException, IOException;

    /**
     * Receive notification of the beginning of a JSON array.
     *
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException
     * @throws IOException
     * @see #endArray
     */
    boolean startArray() throws ParseException, IOException;

    /**
     * Receive notification of the end of a JSON array.
     *
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException
     * @throws IOException
     * @see #startArray
     */
    boolean endArray() throws ParseException, IOException;

    /**
     * Receive notification of the JSON primitive values: java.lang.String, java.lang.Number, java.lang.Boolean null
     *
     * @param value - Instance of the following: java.lang.String, java.lang.Number, java.lang.Boolean null
     * @return false if the handler wants to stop parsing after return.
     * @throws ParseException
     * @throws IOException
     */
    boolean primitive(Object value) throws ParseException, IOException;

}
