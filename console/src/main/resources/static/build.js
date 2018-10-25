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
const buildDir = 'build';
// 打包后文件存放地址
const targetDir = __dirname;

const spawnAsync = (...args) => new Promise((resolve, reject) => {
    const worker = cp.spawn(...args, { stdio: 'inherit' });
    worker.on('close', resolve);
    worker.on('error', reject);
});

spawnAsync('roadhog', ['build'])
.then(() => {
    const _buildDir = path.join(__dirname, buildDir);
    if (!fs.statSync(_buildDir).isDirectory()) {
        return;
    }
    let fileList = fs.readdirSync(_buildDir, "utf8");
    fileList.forEach((fileName) => {
        if (fileName === "." || fileName === "..") {
            return;
        }
        const _buildPath = path.join(buildDir, fileName);
        const _targetPath = path.join(targetDir, fileName);
        fs.writeFileSync(_targetPath, fs.readFileSync(_buildPath, "utf8"), "utf8");
    })
});