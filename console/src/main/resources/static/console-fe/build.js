#! /usr/bin/env node
/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const cp = require('child_process');
const fs = require('fs');
const path = require('path');
// 默认打包存放地址
const buildDir = path.join(__dirname, 'build');
// 打包后文件存放地址
const targetDir = path.join(__dirname, '../');

const spawnAsync = (...args) =>
  new Promise((resolve, reject) => {
    const worker = cp.spawn(...args, { stdio: 'inherit' });
    worker.on('close', resolve);
    worker.on('error', reject);
  });

const mkdir = dir => {
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir);
  }
};

const copyDir = (sourceDir, targetDir) => {
  if (!fs.existsSync(sourceDir) || !fs.statSync(sourceDir).isDirectory()) {
    return;
  }

  mkdir(targetDir);

  fs.readdirSync(sourceDir).forEach(_fileName => {
    const sourceFileName = path.join(sourceDir, _fileName);
    const targetFileName = path.join(targetDir, _fileName);
    const fileStat = fs.statSync(sourceFileName);
    if (fileStat.isDirectory()) {
      copyDir(sourceFileName, targetFileName);
    }

    if (fileStat.isFile()) {
      fs.writeFileSync(targetFileName, fs.readFileSync(sourceFileName));
    }
  });
};

spawnAsync('roadhog', ['build']).then(() => copyDir(buildDir, targetDir));
