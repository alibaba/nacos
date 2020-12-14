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
import PropTypes from 'prop-types';
import $ from 'jquery';
import { Button } from '@alifd/next';
import NameSpaceList from '../NameSpaceList';
import { setParams, request } from '../../globalLib';

import './index.scss';

class RegionGroup extends React.Component {
  static propTypes = {
    url: PropTypes.string,
    left: PropTypes.any,
    right: PropTypes.any,
    namespaceCallBack: PropTypes.func,
    setNowNameSpace: PropTypes.func,
  };

  constructor(props) {
    super(props);
    this.state = {
      instanceData: [],
      currRegionId: '',
      url: props.url || '/diamond-ops/env/domain',
      left: props.left,
      right: props.right,
      regionWidth: 700,
      hideRegionList: false,
    };
    this.currRegionId = '';
    this.styles = {
      title: {
        // marginTop: '8px',
        // marginBottom: '8px',
        margin: 0,
        lineHeight: '32px',
        display: 'inline-block',
        textIndent: '8px',
        marginRight: '8px',
        borderLeft: '2px solid #88b7E0',
        fontSize: '16px',
      },
    };
    this.nameSpaceList = React.createRef();
    this.mainRef = null;
    this.titleRef = null;
    this.regionRef = null;
    this.extraRef = null;
    this.resizer = null;
    this.timer = null;
    this.handleResize = this.handleResize.bind(this);
    this.handleAliyunNav = this.handleAliyunNav.bind(this);
    !window.viewframeSetting && (window.viewframeSetting = {});
  }

  componentDidMount() {
    // this.setRegionWidth();
    // window.postMessage({ type: 'CONSOLE_HAS_REGION' }, window.location)
    // $(".aliyun-console-regionbar").show();
    // $(window).bind("resize", this.handleResize);
    // window.addEventListener("message", this.handleAliyunNav);
    // this.getRegionList();
    // setTimeout(() => {
    //     this.setRegionWidth();
    //     this.handleRegionListStatus();
    // });
    const nameSpaceList = this.nameSpaceList.current;
    if (nameSpaceList) {
      nameSpaceList.getInstance().getNameSpaces();
    }
  }

  componentWillUnmount() {
    $(window).unbind('resize', this.handleResize);
    window.postMessage({ type: 'CONSOLE_HIDE_REGION' }, window.location);
    $('.aliyun-console-regionbar').hide();
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    this.setState({
      url: nextProps.url,
      left: nextProps.left,
      right: nextProps.right,
    });
  }

  handleAliyunNav(event = {}) {
    const { type, payload } = (event && event.data) || {};

    switch (type) {
      case 'TOPBAR_SIDEBAR_DID_MOUNT':
        // this.getRegionList();
        this.handleRegionListStatus();
        this.changeRegionBarRegionId(this.currRegionId);
        setTimeout(() => {
          this.changeRegionBarRegionId(this.currRegionId);
        }, 1000);
        break;
      case 'CONSOLE_REGION_CHANGE':
        this.changeTableData(payload.toRegionId);
        break;
      default:
        break;
    }
  }

  handleRegionListStatus() {
    const isPrivateClound = window.globalConfig && window.globalConfig.isParentEdas();
    this.setState(
      {
        hideRegionList: isPrivateClound
          ? false
          : window.location.search.indexOf('hideTopbar=') === -1,
      },
      () => this.setRegionWidth()
    );
  }

