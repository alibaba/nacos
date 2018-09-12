import React from 'react';
import { Icon } from '@alifd/next';
import siteConfig from '../config';
import Header from './Header';
import $ from 'jquery';

export default class extends React.Component {
    constructor(props) {
        super(props);
        this.initNav = this.props.navList;
        this.deepNav = [];
        this.oneLevelNavArr = {}; //平行导航map
        this.state = {
            leftBarClose: false,
            showLink: null,
            navRow: [],
            noChild: false
        };
    }

    goBack() {

        window.hashHistory.goBack();
    }
    simpleMVCToggleNav(id, event) {
        event.preventDefault();
        let nowNav = document.getElementById(id);
        let iconClass = nowNav.querySelector('.iconshow');
        let subNav = nowNav.querySelector('.subnavlist');
        let classList = iconClass.classList;
        let tmpClassName = 'iconshow ';
        for (let i = 0; i < classList.length; i++) {
            if (classList[i] === 'icon-arrow-down') {
                subNav.style.display = 'none';
                subNav.className += ' hidden';
                tmpClassName += 'icon-arrow-right';
            }
            if (classList[i] === 'icon-arrow-right') {
                tmpClassName += 'icon-arrow-down';
                subNav.className = subNav.className.replace(/hidden/g, '');
                subNav.style.display = 'block';
            }
        }
        iconClass.className = tmpClassName;
    }
    simpleMVCGoBack(url) {

        let params = window.location.hash.split('?')[1];
        let urlArr = params.split('&') || [];
        let queryParams = [];
        for (let i = 0; i < urlArr.length; i++) {
            if (urlArr[i].split('=')[0] !== '_k' && urlArr[i].split('=')[0] !== 'dataId' && urlArr[i].split('=')[0] !== 'group') {
                if (urlArr[i].split('=')[0] === 'searchDataId') {
                    queryParams.push(`dataId=${urlArr[i].split('=')[1]}`)
                } else if (urlArr[i].split('=')[0] === 'searchGroup') {
                    queryParams.push(`group=${urlArr[i].split('=')[1]}`)
                } else {
                    queryParams.push(urlArr[i])
                }
            }
        }
        window.hashHistory.push(`/${url}?${queryParams.join('&')}`);
    }
    simpleMVCEnterBack() {
        document.getElementById('backarrow').style.color = '#09c';
    }
    simpleMVCOutBack() {
        document.getElementById('backarrow').style.color = '#546478';
    }
    simpleMVCToggleLeftBar() {
        if (!this.state.leftBarClose) {
            //关闭
            this.simpleMVCOutDom.className = 'viewFramework-product';
            this.simpleMVCLeftBarDom.style.width = 0;
            this.simpleMVCBodyDom.style.left = 0;
            this.simpleMVCToggleIconDom.style.left = 0;
        } else {
            this.simpleMVCOutDom.className = 'viewFramework-product viewFramework-product-col-1';
            this.simpleMVCLeftBarDom.style.width = '180px';
            this.simpleMVCBodyDom.style.left = '180px';
            this.simpleMVCToggleIconDom.style.left = '160px';
        }

        this.setState({
            leftBarClose: !this.state.leftBarClose
        });
    }
    navTo(url) {
        if (url !== '/configdetail' && url !== '/configeditor') {
            //二级菜单不清空
            window.setParams({
                dataId: '',
                group: ''
            });
        }

        let params = window.location.hash.split('?')[1];
        let urlArr = params.split('&') || [];
        let queryParams = [];
        for (let i = 0; i < urlArr.length; i++) {
            if (urlArr[i].split('=')[0] !== '_k') {
                queryParams.push(urlArr[i]);
            }
        }

        window.hashHistory.push(`${url}?${queryParams.join('&')}`);
    }
    simpleMVCSetSpecialNav(item) {
        item.children.forEach(_item => {
            let obj = _item;

            if (obj.dontUseChild === true) {
                obj.parentName = item.title;
                obj.parentId = item.id;
                obj.parentPath = '/' + item.id;
                this.deepNav.push(obj);
            }
            if (_item.children) {
                this.simpleMVCSetSpecialNav(_item);
            }
        });
    }
    simpleMVCNavAct(serviceName, match, location) {

        if (!match) {
            let formatpath = location.pathname.substr(1); //得到当前路径
            let nowpathobj = this.oneLevelNavArr[formatpath]; //根据平行导航匹配父类
            if (nowpathobj) {
                if (nowpathobj.parent === serviceName) {
                    //如果父类等于当前的导航则高亮
                    return true;
                }
            }

            return false;
        }
        return true;
    }
    simpleMVCLoopNavDeeply(data, parent) {
        //深度遍历获取所有的导航数据
        let self = this;
        data.forEach(item => {
            if (item) {
                let navObj = {};
                navObj.name = item.name;
                navObj.id = item.id;
                navObj.serviceName = item.serviceName;
                navObj.parent = parent;
                navObj.dontUseChild = item.dontUseChild;
                navObj.children = item.children || [];
                self.oneLevelNavArr[item.serviceName] = navObj;
                let newparent = parent ? parent : item.serviceName;
                if (item.children && item.children.length > 0) {
                    self.simpleMVCLoopNavDeeply(item.children, newparent);
                }
            }
        });
    }
    activeNav(id) {
        if (this.preActNav) {
            this.preActNav.removeClass('active');
        }
        let nowNav = $(`#${id}`);
        nowNav.addClass('active');
        this.preActNav = nowNav;
    }
    simpleMVCLoopNav(data, index = 0, parent) {
        //遍历导航，只显示2级
        let self = this;
        return data.map(item => {
            if (!item) {
                return '';
            }
            index++;
            if (item.dontUseChild === true) {
                return '';
            }
            if (item.children && item.children.length > 0) {

                if (item.isVirtual) {
                    //如果是虚拟菜单需要增加展开箭头
                    let icon = item.isExtend ? <span className="icon-arrow-down iconshow"></span> : <span className="icon-arrow-right iconshow"></span>;
                    let hiddenClass = item.isExtend ? '' : 'hidden';
                    return <li key={`${item.serviceName}`}
                        data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
                        id={`${item.serviceName}`}>
                        <div>
                            <a href="" onClick={this.simpleMVCToggleNav.bind(this, `nav${index}`)}>
                                <div className="nav-icon">
                                    {icon}
                                </div>
                                <div className="nav-title">{window.aliwareIntl.get(item.id) || item.name}</div>
                            </a>
                        </div>
                        <ul className={`subnavlist ${hiddenClass}`}>
                            {self.simpleMVCLoopNav(item.children, index)}
                        </ul>
                    </li>;
                } else {
                    if (item.link && item.link.indexOf('http') !== -1) {
                        return <li key={`nav${index}`}
                            data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}>
                            <a href="{item.link}" >
                                <div className="nav-icon">

                                </div>
                                <div className="nav-title">{window.aliwareIntl.get(item.id) || item.name}</div>
                            </a>
                        </li>;
                    }

                    return <li key={`${item.serviceName}`}
                        data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
                        onClick={this.navTo.bind(this, `/${item.serviceName}`)}>
                        <a href={`javascript:;`} id={`${item.serviceName}`} onClick={this.activeNav.bind(this, `nav${index}`)}>
                            <div className="nav-icon"></div>
                            <div className="nav-title">{window.aliwareIntl.get(item.id) || item.name}</div>
                        </a>
                    </li>;
                }
            }

            //                if (item.serviceName === 'namespace') {
            //                    const help = <Balloon trigger={<span>{window.aliwareIntl.get(item.id) || item.name} <Icon type="help" size={'small'} style={{ color: '#1DC11D', marginRight: 5, verticalAlign: 'middle', marginLeft: 5 }} /></span>} align="tr" style={{ marginRight: 5 }} triggerType="hover">
            //                        <a style={{ fontSize: 12 }} href={window._getLink && window._getLink("knowNamespace") || ''} target="_blank">{window.aliwareIntl.get('com.alibaba.nacos.layout.noenv.Click_to_learn_the_namespace')}</a>
            //                    </Balloon>;
            //                    return <li key={`${item.serviceName}`}
            //                        data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
            //                        onClick={this.navTo.bind(this, `/${item.serviceName}`)}>
            //                        <a href={`javascript:;`} id={`${item.serviceName}`} onClick={this.activeNav.bind(this, `nav${index}`)}>
            //                            <div className="nav-icon"></div>
            //                            <div className="nav-title">{help}</div>
            //                        </a>
            //                    </li>;
            //                }
            return <li key={`${item.serviceName}`}
                data-spm-click={`gostr=/aliyun;locaid=${item.serviceName}`}
                onClick={this.navTo.bind(this, `/${item.serviceName}`)}>
                <a href={`javascript:;`} id={`${item.serviceName}`} onClick={this.activeNav.bind(this, `nav${index}`)}>
                    <div className="nav-icon"></div>
                    <div className="nav-title">{window.aliwareIntl.get(item.id) || item.name}</div>
                </a>
            </li>;
        });
    }
    simpleMVCGetNav(navList) {
        let navRow = ''; //导航
        if (navList.length > 0) {
            navRow = <ul>{this.simpleMVCLoopNav(navList)}</ul>;
            this.simpleMVCLoopNavDeeply(navList); //深度遍历导航树获得平行map
        }
        return navRow;
    }
    UNSAFE_componentWillMount() {
        //抓取edas 数据
        //和现有的导航数据整合
        //渲染出导航
        let nav = this.props.navList || [];
        let navRow = this.simpleMVCGetNav(nav);
        this.setState({
            navRow: navRow
        });

    }
    componentDidMount() {
        this.simpleMVCLeftBarDom = document.getElementById('viewFramework-product-navbar');
        this.simpleMVCBodyDom = document.getElementById('viewFramework-product-body');
        this.simpleMVCToggleIconDom = document.getElementById('viewFramework-product-navbar-collapse');
        this.simpleMVCOutDom = document.getElementById('page-header-mask');
        let parentNav = this.initNav[0] || [];
        let defaultNav = '/configurationManagement';
        let self = this;
        let childrenNav = parentNav.children || [];
        window.hashHistory.listen((location, action) => {
            if (this.preSimplePath && this.preSimplePath !== '/') {
                if (location.pathname.indexOf(this.preSimplePath) !== -1) {
                    return;
                }
            }
            //console.log(location.pathname,'fff');
            let simplePath = window.location.hash.split('?')[0];
            //let simplePath = location.pathname.split('?')[0];

            let navName = simplePath.substr('2');

            this.preSimplePath = simplePath;

            if (navName === '') {
                // let firstNav = defaultNav + window.location.hash;
                window.hashHistory.push(defaultNav);
                setTimeout(() => {
                    this.activeNav('configurationManagement');
                });
                return;
            }

            let nowNavObj = self.oneLevelNavArr[navName];
            if (!nowNavObj) {
                //如果路径不存在直接显示
                self.setState({
                    noChild: true
                });
                return;
            }
            self.setState({
                noChild: !!nowNavObj.dontUseChild
            });
            let parentId = nowNavObj.parent;
            if (simplePath !== '/' && nowNavObj && parentId) {
                childrenNav = JSON.parse(JSON.stringify(self.oneLevelNavArr[parentId].children));
                if (nowNavObj.serviceName === 'newconfig') {
                    childrenNav.forEach(value => {
                        if (value.serviceName !== 'newconfig') {
                            value.dontUseChild = true;
                        } else {
                            value.dontUseChild = false;
                        }
                    });
                }
                self.setState({
                    showLink: <div>
                        <Icon type="back" onClick={this.simpleMVCGoBack.bind(self, parentId)} id={'backarrow'} onMouseOver={self.simpleMVCEnterBack.bind(self)} onMouseLeave={self.simpleMVCOutBack.bind(self)} style={{ marginLeft: 77, marginTop: 0, fontWeight: 'bold', cursor: 'pointer', color: '#546478', fontSize: '20px' }} />
                    </div>,

                    navRow: <ul>{this.simpleMVCLoopNav([nowNavObj])}</ul>
                });
                setTimeout(() => {
                    let navid = navName;
                    this.activeNav(navid);
                });
            } else {

                self.setState({
                    showLink: null,
                    navRow: <ul>{this.simpleMVCLoopNav(this.initNav)}</ul>
                });
                setTimeout(() => {
                    let navid = navName;
                    this.activeNav(navid);
                });
            }
        });
    }

    onLanguageChange = (language) => {
        window.aliwareIntl.changeLanguage(language);
        document.cookie = `docsite_language=${language}`;
        window.location.reload();
    }

    render() {
        // const hashSearch = window.location.hash.split('?');
        let language = window.aliwareGetCookieByKeyName('docsite_language') || siteConfig.defaultLanguage;

        const { headerType } = this.state;
        const headerLogo = 'https://img.alicdn.com/tfs/TB118jPv_mWBKNjSZFBXXXxUFXa-2000-390.svg';
        return <div className="viewFramework-product" style={{ top: 66 }}>
            <Header type={headerType}
                logo={headerLogo}
                language={language}
                onLanguageChange={this.onLanguageChange} />
            <div className="viewFramework-product-navbar"
                style={{ width: 180, marginLeft: 0 }}
                id="viewFramework-product-navbar"
                data-spm="acm_nav">
                <div className="viewFramework-product-navbar-removed">
                    <div>
                        <div className="product-nav-scene product-nav-main-scene">
                            {this.state.showLink ? <div className="product-nav-icon env" style={{ height: 80, paddingTop: 25 }}>
                                {this.state.showLink}
                            </div> : <div style={{ textIndent: 0 }} className={'product-nav-title'} title={window.aliwareIntl.get('com.alibaba.nacos.layout.noenv.app_configuration_management_acm')}>{window.aliwareIntl.get('com.alibaba.nacos.layout.noenv.app_configuration_management_acm')}</div>}

                            <div className="product-nav-list" style={{ position: 'relative', top: 0, height: '100%' }}>
                                {this.state.navRow}
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div className="viewFramework-product-navbar-collapse"
                id="viewFramework-product-navbar-collapse"
                onClick={this.simpleMVCToggleLeftBar.bind(this)}>
                <div className="product-navbar-collapse-inner">
                    <div className="product-navbar-collapse-bg"></div>
                    {/* <div className="product-navbar-collapse" aliyun-console-spm="4"  > */}
                    <div className="product-navbar-collapse">
                        {this.state.leftBarClose ? <span className="icon-collapse-right" style={{ display: 'block' }}></span> : <span className="icon-collapse-left"></span>}
                    </div>
                </div>
            </div>
            <div className="viewFramework-product-body" style={{ marginLeft: 180 }} id="viewFramework-product-body">

                <div>
                    {!this.state.noChild ? <div>
                        {this.props.children}
                    </div> : <div style={{ height: 300, lineHeight: '300px', textAlign: 'center', fontSize: '18px' }}>{window.aliwareIntl.get('com.alibaba.nacos.layout.noenv.does_not_exist')}</div>}
                </div>
            </div>
        </div>;
    }

}