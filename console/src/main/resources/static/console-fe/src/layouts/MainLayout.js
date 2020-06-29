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
import { connect } from 'react-redux';
import PropTypes from 'prop-types';
import { ConfigProvider, Icon, Menu } from '@alifd/next';
import Header from './Header';
import { getState } from '../reducers/base';
import getMenuData from './menu';

const { SubMenu, Item } = Menu;

@withRouter
@connect(state => ({ ...state.locale, ...state.base }), { getState })
@ConfigProvider.config
class MainLayout extends React.Component {
  static displayName = 'MainLayout';

  static propTypes = {
    locale: PropTypes.object,
    location: PropTypes.object,
    history: PropTypes.object,
    version: PropTypes.any,
    getState: PropTypes.func,
    functionMode: PropTypes.string,
    children: PropTypes.object,
  };

  componentDidMount() {
    this.props.getState();
  }

  goBack() {
    this.props.history.goBack();
  }

  navTo(url) {
    const { search } = this.props.location;
    this.props.history.push([url, search].join(''));
  }

  isCurrentPath(url) {
    const { location } = this.props;
    return url === location.pathname ? 'current-path' : undefined;
  }

  defaultOpenKeys() {
    const MenuData = getMenuData(this.props.functionMode);
    for (let i = 0, len = MenuData.length; i < len; i++) {
      const { children } = MenuData[i];
      if (children && children.filter(({ url }) => url === this.props.location.pathname).length) {
        return String(i);
      }
    }
  }

  isShowGoBack() {
    const urls = [];
    const MenuData = getMenuData(this.props.functionMode);
    MenuData.forEach(item => {
      if (item.url) urls.push(item.url);
      if (item.children) item.children.forEach(({ url }) => urls.push(url));
    });
    return !urls.includes(this.props.location.pathname);
  }

  render() {
    const { locale = {}, version, functionMode } = this.props;
    const MenuData = getMenuData(functionMode);
    return (
      <>
        <Header />
        <div className="main-container">
          <div className="left-panel">
            {this.isShowGoBack() ? (
              <div className="go-back" onClick={() => this.goBack()}>
                <Icon type="arrow-left" />
              </div>
            ) : (
              <>
                <h1 className="nav-title">
                  {locale.nacosName}
                  <span>{version}</span>
                </h1>
                <Menu
                  defaultOpenKeys={this.defaultOpenKeys()}
                  className="nav-menu"
                  openMode="single"
                >
                  {MenuData.map((subMenu, idx) => {
                    if (subMenu.children) {
                      return (
                        <SubMenu key={String(idx)} label={locale[subMenu.key]}>
                          {subMenu.children.map((item, i) => (
                            <Item
                              key={[idx, i].join('-')}
                              onClick={() => this.navTo(item.url)}
                              className={this.isCurrentPath(item.url)}
                            >
                              {locale[item.key]}
                            </Item>
                          ))}
                        </SubMenu>
                      );
                    }
                    return (
                      <Item
                        key={idx}
                        className={['first-menu', this.isCurrentPath(subMenu.url)]
                          .filter(c => c)
                          .join(' ')}
                        onClick={() => this.navTo(subMenu.url)}
                      >
                        {locale[subMenu.key]}
                      </Item>
                    );
                  })}
                </Menu>
              </>
            )}
          </div>
          <div className="right-panel">{this.props.children}</div>
        </div>
      </>
    );
  }
}

export default MainLayout;
