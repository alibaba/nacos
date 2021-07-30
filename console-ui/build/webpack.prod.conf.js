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
const base = require('./webpack.base.conf');
const UglifyJsPlugin = require('uglifyjs-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const OptimizeCSSAssetsPlugin = require('optimize-css-assets-webpack-plugin');
const { CleanWebpackPlugin } = require('clean-webpack-plugin');

const [cssLoader]  = base.module.rules;
cssLoader.use.push({
  loader: '@alifd/next-theme-loader',
  options: {
    modifyVars: {
      '$icon-font-path': '"/nacos/console-ui/public/icons/icon-font"',
      '$font-custom-path': '"/nacos/console-ui/public/fonts/"'
    }
  }
})
module.exports = Object.assign({}, base, {
  optimization: {
    minimizer: [
      new UglifyJsPlugin({
        cache: true,
        parallel: true
      }),
      new OptimizeCSSAssetsPlugin({}),
    ],
  },
  plugins: [
    new CleanWebpackPlugin({
      cleanOnceBeforeBuildPatterns:[
        path.resolve(__dirname, '../dist/**'),
      ]
    }),
    ...base.plugins,
    new MiniCssExtractPlugin({
      filename: './css/[name].css',
      chunkFilename: '[id].css',
    }),
  ],
  mode: 'production'
});
