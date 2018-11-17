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

/**
 * 入口页
 */
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { connect } from 'dva';
import MainLayout from '../layouts/MainLayout';
import { Message, Loading } from '@alifd/next';
import _menu from '../menu';
import { nacosEvent } from '../globalLib';

class App extends Component {
  constructor(props) {
    super(props);
    this.state = {
      shownotice: 'none',
      noticecontent: '',
      nacosLoading: {},
    };
  }

  componentDidMount() {
    // 监听loading事件
    nacosEvent.listenAllTask('nacosLoadingEvent', nacosLoading => {
      this.setState({
        nacosLoading,
      });
    });
  }

  UNSAFE_componentWillUpdate(nextProps, nextState) {
    const { errcode, errinfo } = nextProps;
    if (errcode === 1) {
      this.openErr(errinfo);
    }
  }

  componentWillUnmount() {
    nacosEvent.remove('nacosLoadingEvent');
  }

  openErr(message) {
    const self = this;
    setTimeout(() => {
      self.props.dispatch({ type: 'error/clear' });
    }, 3000);
  }

  getChildContext() {
    return { history: this.props.history };
  }

  render() {
    const { errcode, errinfo } = this.props;
    return (
      <Loading
        className="nacos-loading"
        shape="flower"
        tip="loading..."
        visible={false}
        fullScreen
        {...this.state.nacosLoading}
      >
        <MainLayout {...this.props} navList={_menu.data}>
          {errcode === 1 ? (
            <Message
              title={errinfo}
              closable
              style={{
                position: 'absolute',
                zIndex: 99999,
                width: 800,
                left: '50%',
                marginLeft: -400,
              }}
            />
          ) : null}
          {this.props.children}
        </MainLayout>
      </Loading>
    );
  }
}

App.propTypes = {};
App.childContextTypes = {
  history: PropTypes.object,
};
function mapStateToProps(state) {
  const { errinfo, errcode } = state.error;
  return {
    errinfo,
    errcode,
  };
}
export default connect(mapStateToProps)(App);