  handleResize() {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.setRegionWidth();
    }, 100);
  }

  setRegionWidth() {
    try {
      const mainWidth = $(this.mainRef).width();
      const titleWidth = $(this.titleRef).width();
      const extraWidth = $(this.extraRef).width();
      const regionWidth = mainWidth - extraWidth - titleWidth - 50;
      this.setState({
        regionWidth: regionWidth > 100 ? regionWidth : 100,
      });
    } catch (error) {}
  }

  getRegionList() {
    if (window._regionList) {
      this.handleRegionList(window._regionList);
    } else {
      // TODO
      const nameSpaceList = this.nameSpaceList.current;
      if (nameSpaceList) {
        nameSpaceList.getInstance().getNameSpaces();
      }

      request({
        url: this.state.url,
        data: {},
        success: res => {
          if (res && res.data) {
            window._regionList = res.data;
            this.handleRegionList(res.data);
          }
        },
      });
    }
  }

  handleRegionList(data = {}) {
    let envcontent = '';
    const { envGroups } = data;
    let instanceData = [];
    for (let i = 0; i < envGroups.length; i++) {
      const obj = envGroups[i].envs || [];
      instanceData = obj;
      for (let j = 0; j < obj.length; j++) {
        if (obj[j].active) {
          envcontent = obj[j].serverId;
        }
      }
    }

    this.currRegionId = envcontent || (instanceData[0] && instanceData[0].serverId);
    setParams('serverId', this.currRegionId);

    this.setRegionBarRegionList(instanceData, this.currRegionId);
    this.changeRegionBarRegionId(this.currRegionId);
    setTimeout(() => {
      this.changeRegionBarRegionId(this.currRegionId);
    }, 1000);
    const nameSpaceList = this.nameSpaceList.current;
    if (nameSpaceList) {
      nameSpaceList.getInstance().getNameSpaces();
    }
    this.setState({
      currRegionId: envcontent,
      instanceData,
    });
  }

  changeTableData(serverId) {
    setParams('serverId', serverId);
    if (this.state.currRegionId === serverId) {
      return;
    }
    this.currRegionId = serverId;
    const { instanceData } = this.state;

    let inEdas = false;
    if (window.globalConfig.isParentEdas()) {
      inEdas = true;
    }

    instanceData.forEach(obj => {
      if (obj.serverId === serverId) {
        const lastHash = window.location.hash.split('?')[0];
        if (inEdas) {
          setParams('serverId', obj.serverId);
          const url = window.location.href;
          window.location.href = url;
        } else {
          let url = obj.domain + window.location.search + lastHash;
          if (lastHash.indexOf('serverId') === -1) {
            if (lastHash.indexOf('?') === -1) {
              url += `?serverId=${serverId}`;
            } else {
              url += `&serverId=${serverId}`;
            }
          }
          window.location.href = `${window.location.protocol}//${url}`;
        }
      }
    });
  }

  setRegionBarRegionList(regionList, regionId) {
    if (window.viewframeSetting) {
      window.viewframeSetting.regionList = regionList;
      window.postMessage(
        { type: 'TOGGLE_REGIONBAR_STATUS', payload: { regionList, defaultRegionId: regionId } },
        window.location
      );
    }
  }

  changeRegionBarRegionId(regionId) {
    window.viewframeSetting && (window.viewframeSetting.defaultRegionId = regionId);
    window.postMessage(
      { type: 'SET_ACTIVE_REGION_ID', payload: { defaultRegionId: regionId } },
      window.location
    );
  }

  render() {
    return (
      <div>
        <div ref={ref => (this.mainRef = ref)} className="clearfix">
          <div style={{ overflow: 'hidden' }}>
            <div id="left" style={{ float: 'left', display: 'inline-block', marginRight: 20 }}>
              <div
                ref={ref => (this.titleRef = ref)}
                style={{ display: 'inline-block', verticalAlign: 'top' }}
              >
                {typeof this.state.left === 'string' ? (
                  <h5 style={this.styles.title}>{this.state.left}</h5>
                ) : (
                  this.state.left
                )}
              </div>
              {this.state.hideRegionList ? null : (
                <div
                  ref={ref => (this.regionRef = ref)}
                  style={{
                    width: this.state.regionWidth,
                    display: 'inline-block',
                    lineHeight: '40px',
                    marginLeft: 20,
                  }}
                >
                  {this.state.instanceData.map((val, key) => (
                    <Button
                      key={val.serverId}
                      type={this.state.currRegionId === val.serverId ? 'primary' : 'normal'}
                      style={{
                        fontSize: '12px',
                        marginRight: 10,
                        backgroundColor:
                          this.state.currRegionId === val.serverId ? '#546478' : '#D9DEE4',
                      }}
                      onClick={this.changeTableData.bind(this, val.serverId)}
                    >
                      {' '}
                      {val.name}{' '}
                    </Button>
                  ))}
                </div>
              )}
            </div>
            <div
              ref={ref => (this.extraRef = ref)}
              style={{ float: 'right', display: 'inline-block', paddingTop: 6 }}
            >
              {Object.prototype.toString.call(this.state.right) === '[object Function]'
                ? this.state.right()
                : this.state.right}
            </div>
          </div>
          {this.props.namespaceCallBack && (
            <div>
              <NameSpaceList
                ref={this.nameSpaceList}
                namespaceCallBack={this.props.namespaceCallBack}
                setNowNameSpace={this.props.setNowNameSpace}
              />
            </div>
          )}
        </div>
      </div>
    );
  }
}

export default RegionGroup;
