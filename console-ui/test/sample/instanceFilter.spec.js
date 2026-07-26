const $ = require('jquery');
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

  it('× url: http://localhost:8000/#/serviceDetail?name=scenator&groupName=DEFAULT_GROUP', async function() {
    await driver.url(
      _(`http://localhost:8000/#/serviceDetail?name=scenator&groupName=DEFAULT_GROUP`)
    );
  });

  it('url: http://localhost:8000/#/serviceDetail?name=scenator&groupName=DEFAULT_GROUP', async function() {
    await driver.url(
      _(`http://localhost:8000/#/serviceDetail?name=scenator&groupName=DEFAULT_GROUP`)
    );
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

  it('click: #username, 142, 43, 0', async function() {
    await driver
      .sleep(300)
      .wait('#username', 30000)
      .sleep(300)
      .mouseMove(142, 43)
      .click(0);
  });

  it('sendKeys: nacos{ENTER}{TAB}nacos{ENTER}', async function() {
    await driver.sendKeys('nacos{ENTER}{TAB}nacos{ENTER}');
  });

  it('click: 权限控制 ( #root li[role="menuitem"]:nth-child(3) > div[role="listitem"].next-menu-item > div.next-menu-item-inner, 112, 24, 0 )', async function() {
    await driver
      .sleep(300)
      .wait(
        '#root li[role="menuitem"]:nth-child(3) > div[role="listitem"].next-menu-item > div.next-menu-item-inner',
        30000
      )
      .sleep(300)
      .mouseMove(112, 24)
      .click(0);
  });

  it('click: 服务管理 ( #root li[role="menuitem"]:nth-child(2) > div[role="listitem"].next-menu-item > div.next-menu-item-inner, 83, 27, 0 )', async function() {
    await driver
      .sleep(300)
      .wait(
        '#root li[role="menuitem"]:nth-child(2) > div[role="listitem"].next-menu-item > div.next-menu-item-inner',
        30000
      )
      .sleep(300)
      .mouseMove(83, 27)
      .click(0);
  });

  it('click: 服务列表 ( #root li[role="menuitem"]:nth-child(1) > div.next-menu-item-inner, 63, 30, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('#root li[role="menuitem"]:nth-child(1) > div.next-menu-item-inner', 30000)
      .sleep(300)
      .mouseMove(63, 30)
      .click(0);
  });

  it('× click: 详情 ( #root tr.last > td[role="gridcell"].last > div.next-table-cell-wrapper > div > a:nth-child(1), 13, 3, 0 )', async function() {
    await driver
      .sleep(300)
      .wait(
        '#root tr.last > td[role="gridcell"].last > div.next-table-cell-wrapper > div > a:nth-child(1)',
        30000
      )
      .sleep(300)
      .mouseMove(13, 3)
      .click(0);
  });

  it('scrollElementTo: #root div.right-panel, 0, 158', async function() {
    await driver
      .sleep(300)
      .wait('#root div.right-panel', 30000)
      .sleep(300)
      .scrollElementTo(0, 158);
  });

  it('× click: div:nth-child(1) > div.next-form-item-control > span.next-small > input, 130, 11, 0', async function() {
    await driver
      .sleep(300)
      .wait('div:nth-child(1) > div.next-form-item-control > span.next-small > input', 30000)
      .sleep(300)
      .mouseMove(130, 11)
      .click(0);
  });

  it('sendKeys: name{TAB}ins1', async function() {
    await driver.sendKeys('name{TAB}ins1');
  });

  it('× click: 添加过滤 ( //span[text()="添加过滤"], 43, 1, 0 )', async function() {
    await driver
      .sleep(300)
      .wait('//span[text()="添加过滤"]', 30000)
      .sleep(300)
      .mouseMove(43, 1)
      .click(0);
  });

  it('scrollElementTo: #root div.right-panel, 0, 37', async function() {
    await driver
      .sleep(300)
      .wait('#root div.right-panel', 30000)
      .sleep(300)
      .scrollElementTo(0, 37);
  });

  it('scrollTo: 0, 1', async function() {
    await driver.scrollTo(0, 1);
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
  let group = webdriverConfig.group || 'default';
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
  delete webdriverConfig.group;
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
          group: group,
          browserName: browserName,
          version: browserVersion,
          'ie.ensureCleanSession': true,
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
        if (config.reporter && config.reporter.distDir) {
          self.screenshotPath = config.reporter.distDir + '/reports/screenshots/' + casePath;
          self.diffbasePath = config.reporter.distDir + '/reports/diffbase/' + casePath;
        } else {
          self.screenshotPath = rootPath + '/reports/screenshots/' + casePath;
          self.diffbasePath = rootPath + '/reports/diffbase/' + casePath;
        }
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

        if (
          (config.screenshots && config.screenshots.captureAll && !/^(closeWindow):/.test(title)) ||
          currentTest.state === 'failed'
        ) {
          const casePath = path.dirname(caseName);
          const filepath = `${self.screenshotPath}/${self.caseName}_${self.stepId}`;
          const relativeFilePath = `./screenshots/${casePath}/${self.caseName}_${self.stepId}`;
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
            appendToContext(self, relativeFilePath + '.png');
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

function appendToContext(mocha, content) {
  try {
    const test = mocha.currentTest || mocha.test;

    if (!test.context) {
      test.context = content;
    } else if (Array.isArray(test.context)) {
      test.context.push(content);
    } else {
      test.context = [test.context];
      test.context.push(content);
    }
  } catch (e) {
    console.log('error', e);
  }
}

function catchError(error) {}
