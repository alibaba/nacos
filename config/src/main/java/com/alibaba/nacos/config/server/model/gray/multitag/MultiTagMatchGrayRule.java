/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model.gray.multitag;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.gray.AbstractTagMatchGrayRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.nacos.api.common.Constants.TAG_V2;

/**
 * tag v2 gray rule.
 *
 * @author rong
 */
public class MultiTagMatchGrayRule extends AbstractTagMatchGrayRule {
    
    private List<TagV2GrayRuleItem> ruleItems;
    
    public static final String TYPE_TAGV2 = TAG_V2;
    
    public static final String VERSION_1_1_0 = "1.1.0";
    
    private static final String ELEM_PATTERN =
        "\\s*" + KEY_PATTERN + "\\s*" + EQUAL_PATTERN + "\\s*" + VALUE_PATTERN + "\\s*";
    
    private static final String JOINT_PATTERN = "(\\|\\||&&)";
    
    private static final String EXPRESSION_PATTERN =
        "^" + ELEM_PATTERN + "(" + JOINT_PATTERN + ELEM_PATTERN + ")*$";
    
    public MultiTagMatchGrayRule() {
        super();
    }
    
    public MultiTagMatchGrayRule(String rawGrayRuleExp, int priority) {
        super(rawGrayRuleExp, priority);
    }
    
    @Override
    protected void parse(String rawRule) throws NacosException {
        this.isPatternMatch(rawRule, EXPRESSION_PATTERN);
        String[] splitSubExpressionByOrArray = rawRule.trim()
            .split(MultiTagMatchGrayRule.TagV2GrayRuleJoint.OR_REGEXP.getExpression());
        for (String s : splitSubExpressionByOrArray) {
            if (StringUtils.isBlank(s)) {
                continue;
            }
            //each subExpression is jointed by one or multi "&&"
            String[] splitSubExpressionByAndArray = s.trim()
                .split(MultiTagMatchGrayRule.TagV2GrayRuleJoint.AND_REGEXP.getExpression());
            for (int andIndex = 0; andIndex < splitSubExpressionByAndArray.length; andIndex++) {
                if (StringUtils.isBlank(splitSubExpressionByAndArray[andIndex])) {
                    continue;
                }
                String[] keyValueArray =
                    splitSubExpressionByAndArray[andIndex].trim().split(EQUAL_PATTERN);
                if (keyValueArray.length != 2) {
                    throw new NacosException(NacosException.INVALID_PARAM, String.format(
                        "tagv2 gray rule parse failed: key and value's[%s] doesn't match pattern[%s].",
                        splitSubExpressionByAndArray[andIndex],
                        KEY_PATTERN + EQUAL_PATTERN + VALUE_PATTERN));
                }
                isPatternMatch(keyValueArray[0].trim(), KEY_PATTERN);
                isPatternMatch(keyValueArray[1].trim(), VALUE_PATTERN);
                Set<String> values =
                    Arrays.stream(keyValueArray[1].split(VALUE_SPLITER_PATTERN)).map(String::trim)
                        .filter(StringUtils::isNotBlank).collect(Collectors.toSet());
                MultiTagMatchGrayRule.TagV2GrayRuleItem tagV2GrayRuleItem =
                    new MultiTagMatchGrayRule.TagV2GrayRuleItem(
                        keyValueArray[0].trim(), values);
                if (andIndex == 0) {
                    tagV2GrayRuleItem.setJoint(MultiTagMatchGrayRule.TagV2GrayRuleJoint.OR);
                }
                if (this.ruleItems == null) {
                    this.ruleItems = new ArrayList<>();
                }
                ruleItems.add(tagV2GrayRuleItem);
            }
        }
    }
    
