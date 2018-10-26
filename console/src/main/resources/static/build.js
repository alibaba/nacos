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
        // 复制index.js
        const copyFileList = ['index.js', 'index.css'];
        copyFileList.forEach((fileName) => {
            const _srcFileName = path.join(buildDir, fileName);
            const _targetFileName = path.join(targetDir, fileName);

            fs.writeFileSync(_targetFileName, fs.readFileSync(_srcFileName, "utf8"), "utf8");
        });
    });