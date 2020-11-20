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
const webpack = require('webpack');
const base = require('./webpack.base.conf');

const [cssLoader]  = base.module.rules;
cssLoader.use.push({
  loader: '@alifd/next-theme-loader',
  options: {
    modifyVars: {
      '$icon-font-path': '"/icons/icon-font"',
      '$font-custom-path': '"/fonts/"'
    }
  }
})
module.exports = Object.assign({}, base, {
  output: {
    filename: './js/[name].js',
    path: path.resolve(__dirname, '../dist'),
  },
  devServer: {
    port: process.env.PORT || 8000,
    proxy: [{
      context: ['/'],
      changeOrigin: true,
      secure: false,
      target: 'http://localhost:8848',
      pathRewrite: {'^/v1' : '/nacos/v1'}
    }],
    disableHostCheck: true,
    open: true,
    hot: true,
    overlay: true
  },
  mode: 'development',
  devtool: 'eval-source-map',
  plugins: [
    ...base.plugins,
    new webpack.HotModuleReplacementPlugin()
  ]
});
