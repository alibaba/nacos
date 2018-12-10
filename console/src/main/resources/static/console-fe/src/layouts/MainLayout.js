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
import { withRouter } from 'react-router-dom';
import PropTypes from 'prop-types';
import { ConfigProvider, Icon } from '@alifd/next';
import Header from './Header';
import $ from 'jquery';
import { setParams } from '../globalLib';
import { connect } from 'react-redux';

@withRouter
@connect(state => ({ ...state.locale }))
@ConfigProvider.config
class MainLayout extends React.Component {
  static displayName = 'MainLayout';

  static propTypes = {
    navList: PropTypes.array,
    history: PropTypes.object,
    children: PropTypes.any,
  };

  constructor(props) {
    super(props);
    this.initNav = this.props.navList;
    this.deepNav = [];
    this.oneLevelNavArr = {}; // 平行导航map
    this.state = {
      leftBarClose: false,
      showLink: null,
      navRow: [],
      noChild: false,
    };
  }

  goBack() {
    this.props.history.goBack();
  }

  nacosToggleNav(id, event) {
    event.preventDefault();
    const nowNav = document.getElementById(id);
    const iconClass = nowNav.querySelector('.iconshow');
    const subNav = nowNav.querySelector('.subnavlist');
    const { classList } = iconClass;
    let tmpClassName = 'iconshow ';
    for (let i = 0; i < classList.length; i++) {
      if (classList[i] === 'icon-arrow-down') {
        subNav.style.display = 'none';
        subNav.className += ' hidden';
        tmpClassName += 'icon-arrow-right';
      }
      if (classList[i] === 'icon-arrow-right') {
        tmpClassName += 'icon-arrow-down';
        subNav.className = subNav.className.replace(/hidden/g, '');
        subNav.style.display = 'block';
      }
    }
    iconClass.className = tmpClassName;
  }

  nacosGoBack(url) {
    const params = window.location.hash.split('?')[1];
    const urlArr = params.split('&') || [];
    const queryParams = [];
    for (let i = 0; i < urlArr.length; i++) {
      if (
        urlArr[i].split('=')[0] !== '_k' &&
        urlArr[i].split('=')[0] !== 'dataId' &&
        urlArr[i].split('=')[0] !== 'group'
      ) {
        if (urlArr[i].split('=')[0] === 'searchDataId') {
          queryParams.push(`dataId=${urlArr[i].split('=')[1]}`);
        } else if (urlArr[i].split('=')[0] === 'searchGroup') {
          queryParams.push(`group=${urlArr[i].split('=')[1]}`);
        } else {
          queryParams.push(urlArr[i]);
        }
      }
    }
    this.props.history.push(`/${url}?${queryParams.join('&')}`);
  }

  nacosEnterBack() {
    document.getElementById('backarrow').style.color = '#09c';
  }

  nacosOutBack() {
    document.getElementById('backarrow').style.color = '#546478';
  }

  nacosToggleLeftBar() {
    if (!this.nacosOutDom) return;
    if (!this.state.leftBarClose) {
      // 关闭
      this.nacosOutDom.className = 'viewFramework-product';
      this.nacosLeftBarDom.style.width = 0;
      this.nacosBodyDom.style.left = 0;
      this.nacosToggleIconDom.style.left = 0;
    } else {
      this.nacosOutDom.className = 'viewFramework-product viewFramework-product-col-1';
      this.nacosLeftBarDom.style.width = '180px';
      this.nacosBodyDom.style.left = '180px';
      this.nacosToggleIconDom.style.left = '160px';
    }

    this.setState({
      leftBarClose: !this.state.leftBarClose,
    });
  }

  navTo(url) {
    if (url !== '/configdetail' && url !== '/configeditor') {
      // 二级菜单不清空
      setParams({
        dataId: '',
        group: '',
      });
    }

    const params = window.location.hash.split('?')[1];
    const urlArr = params.split('&') || [];
    const queryParams = [];
    for (let i = 0; i < urlArr.length; i++) {
      if (urlArr[i].split('=')[0] !== '_k') {
        queryParams.push(urlArr[i]);
      }
    }

    this.props.history.push(`${url}?${queryParams.join('&')}`);
  }

