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
import siteConfig from '../config';
import { changeLanguage } from '@/reducers/locale';
import { aliwareIntl } from '@/globalLib';

import './index.scss';

@connect(
  state => ({ ...state.locale }),
  { changeLanguage }
)
@ConfigProvider.config
class Header extends React.Component {
  static displayName = 'Header';

  switchLang = () => {
    const { language = 'en-us', changeLanguage } = this.props;
    const currentLanguage = language === 'en-us' ? 'zh-cn' : 'en-us';
    changeLanguage(currentLanguage);
    aliwareIntl.changeLanguage(currentLanguage);
    document.cookie = `docsite_language=${currentLanguage}`;
    window.location.reload();
  };

  render() {
    const { locale = {}, language = 'en-us' } = this.props;
    const { home, docs, blog, community, languageSwitchButton } = locale;
    const BASE_URL = `https://nacos.io/${language}/`;
    const NAV_MENU = [
      {
        id: 1,
        title: home,
        link: BASE_URL,
      },
      {
        id: 2,
        title: docs,
        link: `${BASE_URL}docs/what-is-nacos.html`,
      },
      {
        id: 3,
        title: blog,
        link: `${BASE_URL}blog/index.html`,
      },
      {
        id: 4,
        title: community,
        link: `${BASE_URL}community/index.html`,
      },
    ];
    return (
      <header className="header-container header-container-primary">
        <div className="header-body">
          <a href="https://nacos.io/zh-cn/" target="_blank" rel="noopener noreferrer">
            <img
              src="img/TB118jPv_mWBKNjSZFBXXXxUFXa-2000-390.svg"
              className="logo"
              alt={siteConfig.name}
              title={siteConfig.name}
            />
          </a>
          <span className="language-switch language-switch-primary" onClick={this.switchLang}>
            {languageSwitchButton}
          </span>
          <div className="header-menu header-menu-open">
            <ul>
              {NAV_MENU.map(item => (
                <li key={item.id} className="menu-item menu-item-primary">
                  <a href={item.link} target="_blank" rel="noopener noreferrer">
                    {item.title}
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
