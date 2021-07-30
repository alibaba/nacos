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
// 默认打包存放地址
const srcDir = path.join(__dirname, '../dist');
// 打包后文件存放地址
const destDir = path.join(__dirname, '../../console/src/main/resources/static/');

const mkdir = dir => {
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir);
    }
  };

const copyList = ['js/main.js', 'css/main.css'];

copyList.forEach(_fileName => {
    const srcFileName = path.join(srcDir, _fileName);
    const destFileName = path.join(destDir, _fileName);

    if (!fs.existsSync(srcFileName)) {
        return;
    }

    mkdir(path.dirname(destFileName));

    const readStream = fs.createReadStream(srcFileName);
    const writeStream = fs.createWriteStream(destFileName);
    readStream.pipe(writeStream);
});
