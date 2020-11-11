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
const destDir = path.join(__dirname,
    '../../console/src/main/resources/static/');

const copy = function (src, dst) {
  let paths = fs.readdirSync(src); //同步读取当前目录
  paths.forEach(function (path) {
    const _src = src + '/' + path;
    const _dst = dst + '/' + path;
    fs.stat(_src, function (err, stats) {  //stats  该对象 包含文件属性
      if (err) {
        throw err;
      }
      if (stats.isFile()) { //如果是个文件则拷贝
        let readable = fs.createReadStream(_src);//创建读取流
        let writable = fs.createWriteStream(_dst);//创建写入流
        readable.pipe(writable);
      } else if (stats.isDirectory()) { //是目录则 递归
        checkDirectory(_src, _dst, copy);
      }
    });
  });
}
const checkDirectory = function (src, dst, callback) {
  fs.access(dst, fs.constants.F_OK, (err) => {
    if (err) {
      fs.mkdirSync(dst);
      callback(src, dst);
    } else {
      callback(src, dst);
    }
  });
};

checkDirectory(srcDir,destDir,copy);

