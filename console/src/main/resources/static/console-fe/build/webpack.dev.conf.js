const base = require('./webpack.base.conf')

module.exports = Object.assign({}, base, {
    devServer: {
        port: 8000,
        proxy: [{
            context: ['/'],
            changeOrigin: true,
            secure: false,
            target: 'http://11.163.128.36:8848'
        }],
        disableHostCheck: true
    },
    mode: 'development'
})
