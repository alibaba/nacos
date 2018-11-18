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
    filename: './js/[name].[chunkhash:8].js',
    path: path.resolve(__dirname, '../dist'),
  },
  resolve: {
    extensions: ['.js', '.jsx', '.json'],
    alias: {
      '@': resolve('src'),
      'utils': resolve('src/utils'),
      'components': resolve('src/components'),
    },
  },
  module: {
    rules: [{
      test: /\.(css|scss)$/,
      use: [
        isDev ? 'style-loader' : MiniCssExtractPlugin.loader,
        'css-loader',
        'sass-loader',
      ],
    }, {
      test: /\.(js|jsx)$/,
      loader: 'eslint-loader',
      enforce: 'pre',
      include: [resolve('src')],
    }, {
      test: /\.(js|jsx)$/,
      exclude: /node_modules/,
      use: ['babel-loader'],
    }, {
      test: [/\.bmp$/, /\.gif$/, /\.jpe?g$/, /\.png$/],
      loader: 'url-loader',
      options: {
        limit: 10000,
        name: '/images/[name].[hash:8].[ext]',
      },
    }, {
      test: /\.(ttf|woff|svg)$/,
      use: [{
        loader: 'url-loader',
        options: {
          name: '/fonts/[name].[hash:8].[ext]',
        },
      }],

    }],
  },
  plugins: [
    new HtmlWebpackPlugin({
      filename: 'index.html',
      template: './public/index.html',
      minify: !isDev,
    }),
    new CopyWebpackPlugin([
      {
        from: path.resolve(__dirname, '../public'),
        to: './',
        ignore: ['index.html'],
      },
    ]),
  ],
};
