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
import { ConfigProvider, Dropdown, Menu } from '@alifd/next';
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
    const { home, docs, blog, community, enterprise, languageSwitchButton } = locale;
    const { passwordResetUser = '', passwordResetUserVisible = false } = this.state;
    const BASE_URL = `https://nacos.io/${language.toLocaleLowerCase()}/`;
    const NAV_MENU = [
      { id: 1, title: home, link: BASE_URL },
      { id: 2, title: docs, link: `${BASE_URL}docs/what-is-nacos.html` },
      { id: 3, title: blog, link: `${BASE_URL}blog/index.html` },
      { id: 4, title: community, link: `${BASE_URL}community/index.html` },
      {
        id: 5,
        title: enterprise,
        link: 'https://cn.aliyun.com/product/aliware/mse?spm=nacos-website.topbar.0.0.0',
      },
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
