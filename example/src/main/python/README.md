# NacosPython
## __简介：__
> 此项目基于Python实现服务自动接入Nacos，实现服务自注册
## __目录：__

> ### NacosPython
>> - [comlib](comlib)  
     > 公共库文件
>> - [config](custom_api.py)  
     > 配置文件
>> - [custom_api.py](custom_api.py)  
     > 用户自定义api
>> - [nacos_client.py](nacos_client.py)  
     > nacos client 启动入口
>> - [config.json](config.json)  
     > nacos server config

## __Getting Started:__

### Prerequisites:
> - ####  PIP Install
>> ` pip install -r requirements.txt`  
> 
## __Usage:__
> - Config the file [config.json](config.json)   
> - Edit your own [custom_api.py](custom_api.py)  
> - Edit [nacos_client.py](nacos_client.py)  
> ```
> app.include_router(custom_api.router, tags=['test'])
> 将另一个路由对象（通常定义在其他模块或文件中）合并到主应用（app）中。
> app: 这是一个FastAPI的实例。FastAPI应用是通过实例化FastAPI类来创建的。
> include_router: 这是FastAPI类的一个方法，用于将其他路由（通常在其他模块或文件中定义）合并到当前应用中。
> custom_api.router: 这是一个路由器对象，通常在其他模块（在这里是custom_api模块）中定义。这个路由器对象包含了多个路由（即端点）定义。
> tags=['test']: 这是一个可选参数，用于给所有从这个路由器合并到主应用的路由添加一个或多个标签。标签在FastAPI的文档生成中非常有用，因为它们可以帮助组织和分类API的端点。在这个例子中，所有从custom_api.router合并到app的路由都会被标记为'test'标签。
> 总的来说，这段代码的作用是将custom_api.router中的所有路由合并到app中，并为这些路由添加一个'test'标签。这样做的好处是你可以在一个中心位置（即app）管理和组织你的API路由，同时还可以利用标签功能来更好地组织和呈现API文档。
>```
> - `python nacos_client.py`

## __Demo:__
> - Open http://127.0.0.1:12345/docs  