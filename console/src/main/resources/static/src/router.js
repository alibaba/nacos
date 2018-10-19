import React from 'react';
import {Router, Route, Switch} from 'dva/router';
import './globalLib';
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

function RouterConfig({history}) {
    window.hashHistory = history;
    return (
        <Router history={history}>
            <Switch>
                <App>
                    <Route path="/Namespace" component={Namespace}/>
                    <Route path="/Newconfig" component={Newconfig}/>
                    <Route path="/Configsync" component={Configsync}/>
                    <Route path="/Configdetail" component={Configdetail}/>
                    <Route path="/Configeditor" component={Configeditor}/>
                    <Route path="/HistoryDetail" component={HistoryDetail}/>
                    <Route path="/ConfigRollback" component={ConfigRollback}/>
                    <Route path="/HistoryRollback" component={HistoryRollback}/>
                    <Route path="/ListeningToQuery" component={ListeningToQuery}/>
                    <Route path="/ConfigurationManagement" component={ConfigurationManagement}/>
                    <Route path="/ServiceManagement" component={ServiceList}/>
                    <Route path="/ServiceDetail" component={ServiceDetail}/>
                </App>
            </Switch>
        </Router>
    );
}

export default RouterConfig;
