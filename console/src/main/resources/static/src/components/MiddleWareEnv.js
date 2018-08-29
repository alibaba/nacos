import React from 'react'; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class MiddleWareEnv extends React.Component {
    constructor() {
        super();
        this.state = {
            envcontent: aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.online_center')
        };
        this.showActiveRef = getParams('serverId') || '';
    }

    renderDomains(domains) {
        return domains.map((value, index) => {

            return <li className={value.active ? 'active' : ''} key={`domain${index}`} style={{ width: '54px', height: '32px', marginLeft: '0', textAlign: 'center', marginRight: '4px', float: 'left' }}>
                        <a href="javascript:;" target="_self" style={{ marginLeft: 0 }}>
                            {value.domainName}
                        </a>
                    </li>;
        });
    }
    renderEnvGroups(data) {

        return data.map((value, index) => {
            let height = 20 * value.envs.length + 44;
            let dtComponent = <dt style={{ color: '#666' }}>
                            <p className={['topbar-nav-item-title']}>{value.groupName}</p>
                        </dt>;

            return <dl style={{ height: height }} key={value.groupId} className={'dl'}>
                        {dtComponent}
                        {value.envs.map((_value, _index) => {
                    let ddClass = 'dd';
                    if (this.showActiveRef) {
                        if (_value.serverId === this.showActiveRef) {
                            ddClass = `dd active`;
                        } else {
                            ddClass = 'dd';
                        }
                    } else {
                        if (_value.active) {
                            this.showActiveRef = _value.serverId;
                            ddClass = `dd active`;
                        }
                    }
                    return <dd className={ddClass} id={_value.serverId} onClick={this.showEnvContent.bind(this, _value.name, _value.serverId)} key={_value.serverId}><span>{_value.name}</span></dd>;
                })}
                        
                    </dl>;
        });
    }
    showEnvContent(content, serverId) {

        if (this.showActiveRef) {
            document.getElementById(this.showActiveRef).className = 'dd';
        }
        let hash = window.location.hash;
        let path = hash.split('?');
        let params = '';
        if (path.length > 1) {
            if (path[1].indexOf('serverId') !== -1) {
                setParam('serverId', serverId);
            } else {
                path[1] = '?serverId=' + serverId + '&' + path[1];
                window.location.hash = path.join('');
            }
        }
        document.getElementById(serverId).className = `dd active`;
        this.showActiveRef = serverId;
        this.hideContainer();

        this.setState({
            envcontent: content
        });
    }
    showContainer() {
        document.getElementById('envcontainer').style.display = 'block';
    }
    hideContainer() {
        document.getElementById('envcontainer').style.display = 'none';
    }
    render() {
        //const {domains,envGroups} = this.props.data;
        let domains = [{ "domainId": "online", "domainName": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.online'), "domainUrl": "http://diamond.alibaba-inc.com/diamond-ops", "active": false }, { "domainId": "offline", "domainName": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.offline'), "domainUrl": "http://diamond.alibaba.net/diamond-ops", "active": true }];
        let envGroups = [{ "groupId": "daily-group", "groupName": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.daily_environment_packet'), "envs": [{ "serverId": "daily", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.daily'), "active": true }, { "serverId": "dailyunit", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.daily_units'), "active": false }, { "serverId": "yununit", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.cloud_unit'), "active": false }] }, { "groupId": "noGroup", "groupName": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.ungrouped'), "envs": [{ "serverId": "spas", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.Spas_dedicated'), "active": false }, { "serverId": "perf", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.performance'), "active": false }, { "serverId": "stable", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.daily_stable'), "active": false }, { "serverId": "test", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.from-test'), "active": false }, { "serverId": "global", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.international'), "active": false }, { "serverId": "paytm", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.payTM_daily'), "active": false }, { "serverId": "testyang", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.maletest'), "active": false }, { "serverId": "lazada", "unitId": null, "name": "lazada", "active": false }, { "serverId": "center", "unitId": null, "name": aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.daily_test_center'), "active": false }] }];

        return <div>
                    <div className={'product-nav-icon'}>

                        <p className={'current-env'}>
                            <strong style={{ fontWeight: 'bold', color: '#333', fontSize: '14px' }}>{aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.line')}{this.state.envcontent}
                            </strong>
                        </p>
                        <p className={'current-env'} onMouseOver={this.showContainer.bind(this)}>
                            <a href="javascript:;" id="switchEnvBar">
                                <span style={{ color: '#666', fontSize: '14px' }}>{aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.switch_environment')}</span>
                                {}
                            </a>
                        </p>
                        <div className="envcontainer" id={'envcontainer'} ref={'envcontainer'} onMouseOver={this.showContainer.bind(this)} onMouseOut={this.hideContainer.bind(this)}>
                                <div className="row">
                                    <div className="col-sm-12">
                                        <div className={'console-title'} style={{}}>
                                            <div className="pull-left">
                                                <ul className="nav nav-pills env-pills">
                                                  {this.renderDomains(domains)}
                                                </ul>
                                            </div>
                                        </div>
                                    </div>
                                </div> 
                                <div className="row" style={{ minWidth: '400px' }}>
                                    <div className="col-sm-12" style={{ marginRight: '0px', 'paddingRight': '0px' }}>
                                        <div className="topbar-nav-list">
                                            <div className="topbar-nav-col"></div>
                                            <div className="topbar-nav-item clearfix">
                                                {this.renderEnvGroups(envGroups)}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                        </div>
                    </div>
                    <div className={'product-nav-title'}>
                        {}{aliwareIntl.get('com.alibaba.newDiamond.component.MiddleWareEnv.new_diamond')}</div>
                </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default MiddleWareEnv;