  nacosSetSpecialNav(item) {
    item.children.forEach(_item => {
      const obj = _item;

      if (obj.dontUseChild === true) {
        obj.parentName = item.title;
        obj.parentId = item.id;
        obj.parentPath = `/${item.id}`;
        this.deepNav.push(obj);
      }
      if (_item.children) {
        this.nacosSetSpecialNav(_item);
      }
    });
  }

  nacosNavAct(serviceName, match, location) {
    if (!match) {
      const formatpath = location.pathname.substr(1); // 得到当前路径
      const nowpathobj = this.oneLevelNavArr[formatpath]; // 根据平行导航匹配父类
      if (nowpathobj) {
        if (nowpathobj.parent === serviceName) {
          // 如果父类等于当前的导航则高亮
          return true;
        }
      }

      return false;
    }
    return true;
  }

  nacosLoopNavDeeply(data, parentServiceName) {
    // 深度遍历获取所有的导航数据
    data.forEach(item => {
      if (item) {
        const navObj = item;

        const _parentServiceName = item.serviceName;
        navObj.parentServiceName = parentServiceName;
        this.oneLevelNavArr[item.serviceName] = navObj; // 得到每一个层级的导航映射
        if (item.children && item.children.length > 0) {
          this.nacosLoopNavDeeply(item.children, _parentServiceName);
        }
      }
    });
  }

  activeNav(id) {
    if (this.preActNav) {
      this.preActNav.removeClass('active');
    }
    const nowNav = $(`#${id}`);
    nowNav.addClass('active');
    this.preActNav = nowNav;
  }

  nacosLoopNav(data, _index = 0, parent) {
    const { locale = {} } = this.props;
    let index = _index;
    // 遍历导航，只显示2级
    const self = this;
    return data.map(item => {
      if (!item) return '';
      index++;
      if (item.dontUseChild === true) return '';
      if (item.children && item.children.length > 0) {
        if (item.isVirtual) {
          // 如果是虚拟菜单需要增加展开箭头
          const icon = item.isExtend ? (
            <span className="icon-arrow-down iconshow" />
          ) : (
            <span className="icon-arrow-right iconshow" />
          );
          const hiddenClass = item.isExtend ? '' : 'hidden';
          return (
            <li
              key={`${item.serviceName}`}
              data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
              id={`${item.serviceName}`}
            >
              <div>
                <a href="" onClick={this.nacosToggleNav.bind(this, item.serviceName)}>
                  <div className="nav-icon">{icon}</div>
                  <div className="nav-title">{locale[item.serviceName]}</div>
                </a>
              </div>
              <ul className={`subnavlist ${hiddenClass}`}>
                {self.nacosLoopNav(item.children, index)}
              </ul>
            </li>
          );
        } else {
          return (
            <li
              key={`${item.serviceName}`}
              data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
              onClick={this.navTo.bind(this, `/${item.serviceName}`)}
            >
              <a
                href={'javascript:;'}
                id={`${item.serviceName}`}
                onClick={this.activeNav.bind(this, `nav${index}`)}
              >
                <div className="nav-icon" />
                <div className="nav-title">{locale[item.serviceName]}</div>
              </a>
            </li>
          );
        }
      }
      return (
        <li
          key={`${item.serviceName}`}
          data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
          onClick={this.navTo.bind(this, `/${item.serviceName}`)}
        >
          <a
            href={'javascript:;'}
            id={`${item.serviceName}`}
            onClick={this.activeNav.bind(this, `nav${index}`)}
          >
            <div className="nav-icon" />
            <div className="nav-title">{locale[item.serviceName]}</div>
          </a>
        </li>
      );
    });
  }

  nacosGetNav(navList) {
    let navRow = ''; // 导航
    if (navList.length > 0) {
      navRow = <ul>{this.nacosLoopNav(navList)}</ul>;
      this.nacosLoopNavDeeply(navList); // 深度遍历导航树获得平行map
    }
    return navRow;
  }

