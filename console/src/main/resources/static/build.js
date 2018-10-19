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