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

const fs = require('fs');
const path = require('path');
const chai = require('chai');
const should = chai.should();
const JWebDriver = require('jwebdriver');
chai.use(JWebDriver.chaiSupportChainPromise);
const resemble = require('resemblejs-node');
resemble.outputSettings({
  errorType: 'flatDifferenceIntensity',
});

const rootPath = getRootPath();

module.exports = function() {
  let driver, testVars;

  before(function() {
    let self = this;
    driver = self.driver;
    testVars = self.testVars;
  });

  it('url: http://127.0.0.1:8811', async function() {
    await driver.url(_(`http://127.0.0.1:8811`));
  });

  it('waitBody: ', async function() {
    await driver
      .sleep(500)
      .wait('body', 30000)
      .html()
      .then(function(code) {
        isPageError(code).should.be.false;
      });
  });

  it('click: #username, 89, 41, 0', async function() {
    await driver
      .sleep(300)
      .wait('#username', 30000)
      .sleep(300)
      .mouseMove(89, 41)
      .click(0);
  });

  it('sendKeys: nacos', async function() {
    await driver.sendKeys('nacos');
  });

  it('click: #password, 53, 34, 0', async function() {
    await driver
      .sleep(300)
      .wait('#password', 30000)
      .sleep(300)
      .mouseMove(53, 34)
      .click(0);
  });

  it('sendKeys: nacos', async function() {
    await driver.sendKeys('nacos');
  });

  it('click: 提交 ( //button[text()="提交"], 321, 30, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="提交"]', 30000)
      .sleep(300)
      .mouseMove(321, 30)
      .click(0);
  });

  it('click: div:nth-child(1) > div.next-form-item-control > span.next-medium > input[type="text"], 178, 9, 0', async function() {
    await driver
      .sleep(300)
      .wait(
        'div:nth-child(1) > div.next-form-item-control > span.next-medium > input[type="text"]',
        30000
      )
      .sleep(300)
      .mouseMove(178, 9)
      .click(0);
  });

  it('sendKeys: test_test', async function() {
    await driver.sendKeys('test_test');
  });

  it('click: 查询 ( //button[text()="查询"], 3, 9, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="查询"]', 30000)
      .sleep(300)
      .mouseMove(3, 9)
      .click(0);
  });

  it('click: #viewFramework-product-body i.next-icon-add, 15, 27, 0', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body i.next-icon-add', 30000)
      .sleep(300)
      .mouseMove(15, 27)
      .click(0);
  });

  it('click: Data ID: ( #dataId, 154, 20, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#dataId', 30000)
      .sleep(300)
      .mouseMove(154, 20)
      .click(0);
  });

  it('sendKeys: test_test', async function() {
    await driver.sendKeys('test_test');
  });

  it('dblClick: Group: ( #group, 89, 11, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#group', 30000)
      .sleep(300)
      .mouseMove(89, 11)
      .click(0)
      .click(0);
  });

  it('click: #viewFramework-product-body i.next-icon-delete-filling, 11, 7, 0', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body i.next-icon-delete-filling', 30000)
      .sleep(300)
      .mouseMove(11, 7)
      .click(0);
  });

  it('sendKeys: test', async function() {
    await driver.sendKeys('test');
  });

  it('click: 更多高级选项 ( //a[text()="更多高级选项"], 61, 3, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//a[text()="更多高级选项"]', 30000)
      .sleep(300)
      .mouseMove(61, 3)
      .click(0);
  });

  it('click: span.next-select-trigger-search > input[role="combobox"]:nth-child(1), 66, 8, 0', async function() {
    await driver
      .sleep(300)
      .wait('span.next-select-trigger-search > input[role="combobox"]:nth-child(1)', 30000)
      .sleep(300)
      .mouseMove(66, 8)
      .click(0);
  });

  it('click: span.next-select-trigger-search > input[role="combobox"]:nth-child(1), 71, 16, 0', async function() {
    await driver
      .sleep(300)
      .wait('span.next-select-trigger-search > input[role="combobox"]:nth-child(1)', 30000)
      .sleep(300)
      .mouseMove(71, 16)
      .click(0);
  });

  it('click: 归属应用: ( #appName, 50, 19, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#appName', 30000)
      .sleep(300)
      .mouseMove(50, 19)
      .click(0);
  });

  it('click: 收起 ( //a[text()="收起"], 16, 5, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//a[text()="收起"]', 30000)
      .sleep(300)
      .mouseMove(16, 5)
      .click(0);
  });

  it('click: #desc, 77, 40, 0', async function() {
    await driver
      .sleep(300)
      .wait('#desc', 30000)
      .sleep(300)
      .mouseMove(77, 40)
      .click(0);
  });

  it('sendKeys: test', async function() {
    await driver.sendKeys('test');
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 49', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 49);
  });

  it('click: #container div.view-line, 60, 15, 0', async function() {
    await driver
      .sleep(300)
      .wait('#container div.view-line', 30000)
      .sleep(300)
      .mouseMove(60, 15)
      .click(0);
  });

  it('sendKeys: test', async function() {
    await driver.sendKeys('test');
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 155', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 155);
  });

  it('click: 发布 ( //button[text()="发布"], 39, 9, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="发布"]', 30000)
      .sleep(300)
      .mouseMove(39, 9)
      .click(0);
  });

  it('× click: 确定 ( //button[text()="确定"], 13, 9, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="确定"]', 30000)
      .sleep(300)
      .mouseMove(13, 9)
      .click(0);
  });

  it('click: 返回 ( //button[text()="返回"], 39, 18, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="返回"]', 30000)
      .sleep(300)
      .mouseMove(39, 18)
      .click(0);
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 0', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 0);
  });

  it('click: 详情 ( //a[text()="详情"], 12, 7, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//a[text()="详情"]', 30000)
      .sleep(300)
      .mouseMove(12, 7)
      .click(0);
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 22', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 22);
  });

  it('click: test ( #content, 225, 35, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#content', 30000)
      .sleep(300)
      .mouseMove(225, 35)
      .click(0);
  });

  it('click: #backarrow, 13, 10, 0', async function() {
    await driver
      .sleep(300)
      .wait('#backarrow', 30000)
      .sleep(300)
      .mouseMove(13, 10)
      .click(0);
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 0', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 0);
  });

  it('click: 示例代码 ( //a[text()="示例代码"], 29, 6, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//a[text()="示例代码"]', 30000)
      .sleep(300)
      .mouseMove(29, 6)
      .click(0);
  });

  it('click: Spring Boot ( li[role="tab"]:nth-child(2) > div.next-tabs-tab-inner, 63, 22, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('li[role="tab"]:nth-child(2) > div.next-tabs-tab-inner', 30000)
      .sleep(300)
      .mouseMove(63, 22)
      .click(0);
  });

  it('click: i.next-icon-close, 9, 10, 0', async function() {
    await driver
      .sleep(300)
      .wait('i.next-icon-close', 30000)
      .sleep(300)
      .mouseMove(9, 10)
      .click(0);
  });

  it('click: 编辑 ( //a[text()="编辑"], 14, 6, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//a[text()="编辑"]', 30000)
      .sleep(300)
      .mouseMove(14, 6)
      .click(0);
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 134', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 134);
  });

  it('click: label:nth-child(2) > span.next-radio > input[type="radio"][role="radio"].next-radio-input, 7, 1, 0', async function() {
    await driver
      .sleep(300)
      .wait(
        'label:nth-child(2) > span.next-radio > input[type="radio"][role="radio"].next-radio-input',
        30000
      )
      .sleep(300)
      .mouseMove(7, 1)
      .click(0);
  });

  it('click: label:nth-child(1) > span.next-radio > input[type="radio"][role="radio"].next-radio-input, 8, 8, 0', async function() {
    await driver
      .sleep(300)
      .wait(
        'label:nth-child(1) > span.next-radio > input[type="radio"][role="radio"].next-radio-input',
        30000
      )
      .sleep(300)
      .mouseMove(8, 8)
      .click(0);
  });

  it('click: test ( #container div.view-line, 47, 11, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#container div.view-line', 30000)
      .sleep(300)
      .mouseMove(47, 11)
      .click(0);
  });

  it('sendKeys: _test', async function() {
    await driver.sendKeys('_test');
  });

  it('click: test ( #desc, 76, 25, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#desc', 30000)
      .sleep(300)
      .mouseMove(76, 25)
      .click(0);
  });

  it('sendKeys: _test', async function() {
    await driver.sendKeys('_test');
  });

  it('click: 发布 ( //button[text()="发布"], 41, 15, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="发布"]', 30000)
      .sleep(300)
      .mouseMove(41, 15)
      .click(0);
  });

  it('click: 确认发布 ( //button[text()="确认发布"], 61, 16, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="确认发布"]', 30000)
      .sleep(300)
      .mouseMove(61, 16)
      .click(0);
  });

  it('click: 确定 ( //button[text()="确定"], 31, 15, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="确定"]', 30000)
      .sleep(300)
      .mouseMove(31, 15)
      .click(0);
  });

  it('click: 返回 ( //button[text()="返回"], 25, 6, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="返回"]', 30000)
      .sleep(300)
      .mouseMove(25, 6)
      .click(0);
  });

  it('scrollElementTo: #viewFramework-product-body, 0, 0', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body', 30000)
      .sleep(300)
      .scrollElementTo(0, 0);
  });

  it('click: 更多 ( #viewFramework-product-body span:nth-child(9), 19, 12, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#viewFramework-product-body span:nth-child(9)', 30000)
      .sleep(300)
      .mouseMove(19, 12)
      .click(0);
  });

  it('click: 历史版本 ( //span[text()="历史版本"], 0, 3, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//span[text()="历史版本"]', 30000)
      .sleep(300)
      .mouseMove(0, 3)
      .click(0);
  });

  it('click: 配置列表 ( //div[text()="配置列表"], 120, 36, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//div[text()="配置列表"]', 30000)
      .sleep(300)
      .mouseMove(120, 36)
      .click(0);
  });

  it('click: 更多 ( #viewFramework-product-body tr.first > td[type="body"][role="gridcell"].last > div.next-table-cell-wrapper > div > span:nth-child(9), 10, 8, 0 )', async function() {
    await driver
      .sleep(300)
      .wait(
        '#viewFramework-product-body tr.first > td[type="body"][role="gridcell"].last > div.next-table-cell-wrapper > div > span:nth-child(9)',
        30000
      )
      .sleep(300)
      .mouseMove(10, 8)
      .click(0);
  });

  it('click: div:nth-child(1) > div.next-form-item-control > span.next-medium > input[type="text"], 163, 21, 0', async function() {
    await driver
      .sleep(300)
      .wait(
        'div:nth-child(1) > div.next-form-item-control > span.next-medium > input[type="text"]',
        30000
      )
      .sleep(300)
      .mouseMove(163, 21)
      .click(0);
  });

  it('click: span.next-input > input[role="combobox"], 31, 19, 0', async function() {
    await driver
      .sleep(300)
      .wait('span.next-input > input[role="combobox"]', 30000)
      .sleep(300)
      .mouseMove(31, 19)
      .click(0);
  });

  it('sendKeys: test', async function() {
    await driver.sendKeys('test');
  });

  it('click: 查询 ( //button[text()="查询"], 8, 25, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="查询"]', 30000)
      .sleep(300)
      .mouseMove(8, 25)
      .click(0);
  });

  it('click: div:nth-child(1) > div.next-form-item-control > span.next-medium > input[type="text"], 91, 18, 0', async function() {
    await driver
      .sleep(300)
      .wait(
        'div:nth-child(1) > div.next-form-item-control > span.next-medium > input[type="text"]',
        30000
      )
      .sleep(300)
      .mouseMove(91, 18)
      .click(0);
  });

  it('sendKeys: test_test', async function() {
    await driver.sendKeys('test_test');
  });

  it('click: 查询 ( //button[text()="查询"], 17, 17, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="查询"]', 30000)
      .sleep(300)
      .mouseMove(17, 17)
      .click(0);
  });

  it('click: 删除 ( //a[text()="删除"], 7, 8, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//a[text()="删除"]', 30000)
      .sleep(300)
      .mouseMove(7, 8)
      .click(0);
  });

  it('click: 确认 ( //button[text()="确认"], 21, 15, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="确认"]', 30000)
      .sleep(300)
      .mouseMove(21, 15)
      .click(0);
  });

  it('click: 确定 ( //button[text()="确定"], 25, 14, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//button[text()="确定"]', 30000)
      .sleep(300)
      .mouseMove(25, 14)
      .click(0);
  });

  function _(str) {
    if (typeof str === 'string') {
      return str.replace(/\{\{(.+?)\}\}/g, function(all, key) {
        return testVars[key] || '';
      });
    } else {
      return str;
    }
  }
};

