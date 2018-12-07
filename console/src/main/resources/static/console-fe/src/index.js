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
import React from 'react';
import ReactDOM from 'react-dom';
import { createStore, combineReducers, compose, applyMiddleware } from 'redux';
import { routerReducer } from 'react-router-redux';
import thunk from 'redux-thunk';
import { Provider, connect } from 'react-redux';
import { HashRouter, Route, Switch, Redirect } from 'react-router-dom';
import { ConfigProvider, Loading } from '@alifd/next';

import _menu from './menu';

import Layout from './layouts/MainLayout';
import CookieHelp from './utils/cookie';
import { LANGUAGE_KEY } from './constants';

import Namespace from './pages/NameSpace';
import Newconfig from './pages/ConfigurationManagement/NewConfig';
import Configsync from './pages/ConfigurationManagement/ConfigSync';
import Configdetail from './pages/ConfigurationManagement/ConfigDetail';
import Configeditor from './pages/ConfigurationManagement/ConfigEditor';
import HistoryDetail from './pages/ConfigurationManagement/HistoryDetail';
import ConfigRollback from './pages/ConfigurationManagement/ConfigRollback';
import HistoryRollback from './pages/ConfigurationManagement/HistoryRollback';
import ListeningToQuery from './pages/ConfigurationManagement/ListeningToQuery';
import ConfigurationManagement from './pages/ConfigurationManagement/ConfigurationManagement';
import ServiceList from './pages/ServiceManagement/ServiceList';
import ServiceDetail from './pages/ServiceManagement/ServiceDetail';

import reducers from './reducers';
import { changeLanguage } from './reducers/locale';

import './index.scss';

module.hot && module.hot.accept();

if (!CookieHelp.getValue(LANGUAGE_KEY)) {
  CookieHelp.setValue(LANGUAGE_KEY, navigator.language === 'zh-CN' ? 'zh-cn' : 'en-us');
}

const reducer = combineReducers({
  ...reducers,
  routing: routerReducer,
});

const store = createStore(
  reducer,
  compose(
    applyMiddleware(thunk),
    window.devToolsExtension ? window.devToolsExtension() : f => f
  )
);

@connect(
  state => ({ ...state.locale }),
  { changeLanguage }
)
class App extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      shownotice: 'none',
      noticecontent: '',
      nacosLoading: {},
    };
  }

  componentDidMount() {
    const language = CookieHelp.getValue(LANGUAGE_KEY);
    this.props.changeLanguage(language);
  }

  static generateRouter() {
    return (
      <HashRouter>
        <Layout navList={_menu.data}>
          <Switch>
            <Route path="/" exact render={() => <Redirect to="/configurationManagement" />} />
            <Route path="/namespace" component={Namespace} />
            <Route path="/newconfig" component={Newconfig} />
            <Route path="/configsync" component={Configsync} />
            <Route path="/configdetail" component={Configdetail} />
            <Route path="/configeditor" component={Configeditor} />
            <Route path="/historyDetail" component={HistoryDetail} />
            <Route path="/configRollback" component={ConfigRollback} />
            <Route path="/historyRollback" component={HistoryRollback} />
            <Route path="/listeningToQuery" component={ListeningToQuery} />
            <Route path="/configurationManagement" component={ConfigurationManagement} />
            <Route path="/serviceManagement" component={ServiceList} />
            <Route path="/serviceDetail" component={ServiceDetail} />
          </Switch>
        </Layout>
      </HashRouter>
    );
  }

  render() {
    const { locale } = this.props;
    return (
      <Loading
        className="nacos-loading"
        shape="flower"
        tip="loading..."
        visible={false}
        fullScreen
        {...this.state.nacosLoading}
      >
        <ConfigProvider locale={locale}>{App.generateRouter()}</ConfigProvider>
      </Loading>
    );
  }
}

ReactDOM.render(
  <Provider store={store}>
    <App />
  </Provider>,
  document.getElementById('root')
);
