/**
 * 入口页
 */
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { connect } from 'dva';
import MainLayout from '../layouts/MainLayout';
import { Message, Loading } from '@alifd/next';
import _menu from '../menu';

class App extends Component {

    constructor(props) {
        super(props);
        this.state = {
            shownotice: 'none',
            noticecontent: ''
        }
    }
    componentDidMount() {
        //监听loading事件
        window.narutoEvent.listenAllTask("narutoLoadingEvent", (narutoLoading) => {
            this.setState({
                narutoLoading
            })
        });
    }
    UNSAFE_componentWillUpdate(nextProps, nextState) {
        const { errcode, errinfo } = nextProps;
        if (errcode === 1) {
            this.openErr(errinfo);
        }
    }

    componentWillUnmount() {
        window.narutoEvent.remove("narutoLoadingEvent");
    }

    openErr(message) {
        const self = this;
        setTimeout(function () {
            self.props.dispatch({ type: 'error/clear' })
        }, 3000);
    }
    getChildContext() {
        return { history: this.props.history };
    }
    render() {
        const { errcode, errinfo } = this.props;

        return (
            <Loading className="naruto-loading" shape="flower" tip="loading..." visible={false} fullScreen {...this.state.narutoLoading}>
                <MainLayout navList={_menu.data}>
                    {errcode === 1 ? <Message title={errinfo} closable style={{ position: 'absolute', zIndex: 99999, width: 800, left: '50%', marginLeft: -400 }} /> : null}
                    {this.props.children}
                </MainLayout>
            </Loading>)
    }
}

App.propTypes = {

};
App.childContextTypes = {
    history: PropTypes.object
};
function mapStateToProps(state) {
    const { errinfo, errcode } = state.error;
    return {
        errinfo,
        errcode
    };
}
export default connect(mapStateToProps)(App);
