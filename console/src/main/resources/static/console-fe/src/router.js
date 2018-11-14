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
import PropTypes from 'prop-types';
import { Router, Route, Switch } from 'dva/router';
import './lib.js';
import App from './containers/App';
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

function RouterConfig({ history }) {
  return (
    <Router history={history}>
      <Switch>
        <App history={history}>
          <Route path="/Namespace" component={Namespace} />
          <Route path="/Newconfig" component={Newconfig} />
          <Route path="/Configsync" component={Configsync} />
          <Route path="/Configdetail" component={Configdetail} />
          <Route path="/Configeditor" component={Configeditor} />
          <Route path="/HistoryDetail" component={HistoryDetail} />
          <Route path="/ConfigRollback" component={ConfigRollback} />
          <Route path="/HistoryRollback" component={HistoryRollback} />
          <Route path="/ListeningToQuery" component={ListeningToQuery} />
          <Route path="/ConfigurationManagement" component={ConfigurationManagement} />
          <Route path="/ServiceManagement" component={ServiceList} />
          <Route path="/ServiceDetail" component={ServiceDetail} />
        </App>
      </Switch>
    </Router>
  );
}
RouterConfig.propTypes = {
  history: PropTypes.object,
};

export default RouterConfig;
