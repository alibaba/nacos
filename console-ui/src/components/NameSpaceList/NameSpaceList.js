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
import { ConfigProvider, Dialog, Select } from '@alifd/next';
import { getParams, setParams, request } from '../../globalLib';

import './index.scss';
import { NAME_SHOW } from '../../constants';

/**
 * 命名空间列表
 */
@ConfigProvider.config
class NameSpaceList extends React.Component {
  static displayName = 'NameSpaceList';

  static propTypes = {
    locale: PropTypes.object,
    setNowNameSpace: PropTypes.func,
    namespaceCallBack: PropTypes.func,
    title: PropTypes.string,
  };

  constructor(props) {
    super(props);
    this._namespace = getParams('namespace') || '';
    // this._namespaceShowName = getParams('namespaceShowName') || '';
    this.state = {
      nownamespace: window.nownamespace || this._namespace || '',
      namespaceList: window.namespaceList || [],
      nameShow: localStorage.getItem(NAME_SHOW),
      // namespaceShowName: window.namespaceShowName || this._namespaceShowName || '',
      // _dingdingLink: "",
      // _forumLink: ""
    };
  }

  componentDidMount() {
    // this.getLink("dingding", "_dingdingLink");
    // this.getLink("discuz", "_forumLink");
  }

  getLink(linkKey, keyName) {
    if (window[keyName] === null) {
      request({
        url: 'com.alibaba.nacos.service.getLink',
        data: {
          linkKey,
        },
        success: res => {
          if (res.code === 200) {
            window[keyName] = res.data;
            this.setState({
              [keyName]: res.data,
            });
          }
        },
      });
    } else {
      this.setState({
        [keyName]: window[keyName],
      });
    }
  }

  // if (!this.state.namespaceList || this.state.namespaceList.length === 0) {
  //     this.getNameSpaces();
  // } else {
  //     this.calleeParent();
  // }

  /**
   切换namespace
   * */
  changeNameSpace(ns, nsName, nsDesc) {
    localStorage.setItem('namespace', ns);
    this.setnamespace(ns || '');
    setParams({
      namespace: ns || '',
      namespaceShowName: nsName,
    });
    window.nownamespace = ns;
    window.namespaceShowName = nsName;
    window.namespaceDesc = nsDesc;

    this.calleeParent(true);
    this.props.setNowNameSpace && this.props.setNowNameSpace(nsName, ns, nsDesc);
  }

  changeName(...value) {
    let space = value[2];
    this.changeNameSpace(space.namespace, space.namespaceShowName, space.namespaceDesc);
  }

  calleeParent(needclean = false) {
    this.props.namespaceCallBack && this.props.namespaceCallBack(needclean);
  }

  getNameSpaces() {
    const { locale = {} } = this.props;
    if (window.namespaceList && window.namespaceList.length) {
      this.handleNameSpaces(window.namespaceList);
    } else {
      request({
        type: 'get',
        url: 'v1/console/namespaces',
        success: res => {
          if (res.code === 200) {
            this.handleNameSpaces(res.data);
          } else {
            Dialog.alert({
              title: locale.notice,
              content: res.message,
            });
          }
        },
        error: () => {
          window.namespaceList = [];
          this.handleNameSpaces(window.namespaceList);
        },
      });
    }
  }

  handleNameSpaces(data) {
    const nownamespace = getParams('namespace') || '';

    // let namespaceShowName = this._namespaceShowName || data[0].namespaceShowName || '';
    window.namespaceList = data;
    window.nownamespace = nownamespace;
    let namespaceShowName = '';
    let namespaceDesc = '';
    for (let i = 0; i < data.length; i++) {
      if (data[i].namespace === nownamespace) {
        ({ namespaceShowName } = data[i]);
        ({ namespaceDesc } = data[i]);
        break;
      }
    }
    window.namespaceShowName = namespaceShowName;
    window.namespaceDesc = namespaceDesc;
    setParams('namespace', nownamespace || '');
    localStorage.setItem('namespace', nownamespace);
    // setParams('namespaceShowName', namespaceShowName);
    this.props.setNowNameSpace &&
      this.props.setNowNameSpace(namespaceShowName, nownamespace, namespaceDesc);
    this.setState({
      nownamespace,
      namespaceList: data,
    });
    this.calleeParent();
  }

  setnamespace(ns) {
    this.setState({
      nownamespace: ns,
    });
  }

  rendernamespace(namespaceList) {
    const { nownamespace, nameShow } = this.state; // 获得当前namespace
    if (nameShow && nameShow === 'select') {
      let de = {
        value: nownamespace,
      };
      namespaceList.forEach(obj => {
        obj.label = obj.namespaceShowName + ' ' + (obj.namespaceDesc ? obj.namespaceDesc : '');
        obj.value = obj.namespace;
        if (obj.value !== undefined && obj.value === de.value) {
          de = obj;
        }
      });
      return (
        <Select
          style={{ width: 200 }}
          size="medium"
          dataSource={namespaceList}
          value={de}
          onChange={this.changeName.bind(this)}
          showSearch
        />
      );
    }
    const namespacesBtn = namespaceList.map((obj, index) => {
      return (
        <div key={index} style={{ cursor: 'pointer' }}>
          {index === 0 ? '' : <span style={{ marginRight: 8, color: '#999' }}>|</span>}
          <span
            className={obj.namespace === nownamespace ? 'naming-focus' : 'naming-simple'}
            onClick={this.changeNameSpace.bind(
              this,
              obj.namespace,
              obj.namespaceShowName,
              obj.namespaceDesc
            )}
            key={index}
          >
            {obj.namespaceShowName}
          </span>
        </div>
      );
    });
    return namespacesBtn;
  }

  render() {
    const namespaceList = this.state.namespaceList || [];
    const title = this.props.title || '';

    return (
      <div
        className={namespaceList.length ? 'namespacewrapper' : ''}
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          marginTop: 8,
          marginBottom: 16,
        }}
      >
        {}
        {title ? (
          <p
            style={{
              height: 30,
              lineHeight: '30px',
              paddingTop: 0,
              paddingBottom: 0,
              borderLeft: '2px solid #09c',
              float: 'left',
              margin: 0,
              paddingLeft: 10,
            }}
          >
            {this.props.title}
          </p>
        ) : (
          ''
        )}
        {this.rendernamespace(namespaceList)}
      </div>
    );
  }
}

export default NameSpaceList;