    /**
     * this rule will match labelsMap.
     *
     * @param labelsMap labels map.
     * @return true if match. false if not match.
     * @date 2024/2/6
     */
    public boolean match(Map<String, String> labelsMap) {
        if (ruleItems.isEmpty() || labelsMap == null || labelsMap.isEmpty()) {
            return false;
        }
        ArrayList<TagV2GrayRuleItem> localRuleItems =
            ruleItems.stream().map(TagV2GrayRuleItem::clone)
                .collect(Collectors.toCollection(ArrayList::new));
        int result = 0;
        int tempResult = 0;
        boolean subRuleMatchFlag = true;
        HashSet<String> tempKeyExistSet = new HashSet<>();
        
        for (int index = 0; index < localRuleItems.size(); index++) {
            if (result > 0) {
                return true;
            }
            TagV2GrayRuleItem curTagV2GrayRuleItem = localRuleItems.get(index);
            
            if (curTagV2GrayRuleItem.getJoint() == TagV2GrayRuleJoint.AND) {
                //if AND, will consider the current ruleItem belong to this subRule.
                
                //if one of the items in the subRule is not match, will continue to next subRule.
                if (!subRuleMatchFlag) {
                    continue;
                }
                
                //if the key has already existed in this subRule,
                // another item with the same key appears which will be considered as a syntax error.
                if (tempKeyExistSet.contains(curTagV2GrayRuleItem.getKey())) {
                    subRuleMatchFlag = false;
                    continue;
                } else {
                    tempKeyExistSet.add(curTagV2GrayRuleItem.getKey());
                }
                
                //check current item
                if (!curTagV2GrayRuleItem.match(labelsMap.get(curTagV2GrayRuleItem.getKey()))) {
                    subRuleMatchFlag = false;
                }
                tempResult++;
            } else if (curTagV2GrayRuleItem.getJoint() == TagV2GrayRuleJoint.OR) {
                //if OR, will consider the current ruleItem belong to the next subRule,
                // and this subRule contains items between [subRuleBeginIndex, index).
                
                //only when subRuleMatchFlag is true, update result.
                if (subRuleMatchFlag) {
                    result = Math.max(result, tempResult);
                }
                curTagV2GrayRuleItem.setJoint(TagV2GrayRuleJoint.AND);
                subRuleMatchFlag = true;
                tempKeyExistSet.clear();
                index--;
            }
        }
        if (subRuleMatchFlag) {
            return Math.max(result, tempResult) > 0;
        }
        return result > 0;
    }
    
    /**
     * check this TagV2GrayRule is valid or not.
     *
     * @return true if valid. false if not valid.
     * @date 2024/2/7
     */
    public boolean isValid() {
        if (!super.isValid()) {
            return false;
        }
        if (ruleItems.isEmpty()) {
            return true;
        }
        HashSet<String> tempKeyExistSet = new HashSet<>();
        
        ArrayList<TagV2GrayRuleItem> localRuleItems =
            ruleItems.stream().map(TagV2GrayRuleItem::clone)
                .collect(Collectors.toCollection(ArrayList::new));
        for (int index = 0; index < localRuleItems.size(); index++) {
            TagV2GrayRuleItem curTagV2GrayRuleItem = localRuleItems.get(index);
            
            if (!curTagV2GrayRuleItem.isValid()) {
                return false;
            }
            
            if (curTagV2GrayRuleItem.getJoint() == TagV2GrayRuleJoint.AND) {
                //if AND, will consider the current ruleItem belong to this subRule.
                
                //if the key has already existed in this subRule,
                // another item with the same key appears which will be considered as a syntax error.
                if (tempKeyExistSet.contains(curTagV2GrayRuleItem.getKey())) {
                    return false;
                } else {
                    tempKeyExistSet.add(curTagV2GrayRuleItem.getKey());
                }
            } else if (curTagV2GrayRuleItem.getJoint() == TagV2GrayRuleJoint.OR) {
                //if OR, will consider the current ruleItem belong to the next subRule,
                // and this subRule contains items between [subRuleBeginIndex, index).
                
                //only when subRuleMatchFlag is true, update result.
                curTagV2GrayRuleItem.setJoint(TagV2GrayRuleJoint.AND);
                tempKeyExistSet.clear();
                index--;
            }
        }
        return true;
    }
    
    @Override
    public String getType() {
        return TYPE_TAGV2;
    }
    
    @Override
    public String getVersion() {
        return VERSION_1_1_0;
    }
    
    /**
     * tag v2 gray rule item.
     *
     * @author rong
     */
    public static class TagV2GrayRuleItem implements Cloneable {
        
        public String key;
        
        public TagV2GrayRuleOperator operator = TagV2GrayRuleOperator.IN;
        
        public final Set<String> values = new HashSet<>();
        
        public TagV2GrayRuleJoint joint = TagV2GrayRuleJoint.AND;
        