if (module.parent && /mocha\.js/.test(module.parent.id)) {
  runThisSpec();
}

function runThisSpec() {
  // read config
  let webdriver = process.env['webdriver'] || '';
  let proxy = process.env['wdproxy'] || '';
  let config = require(rootPath + '/config.json');
  let webdriverConfig = Object.assign({}, config.webdriver);
  let host = webdriverConfig.host;
  let port = webdriverConfig.port || 4444;
  let match = webdriver.match(/([^\:]+)(?:\:(\d+))?/);
  if (match) {
    host = match[1] || host;
    port = match[2] || port;
  }
  let testVars = config.vars;
  let browsers = webdriverConfig.browsers;
  browsers = browsers.replace(/^\s+|\s+$/g, '');
  delete webdriverConfig.host;
  delete webdriverConfig.port;
  delete webdriverConfig.browsers;

  // read hosts
  let hostsPath = rootPath + '/hosts';
  let hosts = '';
  if (fs.existsSync(hostsPath)) {
    hosts = fs.readFileSync(hostsPath).toString();
  }
  let specName = path
    .relative(rootPath, __filename)
    .replace(/\\/g, '/')
    .replace(/\.js$/, '');

  browsers.split(/\s*,\s*/).forEach(function(browserName) {
    let caseName = specName + ' : ' + browserName;

    let browserInfo = browserName.split(' ');
    browserName = browserInfo[0];
    let browserVersion = browserInfo[1];

    describe(caseName, function() {
      this.timeout(600000);
      this.slow(1000);

      let driver;
      before(function() {
        let self = this;
        let driver = new JWebDriver({
          host: host,
          port: port,
        });
        let sessionConfig = Object.assign({}, webdriverConfig, {
          browserName: browserName,
          version: browserVersion,
          'ie.ensureCleanSession': true,
          chromeOptions: {
            args: ['--enable-automation'],
          },
        });
        if (proxy) {
          sessionConfig.proxy = {
            proxyType: 'manual',
            httpProxy: proxy,
            sslProxy: proxy,
          };
        } else if (hosts) {
          sessionConfig.hosts = hosts;
        }

        try {
          self.driver = driver
            .session(sessionConfig)
            .windowSize(1024, 768)
            .config({
              pageloadTimeout: 30000, // page onload timeout
              scriptTimeout: 5000, // sync script timeout
              asyncScriptTimeout: 10000, // async script timeout
            });
        } catch (e) {
          console.log(e);
        }

        self.testVars = testVars;
        let casePath = path.dirname(caseName);
        self.screenshotPath = rootPath + '/screenshots/' + casePath;
        self.diffbasePath = rootPath + '/diffbase/' + casePath;
        self.caseName = caseName.replace(/.*\//g, '').replace(/\s*[:\.\:\-\s]\s*/g, '_');
        mkdirs(self.screenshotPath);
        mkdirs(self.diffbasePath);
        self.stepId = 0;
        return self.driver;
      });

      module.exports();

      beforeEach(function() {
        let self = this;
        self.stepId++;
        if (self.skipAll) {
          self.skip();
        }
      });

      afterEach(async function() {
        let self = this;
        let currentTest = self.currentTest;
        let title = currentTest.title;
        if (
          currentTest.state === 'failed' &&
          /^(url|waitBody|switchWindow|switchFrame):/.test(title)
        ) {
          self.skipAll = true;
        }
        if (!/^(closeWindow):/.test(title)) {
          let filepath = self.screenshotPath + '/' + self.caseName + '_' + self.stepId;
          let driver = self.driver;
          try {
            // catch error when get alert msg
            await driver.getScreenshot(filepath + '.png');
            let url = await driver.url();
            let html = await driver.source();
            html = '<!--url: ' + url + ' -->\n' + html;
            fs.writeFileSync(filepath + '.html', html);
            let cookies = await driver.cookies();
            fs.writeFileSync(filepath + '.cookie', JSON.stringify(cookies));
          } catch (e) {}
        }
      });

      after(function() {
        return this.driver.close();
      });
    });
  });
}

function getRootPath() {
  let rootPath = path.resolve(__dirname);
  while (rootPath) {
    if (fs.existsSync(rootPath + '/config.json')) {
      break;
    }
    rootPath = rootPath.substring(0, rootPath.lastIndexOf(path.sep));
  }
  return rootPath;
}

function mkdirs(dirname) {
  if (fs.existsSync(dirname)) {
    return true;
  } else {
    if (mkdirs(path.dirname(dirname))) {
      fs.mkdirSync(dirname);
      return true;
    }
  }
}

function callSpec(name) {
  try {
    require(rootPath + '/' + name)();
  } catch (e) {
    console.log(e);
    process.exit(1);
  }
}

function isPageError(code) {
  return (
    code == '' ||
    / jscontent="errorCode" jstcache="\d+"|diagnoseConnectionAndRefresh|dnserror_unavailable_header|id="reportCertificateErrorRetry"|400 Bad Request|403 Forbidden|404 Not Found|500 Internal Server Error|502 Bad Gateway|503 Service Temporarily Unavailable|504 Gateway Time-out/i.test(
      code
    )
  );
}

function catchError(error) {}
