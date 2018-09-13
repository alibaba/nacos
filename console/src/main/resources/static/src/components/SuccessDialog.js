import React from 'react'; 
import { Button, Dialog, Grid, Icon } from '@alifd/next';
const { Row, Col } = Grid; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class SuccessDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            visible: false,
            title: window.aliwareIntl.get('com.alibaba.nacos.component.SuccessDialog.Configuration_management'),
            maintitle: '',
            content: '',
            isok: true,
            dataId: '',
            group: ''

        };
    }

    componentDidMount() {}
    openDialog(payload) {
        if(this.props.unpushtrace) {
            payload.title = '';
        }
        this.setState({
            visible: true,
            maintitle: payload.maintitle,
            title: payload.title,
            content: payload.content,
            isok: payload.isok,
            dataId: payload.dataId,
            group: payload.group,
            message: payload.message
        });
    }
    closeDialog() {
        this.setState({
            visible: false
        });
    }
    render() {
        const footer = <div style={{ textAlign: 'right' }}><Button type="primary" onClick={this.closeDialog.bind(this)}>{window.aliwareIntl.get('com.alibaba.nacos.component.SuccessDialog.determine')}</Button></div>;
        return <div>
            <Dialog visible={this.state.visible} footer={footer} style={{ width: 555 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={this.state.maintitle || this.state.title} language={window.aliwareIntl.currentLanguageCode}>
                <div>
                    <Row>
                        <Col span={'4'} style={{ paddingTop: 16 }}>
                            {this.state.isok ? <Icon type="success-filling" style={{ color: 'green' }} size={'xl'} /> : <Icon type="delete-filling" style={{ color: 'red' }} size={'xl'} />}
                           
                        </Col>
                        <Col span={'20'}>
                            <div>
                                {this.state.isok ? <h3>{this.state.title}</h3> : <h3>{this.state.title} {window.aliwareIntl.get('com.alibaba.nacos.component.SuccessDialog.failure')}</h3>}
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>Data ID:</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {this.state.dataId}
                                    </span>
                                </p>
                                <p>
                                    <span style={{ color: '#999', marginRight: 5 }}>Group:</span>
                                    <span style={{ color: '#c7254e' }}>
                                        {this.state.group}
                                    </span>
                                </p>
                                {this.state.isok ? '' : <p style={{ color: 'red' }}>{this.state.message}</p>}
                            </div>
                        </Col>
                    </Row>

                </div>
            </Dialog>

        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default SuccessDialog;