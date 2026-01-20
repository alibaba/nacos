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

import * as yaml from 'js-yaml';
import * as toml from '@iarna/toml';

/**
 * Validate a configuration item
 */
function validateProperty(property) {
  let { length } = property;
  let keyLen = 0;
  let valueStart = length;
  let hasSep = false;
  let precedingBackslash = false;
  let c;
  // Parse key
  while (keyLen < length) {
    c = property[keyLen];
    if ((c === '=' || c === ':') && !precedingBackslash) {
      valueStart = keyLen + 1;
      hasSep = true;
      break;
    }

    if ((c === ' ' || c === '\t' || c === '\f') && !precedingBackslash) {
      valueStart = keyLen + 1;
      break;
    }

    if (c === '\\') {
      precedingBackslash = !precedingBackslash;
    } else {
      precedingBackslash = false;
    }
    keyLen++;
  }
  // Parse value
  while (valueStart < length) {
    c = property[valueStart];
    if (c !== ' ' && c !== '\t' && c !== '\f') {
      if (!hasSep && (c === '=' || c === ':')) {
        hasSep = true;
      } else {
        break;
      }
    }
    valueStart++;
  }

  return (
    validateKeyOrValueForProperty(property, 0, keyLen) &&
    validateKeyOrValueForProperty(property, valueStart, length)
  );
}

function validateKeyOrValueForProperty(property, start, end) {
  // check null
  if (start >= end) {
    return false;
  }
  let index = 0;
  let c;
  while (index < property.length) {
    c = property[index++];
    if (c !== '\\') {
      continue;
    }

    c = property[index++];
    // check backslash
    if (!isPropertyEscape(c)) {
      return false;
    }

    // check Unicode
    if (c === 'u') {
      let unicode = property.slice(index, index + 4).join('');
      if (unicode.match(/^[a-f0-9]{4}$/i) === null) {
        return false;
      }
      index += 4;
    }
  }

  return true;
}

function isPropertyEscape(c = '') {
  return 'abfnrt\\"\'0! #:=u'.includes(c);
}

export default {
  /**
   * Validate if JSON is valid
   */
  validateJson(str) {
    try {
      return !!JSON.parse(str);
    } catch (e) {
      return false;
    }
  },

  /**
   * Validate if XML and HTML are valid
   */
  validateXml(str) {
    try {
      if (typeof DOMParser !== 'undefined') {
        let parserObj =
          new window.DOMParser()
            .parseFromString(str, 'application/xml')
            .getElementsByTagName('parsererror') || {};
        return parserObj.length === 0;
      } else if (typeof window.ActiveXObject !== 'undefined') {
        let xml = new window.ActiveXObject('Microsoft.XMLDOM');
        xml.async = 'false';
        xml.loadXML(str);
        return xml;
      }
    } catch (e) {
      return false;
    }
  },

  /**
   * Validate if YAML is valid
   */
  validateYaml(str) {
    try {
      return yaml.load(str);
    } catch (e) {
      return false;
    }
  },

  /**
   * Validate if properties are correct
   */
  validateProperties(str = '') {
    let isNewLine = true;
    let isCommentLine = false;
    let isSkipWhiteSpace = true;
    let precedingBackslash = false;
    let appendedLineBegin = false;
    let skipLF = false;
    let hasProperty = false;
    let property = [];
    for (let i = 0; i < str.length; i++) {
      let c = str[i];

      if (skipLF) {
        skipLF = false;
        if (c === '\n') {
          continue;
        }
      }
      // Skip leading whitespace
      if (isSkipWhiteSpace) {
        if (c === ' ' || c === '\t' || c === '\f') {
          continue;
        }
        if (!appendedLineBegin && (c === '\r' || c === '\n')) {
          continue;
        }
        appendedLineBegin = false;
        isSkipWhiteSpace = false;
      }

      // Check for comment line
      if (isNewLine) {
        isNewLine = false;
        if (c === '#' || c === '!') {
          isCommentLine = true;
          continue;
        }
      }

      if (c !== '\n' && c !== '\r') {
        property.push(c);
        if (c === '\\') {
          precedingBackslash = !precedingBackslash;
        } else {
          precedingBackslash = false;
        }
        continue;
      }

      // Skip comment line
      if (isCommentLine || property.length === 0) {
        isNewLine = true;
        isCommentLine = false;
        isSkipWhiteSpace = true;
        property = [];
        continue;
      }

      // Handle escape characters
      if (precedingBackslash) {
        property.pop();
        precedingBackslash = false;
        isSkipWhiteSpace = true;
        appendedLineBegin = true;
        if (c === '\r') {
          skipLF = true;
        }
        continue;
      }
      // Parse configuration item
      // Perform validation
      if (!validateProperty(property)) {
        return false;
      }
      hasProperty = true;
      property = [];
      isNewLine = true;
      isSkipWhiteSpace = true;
    }

    // Validate last line
    if (property.length > 0 && !isCommentLine) {
      return validateProperty(property);
    }

    return hasProperty;
  },

  /**
   * Validate if TOML is valid
   */
  validateToml(str) {
    try {
      // Without the replace, TOML comment line breaks may cause the following error:
      // TomlError: Control characters (codes < 0x1f and 0x7f) are not allowed in comments
      return toml.parse(str.replace(/\r\n/g, '\n'));
    } catch (e) {
      return false;
    }
  },

  /**
   * Validate by type
   */
  validate({ content, type }) {
    let validateObj = {
      json: this.validateJson,
      xml: this.validateXml,
      'text/html': this.validateXml,
      html: this.validateXml,
      properties: this.validateProperties,
      yaml: this.validateYaml,
      toml: this.validateToml,
    };

    if (!validateObj[type]) {
      return true;
    }

    return validateObj[type](content);
  },
};
