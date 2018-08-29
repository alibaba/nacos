import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alifd/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class EnvConfigTopBar extends React.Component {
    constructor() {
        super();
        this.state = {
            envcontent: aliwareIntl.get('com.alibaba.newDiamond.component.EnvConfigTopBar.context_switching'),
            envGroups: []
        };
        this.showActiveRef = getParams('serverId') || '';
    }
    componentDidMount() {
        this.getDomains();
    }
    getDomains() {
        let self = this;
        request({
            type: 'get',
            contentType: 'application/json',
            url: `/diamond-ops/env/domain`,

            success: function (res) {

                if (res.code === 200) {
                    let data = res.data;
                    let envcontent = '';
                    let envGroups = data.envGroups;
                    let serverId = getParams('serverId');
                    for (let i = 0; i < envGroups.length; i++) {
                        let obj = envGroups[i].envs || [];
                        for (let j = 0; j < obj.length; j++) {
                            if (!serverId) {
                                if (obj[j].active) {
                                    envcontent = obj[j].name;
                                }
                            } else {
                                if (obj[j].serverId === serverId) {
                                    envcontent = obj[j].name;
                                }
                            }
                        }
                    }
                    self.setState({
                        envcontent: envcontent,
                        envGroups: envGroups
                    });
                } else {}
            },
            error: function () {}
        });
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
            let height = 23 * value.envs.length + 44;
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
                            window.envName = _value.name;
                            setParam('serverId', _value.serverId);
                        } else {
                            ddClass = 'dd';
                        }
                    } else {
                        if (_value.active) {
                            window.envName = _value.name;
                            this.showActiveRef = _value.serverId;
                            setParam('serverId', _value.serverId);
                            ddClass = `dd active`;
                        }
                    }
                    return <dd className={ddClass} id={_value.serverId} onClick={this.showEnvContent.bind(this, _value)} key={_value.serverId}><span>{_value.name}</span></dd>;
                })}

            </dl>;
        });
    }
    showEnvContent(values) {
        let content = values.name;
        let serverId = values.serverId;
        let domain = values.domain || 'https://acm.aliyun.com/';
        let self = this;
        // Dialog.confirm({
        //     title: '',
        //     content: <div>确定切换<span style={{ color: 'red' }}>{content}</span>环境？</div>,
        //     onOk: function () {
        //         if (self.showActiveRef) {
        //             document.getElementById(self.showActiveRef).className = 'dd';

        //         }
        //         let hash = window.location.hash;
        //         let path = hash.split('?');
        //         let params = '';
        //         if (path.length > 1) {
        //             if (path[1].indexOf('serverId') !== -1) {
        //                 setParam('serverId', serverId);
        //             } else {
        //                 path[1] = '?serverId=' + serverId + '&' + path[1];
        //                 window.location.hash = path.join('');
        //             }

        //         }
        //         document.getElementById(serverId).className = `dd active`;
        //         self.showActiveRef = serverId;
        //         //this.hideContainer();

        //         self.setState({
        //             envcontent: content
        //         })
        //         window.location.reload();
        //     }
        // })

        if (self.showActiveRef) {
            document.getElementById(self.showActiveRef).className = 'dd';
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
        self.showActiveRef = serverId;

        //this.hideContainer();

        self.setState({
            envcontent: content
        });
        window.location.href = window.location.protocol + '//' + domain;
    }
    showContainer() {
        document.getElementById('envcontainerex').style.display = 'block';
    }
    hideContainer() {
        document.getElementById('envcontainerex').style.display = 'none';
    }
    render() {

        return <div className={'envtop'}>
            <div className={'product-nav-icon'}>
                <p className={'current-env'} onMouseOver={this.showContainer.bind(this)}>
                    <a href="javascript:;" id="switchEnvBar">
                        <span style={{ color: 'white', fontSize: '14px', fontWeight: '400' }}>{this.state.envcontent}</span>
                        <span className="icon-arrow-down" style={{ color: '#fff' }}></span>
                    </a>
                </p>


                <div className="envcontainer-top" id={'envcontainerex'} onMouseOver={this.showContainer.bind(this)} onMouseOut={this.hideContainer.bind(this)}>
                    <div className="row" style={{ minWidth: '400px' }}>
                        <div className="col-sm-12" style={{ marginRight: '0px', 'paddingRight': '0px' }}>
                            <div className="topbar-nav-list">
                                <div className="topbar-nav-col"></div>
                                <div className="topbar-nav-item clearfix">
                                    {this.renderEnvGroups(this.state.envGroups)}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default EnvConfigTopBar;