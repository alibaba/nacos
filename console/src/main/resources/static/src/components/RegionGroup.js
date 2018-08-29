import React, { Component } from 'react'; import { Affix, Animate, Badge, Balloon, Breadcrumb, Button, Calendar, Card, Cascader, CascaderSelect, Checkbox, Collapse, ConfigProvider, DatePicker, Dialog, Dropdown, Field, Form, Grid, Icon, Input, Loading, Menu, MenuButton, Message, Nav, NumberPicker, Overlay, Pagination, Paragragh, Progress, Radio, Range, Rating, Search, Select, Slider, SplitButton, Step, Switch, Tab, Table, Tag, TimePicker, Timeline, Transfer, Tree, TreeSelect, Upload, Validate } from '@alife/next'; const Accordion = Collapse; const TabPane = Tab.Item; const FormItem = Form.Item; const { RangePicker } = DatePicker; const { Item: StepItem } = Step; const { Row, Col } = Grid; const { Node: TreeNode } = Tree; const { Item } = Nav; const { Panel } = Collapse; const { Gateway } = Overlay; const { Group: CheckboxGroup } = Checkbox; const { Group: RadioGroup } = Radio; const { Item: TimelineItem } = Timeline; const { AutoComplete: Combobox } = Select;
import ValidateDialog from './ValidateDialog' ;
import NameSpaceList from './NameSpaceList' ;

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class RegionGroup extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            instanceData: [],
            currRegionId: '',
            url: props.url || '/diamond-ops/env/domain',
            left: props.left,
            right: props.right,
            regionWidth: 700,
            hideRegionList: false
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
                fontSize: '16px'
            }
        };
        this.nameSpaceList = null;
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
        //this.setRegionWidth();
        window.postMessage({ type: 'CONSOLE_HAS_REGION' }, location)
        $(".aliyun-console-regionbar").show();
        $(window).bind("resize", this.handleResize);
        window.addEventListener("message", this.handleAliyunNav);
        this.getRegionList();
        setTimeout(() => {
            this.setRegionWidth();
            this.handleRegionListStatus();
        });
    }
    componentWillUnmount() {
        $(window).unbind('resize', this.handleResize);
        window.postMessage({ type: 'CONSOLE_HIDE_REGION' }, location)
        $(".aliyun-console-regionbar").hide();
    }
    componentWillReceiveProps(nextProps) {
        this.setState({
            url: nextProps.url,
            left: nextProps.left,
            right: nextProps.right
        });
    }
    handleAliyunNav(event) {
        const type = event.data.type;
        const payload = event.data.payload; // { fromRegionId: 'xxx', toRegionId: 'xxx'' }
        console.log(type, payload);

        switch (type) {
            case "TOPBAR_SIDEBAR_DID_MOUNT":
                // this.getRegionList();
                this.handleRegionListStatus();
                this.changeRegionBarRegionId(this.currRegionId);
                setTimeout(() => {
                    this.changeRegionBarRegionId(this.currRegionId);
                }, 1000);
                break;
            case "CONSOLE_REGION_CHANGE":
                this.changeTableData(payload.toRegionId);
                break;
            default:
                break;
        }
    }
    handleRegionListStatus() {
        const isPrivateClound = window.globalConfig && window.globalConfig.isParentEdas();
        this.setState({
            hideRegionList: isPrivateClound ? false : window.location.search.indexOf("hideTopbar=") === -1
        }, () => {
            this.setRegionWidth();
        });
    }
    handleResize() {
        clearTimeout(this.timer);
        this.timer = setTimeout(() => {
            this.setRegionWidth();
        }, 100);
    }
    setRegionWidth() {
        try {
            let mainWidth = $(this.mainRef).width();
            let titleWidth = $(this.titleRef).width();
            let extraWidth = $(this.extraRef).width();
            let regionWidth = mainWidth - extraWidth - titleWidth - 50;
            this.setState({
                regionWidth: regionWidth > 100 ? regionWidth : 100
            });
        } catch (error) { }
    }
    getRegionList() {
        if (window._regionList) {
            console.log('...');
            this.handleRegionList(window._regionList);
        } else {
            request({
                url: this.state.url,
                data: {},
                success: res => {
                    //this.loading(false);
                    if (res && res.data) {
                        window._regionList = res.data;
                        this.handleRegionList(res.data);
                    }
                }
            });
        }
    }
    handleRegionList(data) {
        let envcontent = '';
        let envGroups = data.envGroups;
        // let serverId = getParams('serverId') || '';
        let instanceData = [];
        for (let i = 0; i < envGroups.length; i++) {
            let obj = envGroups[i].envs || [];
            instanceData = obj;
            for (let j = 0; j < obj.length; j++) {
                if (obj[j].active) {
                    envcontent = obj[j].serverId;
                }
            }
        }

        this.currRegionId = envcontent || instanceData[0] && instanceData[0].serverId;
        setParam("serverId", this.currRegionId);

        this.setRegionBarRegionList(instanceData, this.currRegionId);
        this.changeRegionBarRegionId(this.currRegionId);
        setTimeout(() => {
            this.changeRegionBarRegionId(this.currRegionId);
        }, 1000);
        this.nameSpaceList && this.nameSpaceList.getNameSpaces();
        this.setState({
            currRegionId: envcontent,
            instanceData: instanceData
        });
    }
    changeTableData(serverId) {
        console.log(serverId);
        console.log(this.state.currRegionId);
        setParam("serverId", serverId);
        if (this.state.currRegionId == serverId) {
            return;
        }
        this.currRegionId = serverId;
        let instanceData = this.state.instanceData,
            inEdas = false;
        if (window.globalConfig.isParentEdas()) {
            inEdas = true;
        }

        instanceData.forEach(obj => {
            if (obj.serverId == serverId) {
                let lastHash = window.location.hash.split("?")[0];
                if (inEdas) {
                    setParam("serverId", obj.serverId);
                    // setParam('regionId', obj.serverId);
                    let url = window.location.href;

                    console.log("url: ", url);
                    window.location.href = url;
                } else {
                    let url = obj.domain + window.location.search + lastHash;
                    if (lastHash.indexOf('serverId') == -1) {
                        if (lastHash.indexOf('?') == -1) {
                            url += '?serverId=' + serverId;
                        } else {
                            url += '&serverId=' + serverId;
                        }
                    }
                    console.log("url: ", url, obj);
                    window.location.href = window.location.protocol + '//' + url;
                }

                return;
            }
        });
        //window.location.href = '';
        // return;
        // setParam("serverId", serverId);
        // this.setState({
        //     currRegionId: serverId
        // });
        // this.currRegionId = serverId;
        // this.props.onChange && this.props.onChange({
        //     instanceData: this.state.instanceData,
        //     currRegionId: serverId
        // })
    }
    setRegionBarRegionList(regionList, regionId) {
        console.log("regionList: ", regionList, regionId);
        // regionList = [{
        //     serverId: "cn-hangzhou",
        // }, {
        //     serverId: "cn-shenzhen",
        // }]
        // if (!window.viewframeSetting.regionList || window.viewframeSetting.regionList.length === 0) {
        if (window.viewframeSetting) {
            window.viewframeSetting.regionList = regionList;
            window.postMessage({ type: 'TOGGLE_REGIONBAR_STATUS', payload: { regionList: regionList, defaultRegionId: regionId } }, location);
        }
    }
    changeRegionBarRegionId(regionId) {
        window.viewframeSetting && (window.viewframeSetting.defaultRegionId = regionId);
        window.postMessage({ type: 'SET_ACTIVE_REGION_ID', payload: { defaultRegionId: regionId } }, location);
    }
    render() {

        return <div>
            <ValidateDialog />
            <div ref={ref => this.mainRef = ref} className="clearfix" style={{ marginBottom: '10px', paddingBottom: "10px", borderBottom: "1px solid #ccc" }}>
                <div style={{ overflow: "hidden" }}>
                    <div id="left" style={{ float: 'left', display: 'inline-block', marginRight: 20 }}>
                        <div ref={ref => this.titleRef = ref} style={{ display: 'inline-block', verticalAlign: 'top' }}>
                            {typeof this.state.left == 'string' ? <h5 style={this.styles.title}>{this.state.left}</h5> : this.state.left}
                        </div>
                        {this.state.hideRegionList ? null : <div ref={ref => this.regionRef = ref} style={{ width: this.state.regionWidth, display: 'inline-block', lineHeight: '40px', marginLeft: 20 }}>
                            {this.state.instanceData.map((val, key) => {
                                return <Button key={val.serverId} type={this.state.currRegionId == val.serverId ? "primary" : "normal"} style={{ fontSize: '12px', marginRight: 10, backgroundColor: this.state.currRegionId == val.serverId ? '#546478' : '#D9DEE4' }} onClick={this.changeTableData.bind(this, val.serverId)}> {val.name} </Button>;
                            })}
                        </div>}
                    </div>
                    <div ref={ref => this.extraRef = ref} style={{ float: 'right', display: 'inline-block', paddingTop: 6 }}>
                        {Object.prototype.toString.call(this.state.right) == '[object Function]' ? this.state.right() : this.state.right}
                    </div>
                </div>
                {this.props.namespaceCallBack ? <div className={'namespacewrapper'} style={{ marginTop: 5 }}><NameSpaceList ref={ref => this.nameSpaceList = ref} namespaceCallBack={this.props.namespaceCallBack} setNowNameSpace={this.props.setNowNameSpace} /></div> : null}
            </div>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default RegionGroup;