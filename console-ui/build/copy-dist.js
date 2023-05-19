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

const path = require('path');
const fs = require('fs');

const styles = {
  'red': ['\x1B[31m', '\x1B[39m'],
  'green': ['\x1B[32m', '\x1B[39m'],
  'yellow': ['\x1B[33m', '\x1B[39m'],
};

const distPath = path.join(__dirname, '../dist/');
const rootPath = path.join(__dirname, '../../console/src/main/resources/static/');

console.log('\n\n> Start copying the dist directory...\n');

function delDir(dest) {
  let paths = fs.readdirSync(dest);
  paths.forEach(function(p) {
    const target = path.join(dest, p);
    const st = fs.statSync(target);
    if (st.isFile()) {
      console.log(`\r${styles.red[0]}Delete File${styles.red[1]}: ${target}`);
      fs.unlinkSync(target);
    }
    if (st.isDirectory()) {
      console.log(`\r${styles.red[0]}Delete Directory${styles.red[1]}: ${target}`);
      delDir(target);
    }
  });
  paths = fs.readdirSync(dest);
  if (!paths.length) {
    fs.rmdirSync(dest);
  }
}

function copyDir(source, dest) {
  const paths = fs.readdirSync(source);
  paths.forEach(function(p) {
    const src = path.join(source, p);
    const target = path.join(dest, p);
    const st = fs.statSync(src);
    if (st.isFile()) {
      if (fs.existsSync(target)) {
        console.log(`\r${styles.red[0]}Delete File${styles.red[1]}: ${target}`);
        fs.unlinkSync(target);
      }
      console.log(`\r${styles.yellow[0]}Copy File${styles.yellow[1]}: ${target}`);
      const readStream = fs.createReadStream(src);
      const writeStream = fs.createWriteStream(target);
      readStream.pipe(writeStream);
    }
    if (st.isDirectory()) {
      if (fs.existsSync(target)) {
        console.log(`\r${styles.red[0]}Delete Directory${styles.red[1]}: ${target}`);
        delDir(target);
      }
      console.log(`\r${styles.yellow[0]}Create Directory${styles.yellow[1]}: ${target}`);
      fs.mkdirSync(target);
      copyDir(src, target);
    }
  });
}


copyDir(distPath, rootPath);

console.log(`\n>${styles.green[0]} Copy complete!${styles.green[0]}\n`);
