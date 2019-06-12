import * as yamljs from 'yamljs';

export default {
  /**
   * 检测json是否合法
   */
  validateJson(str) {
    try {
      return !!JSON.parse(str);
    } catch (e) {
      return false;
    }
  },

  /**
   * 检测xml和html是否合法
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
   * 检测yaml是否合法
   */
  validateYaml(str) {
    try {
      return yamljs.parse(str);
    } catch (e) {
      return false;
    }
  },

  /**
   * 检测属性是否正确
   */
  validateProperties(str = '') {
    const reg = /^[^=]+=.+$/;
    return str
      .replace('\n\r', '\n')
      .split('\n')
      .every(_str => reg.test(_str.trim()));
  },

  /**
   * 根据类型验证类型
   */
  validate({ content, type }) {
    let validateObj = {
      json: this.validateJson,
      xml: this.validateXml,
      'text/html': this.validateXml,
      html: this.validateXml,
      properties: this.validateProperties,
      yaml: this.validateYaml,
    };

    if (!validateObj[type]) {
      return true;
    }

    return validateObj[type](content);
  },
};
