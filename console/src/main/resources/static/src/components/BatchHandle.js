import React from 'react'; 
import { Dialog, Pagination, Transfer } from '@alifd/next';

/*****************************此行为标记行, 请勿删和修改此行, 文件和组件依赖请写在此行上面, 主体代码请写在此行下面的class中*****************************/
class BatchHandle extends React.Component {
	constructor(props) {
		super(props);
		this.state = {
			visible: false,
			valueList: props.valueList || [],
			dataSourceList: props.dataSource || [],
			currentPage: 1,
			total: 0,
			pageSize: 10,
			dataSource: {}
		};
	}
	componentDidMount() {}
	openDialog(dataSource) {
		this.setState({
			visible: true,
			dataSource: dataSource,
			pageSize: dataSource.pageSize
		}, () => {
			this.getData();
			console.log(this.transfer._instance.filterCheckedValue);
			this.transfer._instance.filterCheckedValue = function (left, right, dataSource) {
				var result = {
					left: left,
					right: right
				};

				console.log(left, right, dataSource);
				// if (left.length || right.length) {
				// 	var value = dataSource.map(function (item) {
				// 		return item.value;
				// 	});
				// 	value.forEach(function (itemValue) {
				// 		if (left.indexOf(itemValue) > -1) {
				// 			result.left.push(itemValue);
				// 		} else if (right.indexOf(itemValue) > -1) {
				// 			result.right.push(itemValue);
				// 		}
				// 	});
				// }

				return result;
			};
		});
	}
	closeDialog() {
		this.setState({
			visible: false
		});
	}
	getData() {
		const dataSource = this.state.dataSource;
		window.request({
			url: `/diamond-ops/configList/serverId/${dataSource.serverId}?dataId=${dataSource.dataId}&group=${dataSource.group}&appName=${dataSource.appName}&config_tags=${dataSource.config_tags || ''}&pageNo=${this.state.currentPage}&pageSize=${dataSource.pageSize}`,
			success: res => {
				if (res.code === 200) {
					this.setState({
						dataSourceList: res.data.map(obj => {
							return {
								label: obj.dataId,
								value: obj.dataId
							};
						}) || [],
						total: res.total
					});
				}
			}
		});
	}
	changePage(currentPage) {
		this.setState({
			currentPage
		}, () => {
			this.getData();
		});
	}
	onChange(valueList, data, extra) {
		this.setState({
			valueList
		});
	}
	onSubmit() {
		this.props.onSubmit && this.props.onSubmit(this.state.valueList);
	}
	render() {
		// console.log("valueList: ", this.state.valueList, this.transfer);

		return <Dialog visible={this.state.visible} language={window.pageLanguage || 'zh-cn'} style={{ width: "500px" }} onCancel={this.closeDialog.bind(this)} onClose={this.closeDialog.bind(this)} onOk={this.onSubmit.bind(this)} title={"批量操作"}>
		<div>
			<Transfer ref={ref => this.transfer = ref} listStyle={{ height: 350 }} dataSource={this.state.dataSourceList || []} value={this.state.valueList} onChange={this.onChange.bind(this)} language={window.pageLanguage || 'zh-cn'} />
			{/* <div>
      <Table  dataSource={this.state.dataSourceList} language={window.aliwareIntl.currentLanguageCode}></Table>
      </div> */}
			<Pagination style={{ marginTop: 10 }} current={this.state.currentPage} language={window.pageLanguage || 'zh-cn'} total={this.state.total} pageSize={this.state.pageSize} onChange={this.changePage.bind(this)} type="simple" />
		</div>
	</Dialog>;
	}
}
/*****************************此行为标记行, 请勿删和修改此行, 主体代码请写在此行上面的class中, 组件导出语句及其他信息请写在此行下面*****************************/
export default BatchHandle;