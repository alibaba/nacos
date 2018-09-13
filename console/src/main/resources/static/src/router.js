import React from 'react';
import { Router, Route, Switch } from 'dva/router';
import './globalLib';
import './lib.js';
import App from './containers/App';
import Namespace from './pages/Namespace';
import Newconfig from './pages/Newconfig';
import Configsync from './pages/Configsync';
import Configdetail from './pages/Configdetail';
import Configeditor from './pages/Configeditor';
import HistoryDetail from './pages/HistoryDetail';
import ConfigRollback from './pages/ConfigRollback';
import HistoryRollback from './pages/HistoryRollback';
import ListeningToQuery from './pages/ListeningToQuery';
import ConfigurationManagement from './pages/ConfigurationManagement';

function RouterConfig({ history }) {
	window.hashHistory = history;
	return (
		<Router history={history}>
			<Switch>
				<App>
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
				</App>
			</Switch>
		</Router>
	);
}

export default RouterConfig;