  renderNav() {
    this.nacosLeftBarDom = document.getElementById('viewFramework-product-navbar');
    this.nacosBodyDom = document.getElementById('viewFramework-product-body');
    this.nacosToggleIconDom = document.getElementById('viewFramework-product-navbar-collapse');
    this.nacosOutDom = document.getElementById('page-header-mask');
    // let parentNav = this.initNav[0] || [];
    const defaultNav = '/configurationManagement';
    // let childrenNav = parentNav.children || [];
    this.props.history.listen(location => {
      if (this.preSimplePath && this.preSimplePath !== '/') {
        if (location.pathname.indexOf(this.preSimplePath) !== -1) {
          return;
        }
      }
      const simplePath = window.location.hash.split('?')[0];
      const navName = simplePath.substr('2');
      this.preSimplePath = simplePath;

      if (navName === '') {
        this.props.history.push(defaultNav);
        setTimeout(() => {
          this.activeNav('configurationManagement');
        });
        return;
      }

      const nowNavObj = this.oneLevelNavArr[navName];
      if (!nowNavObj) {
        this.setState({
          noChild: true,
        });
        return;
      }
      this.setState({
        noChild: !!nowNavObj.dontUseChild,
      });
      const { parentServiceName } = nowNavObj;

      const parentNav = this.oneLevelNavArr[parentServiceName];
      if (simplePath !== '/' && nowNavObj && parentNav && !parentNav.isVirtual) {
        this.setState({
          showLink: (
            <div>
              <Icon
                type="arrow-left"
                onClick={this.nacosGoBack.bind(this, parentServiceName)}
                id={'backarrow'}
                onMouseOver={this.nacosEnterBack.bind(this)}
                onMouseLeave={this.nacosOutBack.bind(this)}
                style={{
                  marginLeft: 77,
                  marginTop: 0,
                  fontWeight: 'bold',
                  cursor: 'pointer',
                  color: '#546478',
                  fontSize: '20px',
                }}
              />
            </div>
          ),

          navRow: <ul>{this.nacosLoopNav([nowNavObj])}</ul>,
        });
        setTimeout(() => {
          const navid = navName;
          this.activeNav(navid);
        });
      } else {
        this.setState({
          showLink: null,
          navRow: <ul>{this.nacosLoopNav(this.initNav)}</ul>,
        });
        setTimeout(() => {
          const navid = navName;
          this.activeNav(navid);
        });
      }
    });
  }

  componentWillReceiveProps() {
    setTimeout(() => {
      const nav = this.props.navList || [];
      const navRow = this.nacosGetNav(nav);
      this.setState({ navRow }, () => this.renderNav());
    });
  }

  render() {
    const { locale = {} } = this.props;
    const { nacosVersion, nacosName, doesNotExist } = locale;
    const { showLink, navRow, leftBarClose, noChild } = this.state;
    return (
      <div className="viewFramework-product" style={{ top: 66 }}>
        <Header />
        <div
          className="viewFramework-product-navbar"
          style={{ width: 180, marginLeft: 0 }}
          id="viewFramework-product-navbar"
          data-spm="acm_nav"
        >
          <div className="viewFramework-product-navbar-removed">
            <div>
              <div className="product-nav-scene product-nav-main-scene">
                {showLink ? (
                  <div className="product-nav-icon env" style={{ height: 80, paddingTop: 25 }}>
                    {showLink}
                  </div>
                ) : (
                  <div style={{ textIndent: 0 }} className={'product-nav-title'} title={nacosName}>
                    <span>{nacosName}</span>
                    <span style={{ marginLeft: 5 }}>{nacosVersion}</span>
                  </div>
                )}

                <div
                  className="product-nav-list"
                  style={{ position: 'relative', top: 0, height: '100%' }}
                >
                  {navRow}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div
          className="viewFramework-product-navbar-collapse"
          id="viewFramework-product-navbar-collapse"
          onClick={this.nacosToggleLeftBar.bind(this)}
        >
          <div className="product-navbar-collapse-inner">
            <div className="product-navbar-collapse-bg" />
            <div className="product-navbar-collapse">
              {leftBarClose ? (
                <span className="icon-collapse-right" style={{ display: 'block' }} />
              ) : (
                <span className="icon-collapse-left" />
              )}
            </div>
          </div>
        </div>
        <div
          className="viewFramework-product-body"
          style={{ marginLeft: 180 }}
          id="viewFramework-product-body"
        >
          <div>
            {!noChild ? (
              <div>{this.props.children}</div>
            ) : (
              <div
                style={{ height: 300, lineHeight: '300px', textAlign: 'center', fontSize: '18px' }}
              >
                {doesNotExist}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }
}

export default MainLayout;
