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
import EdasNewconfig from './pages/EdasNewconfig';
import HistoryDetail from './pages/HistoryDetail';
import ConfigRollback from './pages/ConfigRollback';
import HistoryRollback from './pages/HistoryRollback';
import EdasConfigdetail from './pages/EdasConfigdetail';
import EdasConfigeditor from './pages/EdasConfigeditor';
import ListeningToQuery from './pages/ListeningToQuery';
import ProblemOrientation from './pages/ProblemOrientation';
import ConsistencyEfficacy from './pages/ConsistencyEfficacy';
import ListAllEnvironmental from './pages/ListAllEnvironmental';
import ConfigurationManagement from './pages/ConfigurationManagement';
import EnvironmentalManagement from './pages/EnvironmentalManagement';
import EdasConfigurationManagement from './pages/EdasConfigurationManagement';

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
					<Route path="/EdasNewconfig" component={EdasNewconfig} />
					<Route path="/HistoryDetail" component={HistoryDetail} />
					<Route path="/ConfigRollback" component={ConfigRollback} />
					<Route path="/HistoryRollback" component={HistoryRollback} />
					<Route path="/EdasConfigdetail" component={EdasConfigdetail} />
					<Route path="/EdasConfigeditor" component={EdasConfigeditor} />
					<Route path="/ListeningToQuery" component={ListeningToQuery} />
					<Route path="/ProblemOrientation" component={ProblemOrientation} />
					<Route path="/ConsistencyEfficacy" component={ConsistencyEfficacy} />
					<Route path="/ListAllEnvironmental" component={ListAllEnvironmental} />
					<Route path="/ConfigurationManagement" component={ConfigurationManagement} />
					<Route path="/EnvironmentalManagement" component={EnvironmentalManagement} />
					<Route path="/EdasConfigurationManagement" component={EdasConfigurationManagement} />
				</App>
			</Switch>
		</Router>
	);
}

export default RouterConfig;