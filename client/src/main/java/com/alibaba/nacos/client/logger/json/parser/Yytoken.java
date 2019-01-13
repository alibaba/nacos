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
 * $Id: Yytoken.java,v 1.1 2006/04/15 14:10:48 platform Exp $
 * Created on 2006-4-15
 */
package com.alibaba.nacos.client.logger.json.parser;

/**
 * @author FangYidong<fangyidong @ yahoo.com.cn>
 */
public class Yytoken {
    /**
     * JSON primitive value: string,number,boolean,null
     */
    public static final int TYPE_VALUE = 0;
    public static final int TYPE_LEFT_BRACE = 1;
    public static final int TYPE_RIGHT_BRACE = 2;
    public static final int TYPE_LEFT_SQUARE = 3;
    public static final int TYPE_RIGHT_SQUARE = 4;
    public static final int TYPE_COMMA = 5;
    public static final int TYPE_COLON = 6;
    /**
     * end of file
     */
    public static final int TYPE_EOF = -1;

    public int type = 0;
    public Object value = null;

    public Yytoken(int type, Object value) {
        this.type = type;
        this.value = value;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        switch (type) {
            case TYPE_VALUE:
                sb.append("VALUE(").append(value).append(")");
                break;
            case TYPE_LEFT_BRACE:
                sb.append("LEFT BRACE({)");
                break;
            case TYPE_RIGHT_BRACE:
                sb.append("RIGHT BRACE(})");
                break;
            case TYPE_LEFT_SQUARE:
                sb.append("LEFT SQUARE([)");
                break;
            case TYPE_RIGHT_SQUARE:
                sb.append("RIGHT SQUARE(])");
                break;
            case TYPE_COMMA:
                sb.append("COMMA(,)");
                break;
            case TYPE_COLON:
                sb.append("COLON(:)");
                break;
            case TYPE_EOF:
                sb.append("END OF FILE");
                break;
            default:
                break;
        }
        return sb.toString();
    }
}
