import React from 'react'; 
import { Button, Dialog, Form } from '@alifd/next';
const FormItem = Form.Item; 

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class ExportDialog extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            visible: false,
            serverId: '',
            tenant: '',
            dataId: '',
            group: '',
            appName: '',
            configTags: '',
            records: [],
            total: 0
        };
        this.formItemLayout = {
            labelCol: {
                fixedSpan: 4
            },
            wrapperCol: {
                span: 20
            }
        };
    }

    componentDidMount() {}

    openDialog(payload) {
        this.setState({
            visible: true,
            serverId: payload.serverId,
            tenant: payload.tenant,
            dataId: payload.dataId,
            group: payload.group,
            appName: payload.appName,
            configTags: payload.configTags,
            records: payload.records,
            total: payload.total
        });
    }

    closeDialog() {
        this.setState({
            visible: false
        });
    }

    getQuery() {
        if (this.state.records.length > 0) {
            return window.aliwareIntl.get('nacos.component.ExportDialog.|_The_selected_entry0');
        }
        if (this.state.dataId === '' && this.state.group === '' && this.state.appName === '' && this.state.configTags.length === 0) {
            return '';
        }
        let query = " |";
        if (this.state.dataId !== '') {
            query += ' DataId: ' + this.state.dataId + ',';
        }
        if (this.state.group !== '') {
            query += ' Group: ' + this.state.group + ',';
        }
        if (this.state.appName !== '') {
            query += window.aliwareIntl.get('nacos.component.ExportDialog.HOME_Application1') + this.state.appName + ',';
        }
        if (this.state.configTags.length !== 0) {
            query += window.aliwareIntl.get('nacos.component.ExportDialog.tags2') + this.state.configTags + ',';
        }
        return query.substr(0, query.length - 1);
    }

    doExport() {
        // document.getElementById('downloadLink').click();
        let url = this.getLink();
        window.open(url);
        this.closeDialog();
    }

    getLink() {
        let data = [];
        this.state.records.forEach(record => {
            data.push({ dataId: record.dataId, group: record.group });
        });
        console.log(encodeURI(JSON.stringify(data)));
        let query = `?dataId=${this.state.dataId}&group=${this.state.group}&appName=${this.state.appName}&tags=${this.state.configTags || ''}&data=${encodeURI(JSON.stringify(data))}`;
        const baseLink = `/diamond-ops/batch/export/serverId/${this.state.serverId}/tenant/${this.state.tenant.id}` + query;
        if (window.globalConfig.isParentEdas()) {
            return '/authgw/'+ window.edasprefix + baseLink;
        }
        return baseLink;
    }

    render() {
        const footer = <div>
            {/* <a id="downloadLink" style={{ display: "none" }} href={this.getLink()} /> */}
            <Button type="primary" onClick={this.doExport.bind(this)} {...{ "disabled": this.state.total <= 0 }}>{window.aliwareIntl.get('nacos.component.ExportDialog.export3')}</Button>
        </div>;

        return <div>
            <Dialog visible={this.state.visible} footer={footer} footerAlign="center" language={window.pageLanguage || 'zh-cn'} style={{ width: 480 }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} title={window.aliwareIntl.get('nacos.component.ExportDialog.export_configuration4') + this.state.serverId + "）"}>
                <Form>
                    <FormItem label={window.aliwareIntl.get('nacos.component.ExportDialog.source_space5')} {...this.formItemLayout}>
                        <p>
                            <span style={{ color: '#33cde5' }}>{this.state.tenant.name}</span>{" | " + this.state.tenant.id}
                        </p>
                    </FormItem>
                    <FormItem label={window.aliwareIntl.get('nacos.component.ExportDialog.configuration_number6')} {...this.formItemLayout}>
                        <p><span style={{ color: '#33cde5' }}>{this.state.total}</span> {this.getQuery()} </p>
                    </FormItem>
                </Form>
            </Dialog>
        </div>;
    }
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default ExportDialog;