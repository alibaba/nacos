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

import React from 'react';
import { connect } from 'react-redux';
import { ConfigProvider } from '@alifd/next';
import classnames from 'classnames';
import siteConfig from '../config';
import { getLink } from '../utils/nacosutil';
import { changeLanguage } from '../reducers/locale';

import './index.scss';

const languageSwitch = [
  {
    text: '中',
    value: 'en-us',
  },
  {
    text: 'En',
    value: 'zh-cn',
  },
];
const noop = () => {};

@connect(
  state => ({ ...state.locale }),
  { changeLanguage }
)
@ConfigProvider.config
class Header extends React.Component {
  static displayName = 'Header';

  constructor(props) {
    super(props);
    this.state = {
      menuBodyVisible: false,
    };

    this.switchLang = this.switchLang.bind(this);
  }

  toggleMenu() {
    this.setState({
      menuBodyVisible: !this.state.menuBodyVisible,
    });
  }

  switchLang() {
    const { language = 'en-US', changeLanguage } = this.props;
    changeLanguage(language === 'en-US' ? 'zh-CN' : 'en-US');
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    this.setState({
      language: nextProps.language,
    });
  }

  render() {
    const { locale = {}, language = 'en-US' } = this.props;
    const { home, docs, blog, community, languageSwitchButton } = locale;
    const { type, logo, onLanguageChange, currentKey } = this.props;
    const { menuBodyVisible } = this.state;
    return (
      <header
        className={classnames({
          'header-container': true,
          [`header-container-${type}`]: true,
        })}
      >
        <div className="header-body">
          <a href="https://nacos.io/zh-cn/" target="_blank" rel="noopener noreferrer">
            <img className="logo" alt={siteConfig.name} title={siteConfig.name} src={logo} />
          </a>
          {onLanguageChange !== noop && (
            <span
              className={classnames({
                'language-switch': true,
                [`language-switch-${type}`]: true,
              })}
              onClick={this.switchLang}
            >
              {languageSwitch.find(lang => lang.value === language).text}
            </span>
          )}
          <div
            className={classnames({
              'header-menu': true,
              'header-menu-open': menuBodyVisible,
            })}
          >
            <ul>
              {[
                [home, 'https://nacos.io/en-us/index.html'],
                [docs, 'https://nacos.io/en-us/docs/quick-start.html'],
                [blog, 'https://nacos.io/en-us/blog'],
                [community, 'https://nacos.io/en-us/community'],
              ].map(([text, link]) => (
                <li
                  key={text}
                  className={classnames({
                    'menu-item': true,
                    [`menu-item-${type}`]: true,
                    [`menu-item-${type}-active`]: false,
                  })}
                >
                  <a href={link} target="_blank" rel="noopener noreferrer">
                    {text}
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </header>
    );
  }
}

export default Header;
