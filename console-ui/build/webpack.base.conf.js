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
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const isDev = process.env.NODE_ENV !== 'production';

function resolve(dir) {
  return path.join(__dirname, '..', dir);
}

module.exports = {
  entry: {
    main: './src/index.js',
  },
  output: {
    filename: './js/[name].js',
    path: path.resolve(__dirname, '../dist'),
  },
  resolve: {
    extensions: ['.js', '.jsx', '.json', '.ts', '.tsx'],
    alias: {
      '@': resolve('src'),
      utils: resolve('src/utils'),
      components: resolve('src/components'),
    },
  },
  externals: {
    jquery: 'jQuery'
  },
  node: {
    fs: 'empty'
  },
  module: {
    rules: [
      {
        test: /\.(css|scss)$/,
        use: [isDev ? 'style-loader' : MiniCssExtractPlugin.loader, 'css-loader', 'sass-loader'],
      },
      {
        test: /\.(js|jsx)$/,
        loader: 'eslint-loader',
        enforce: 'pre',
        include: [resolve('src')],
      },
      {
        test: /\.(js|jsx|ts|tsx)$/,
        include: [resolve('src')],
        use: ['babel-loader'],
      },
      {
        test: [/\.bmp$/, /\.gif$/, /\.jpe?g$/, /\.png$/],
        loader: 'url-loader',
        options: {
          limit: 10000,
          name: '/img/[name].[hash:8].[ext]',
        },
      },
      {
        test: /\.(ttf|woff|svg)$/,
        use: [
          {
            loader: 'url-loader',
            options: {
              name: '/fonts/[name].[hash:8].[ext]',
            },
          },
        ],
      },
    ],
  },
  plugins: [
    new HtmlWebpackPlugin({
      filename: 'index.html',
      template: './public/index.html',
      minify: !isDev,
    }),
    new CopyWebpackPlugin([
      {
        from: resolve('../console/src/main/resources/static/console-ui/public'),
        to: './',
        ignore: ['index.html'],
      },
    ]),
  ],
};
