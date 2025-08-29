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

import React from 'react';
import { withRouter } from 'react-router-dom';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { ConfigProvider, Dropdown, Menu, Message } from '@alifd/next';
import siteConfig from '../config';
import { changeLanguage } from '@/reducers/locale';
import PasswordReset from '../pages/AuthorityControl/UserManagement/PasswordReset';
import { passwordReset } from '../reducers/authority';

import './index.scss';

@withRouter
@connect(state => ({ ...state.locale }), { changeLanguage })
@ConfigProvider.config
class Header extends React.Component {
  static displayName = 'Header';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
    location: PropTypes.object,
    language: PropTypes.string,
    changeLanguage: PropTypes.func,
  };

  state = { passwordResetUser: '' };

  switchLang = () => {
    const { language = 'en-US', changeLanguage } = this.props;
    const currentLanguage = language === 'en-US' ? 'zh-CN' : 'en-US';
    changeLanguage(currentLanguage);
  };

  logout = () => {
    window.localStorage.clear();
    this.props.history.push('/login');
  };

  changePassword = () => {
    this.setState({
      passwordResetUser: this.getUsername(),
      passwordResetUserVisible: true,
    });
  };

  getUsername = () => {
    const token = window.localStorage.getItem('token');
    if (token) {
      const [, base64Url = ''] = token.split('.');
      const base64 = base64Url.replace('-', '+').replace('_', '/');
      try {
        const parsedToken = JSON.parse(decodeURIComponent(escape(window.atob(base64))));
        return parsedToken.sub;
      } catch (e) {
        delete localStorage.token;
        location.reload();
      }
    }
    return '';
  };

  indexAction = () => {
    this.props.history.push('/');
  };

  render() {
    const {
      locale = {},
      language = 'en-us',
      location: { pathname },
    } = this.props;
    const { home, enterprise, mcp, docs, blog, community, languageSwitchButton } = locale;
    const { passwordResetUser = '', passwordResetUserVisible = false } = this.state;
    const BASE_URL =
      language.toLocaleLowerCase() === 'en-us' ? 'https://nacos.io/en/' : 'https://nacos.io/';
    const NAV_MENU = [
      { id: 1, title: home, link: BASE_URL },
      {
        id: 2,
        title: enterprise,
        link: 'https://cn.aliyun.com/product/aliware/mse?spm=nacos-website.topbar.0.0.0',
        tag: 'hot',
      },
      {
        id: 3,
        title: mcp,
        link: `https://mcp.nacos.io?spm=nacos-website.topbar.0.0.0`,
        tag: 'hot',
      },
      { id: 4, title: docs, link: `${BASE_URL}docs/latest/what-is-nacos/` },
      { id: 5, title: blog, link: `${BASE_URL}blog/` },
      { id: 6, title: community, link: `${BASE_URL}news/` },
    ];
    return (
      <>
        <header className="header-container header-container-primary">
          <div className="header-body">
            <a href="#" onClick={this.indexAction} rel="noopener noreferrer">
              <img
                src="img/logo-2000-390.svg"
                className="logo"
                alt={siteConfig.name}
                title={siteConfig.name}
              />
            </a>
            {/* if is login page, we will show logout */}
            {pathname !== '/login' && (
              <Dropdown trigger={<div className="logout">{this.getUsername()}</div>}>
                <Menu>
                  <Menu.Item onClick={this.logout}>{locale.logout}</Menu.Item>
                  <Menu.Item onClick={this.changePassword}>{locale.changePassword}</Menu.Item>
                </Menu>
              </Dropdown>
            )}
            <span className="language-switch language-switch-primary" onClick={this.switchLang}>
              {languageSwitchButton}
            </span>
            <div className="header-menu header-menu-open">
              <ul>
                {NAV_MENU.map(item => (
                  <li key={item.id} className="menu-item menu-item-primary">
                    <a href={item.link} target="_blank" rel="noopener noreferrer">
                      {item.title}
                      {item.tag && item.tag === 'hot' ? (
                        <svg
                          className="icon ml-1"
                          viewBox="0 0 1024 1024"
                          version="1.1"
                          xmlns="http://www.w3.org/2000/svg"
                          p-id="1452"
                          width="14"
                          height="14"
                          data-spm-anchor-id="5238cd80.2ef5001f.0.i1.3f613b7cMyKPOJ"
                        >
                          <path
                            d="M758.915413 332.8L684.390969 409.6s0-307.2-248.433778-409.6c0 0-24.860444 281.6-149.048889 384C162.549191 486.4-85.884587 793.6 411.039858 1024c0 0-248.490667-281.6 74.524444-486.4 0 0-24.803556 102.4 99.441778 204.8 124.188444 102.4 0 281.6 0 281.6s596.309333-153.6 173.909333-691.2"
                            fill="#FF0000"
                            p-id="1453"
                          ></path>
                        </svg>
                      ) : (
                        ''
                      )}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </header>
        <PasswordReset
          visible={passwordResetUserVisible}
          username={passwordResetUser}
          onOk={user =>
            passwordReset(user).then(res => {
              if (res.code === 200) {
                Message.success(locale.PasswordReset.resetSuccessfully);
              }
              return res;
            })
          }
          onCancel={() =>
            this.setState({ passwordResetUser: undefined, passwordResetUserVisible: false })
          }
        />
      </>
    );
  }
}

export default Header;