        public TagV2GrayRuleItem(String key) {
            this.key = key;
        }
        
        public TagV2GrayRuleItem(String key, Set<String> values) {
            this.key = key;
            this.values.addAll(values);
        }
        
        /**
         * judge if value is match the rule.
         *
         * @param value value
         * @return boolean true if match, false otherwise.
         * @date 2024/2/8
         */
        public boolean match(String value) {
            switch (operator) {
                case IN:
                    if (null == value) {
                        return false;
                    } else {
                        return values.contains(value);
                    }
                case NOT_IN:
                    if (null == value) {
                        return false;
                    } else {
                        return !values.contains(value);
                    }
                case EXIST:
                    return value != null;
                case NOT_EXIST:
                    return value == null;
                default:
            }
            return false;
        }
        
        /**
         * judge if rule is valid.
         *
         * @return boolean true if valid, false otherwise.
         * @throws NacosException if invalid.
         * @date 2024/2/8
         */
        public boolean isValid() {
            return !StringUtils.isBlank(key);
        }
        
        public static TagV2GrayRuleItemBuilder builder() {
            return new TagV2GrayRuleItemBuilder();
        }
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
        
        public TagV2GrayRuleOperator getOperator() {
            return operator;
        }
        
        public void setOperator(TagV2GrayRuleOperator operator) {
            this.operator = operator;
        }
        
        public Set<String> getValues() {
            return values;
        }
        
        public TagV2GrayRuleJoint getJoint() {
            return joint;
        }
        
        public void setJoint(TagV2GrayRuleJoint joint) {
            this.joint = joint;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TagV2GrayRuleItem)) {
                return false;
            }
            TagV2GrayRuleItem that = (TagV2GrayRuleItem) o;
            return Objects.equals(key, that.key) && operator == that.operator
                && values.size() == that.values.size()
                && values.containsAll(that.values) && joint == that.joint;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(key, operator, values, joint);
        }
        
        @Override
        public String toString() {
            return "{" + "key='" + key + '\'' + ", operator=" + operator + ", values=" + values
                + ", joint=" + joint
                + '}';
        }
        
        @Override
        public TagV2GrayRuleItem clone() {
            try {
                TagV2GrayRuleItem clone = (TagV2GrayRuleItem) super.clone();
                clone.setKey(key);
                clone.setJoint(joint);
                clone.setOperator(operator);
                clone.getValues().addAll(values);
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError();
            }
        }
        
        public static final class TagV2GrayRuleItemBuilder {
            
            private final TagV2GrayRuleItem item;
            
            private TagV2GrayRuleItemBuilder() {
                item = new TagV2GrayRuleItem(null);
            }
            
            public TagV2GrayRuleItemBuilder key(String key) {
                item.key = key;
                return this;
            }
            
            public TagV2GrayRuleItem build() {
                return item;
            }
        }
    }
    
    /**
     * tag v2 gray rule joint.
     *
     * @author rong
     */
    public enum TagV2GrayRuleJoint {
        
        /**
         * and.
         */
        AND("&&", "AND"),
        
        /**
         * or.
         */
        OR("||", "OR"),
        
        /**
         * and regexp.
         */
        AND_REGEXP("&&", "AND_REGEXP"),
        
        /**
         * or regexp.
         */
        OR_REGEXP("\\|\\|", "OR_REGEXP");
        
        public final String expression;
        
        public final String name;
        
        TagV2GrayRuleJoint(String expression, String name) {
            this.expression = expression;
            this.name = name;
        }
        
        public String getExpression() {
            return expression;
        }
        
        public String getName() {
            return name;
        }
    }
    
    /**
     * tag v2 gray rule operator.
     *
     * @author rong
     */
    public enum TagV2GrayRuleOperator {
        
        /**
         * in.
         */
        IN("in", "IN"),
        
        /**
         * not in.
         */
        NOT_IN("not in", "NOT_IN"),
        
        /**
         * exist.
         */
        EXIST("exist", "EXIST"),
        
        /**
         * not exist.
         */
        NOT_EXIST("not exist", "NOT_EXIST");
        
        public final String expression;
        
        public final String name;
        
        TagV2GrayRuleOperator(String expression, String name) {
            this.expression = expression;
            this.name = name;
        }
    }
}
