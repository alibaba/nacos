# Nacos

## Code Style
Nacos code style Comply with Alibaba Java Coding Guidelines and code style file customized by Nacos community.

Nacos的编码规范遵从于《阿里巴巴JAVA开发规约》和社区制定的Nacos代码风格文件。


### Guidelines
[Alibaba-Java-Coding-Guidelines](https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/) 

[阿里巴巴JAVA开发规约](https://github.com/alibaba/p3c/blob/master/%E9%98%BF%E9%87%8C%E5%B7%B4%E5%B7%B4Java%E5%BC%80%E5%8F%91%E6%89%8B%E5%86%8C%EF%BC%88%E5%8D%8E%E5%B1%B1%E7%89%88%EF%BC%89.pdf)

### Nacos Code Style File

#### Idea IDE

Nacos Code Style file is `style/NacosStyleForIdea.xml` in source code. Developers can import it to Idea IDE and reformat code by IDE.

Nacos代码风格文件在源代码下的`style/NacosStyleForIdea.xml`文件中，开发者可以将其倒入到Idea IDE中，并让IDE帮助您格式化代码。

#### eclipse IDE

Volunteer wanted. 

待补充。

## IDE Plugin Install（not necessary）

*It is not necessary to install, if you want to find a problem when you are coding.*

*不是必须安装，如果你需要在开发的时候实时发现问题的话，你需要安装。*

### idea IDE

#### p3c
[p3c-idea-plugin-install](https://github.com/alibaba/p3c/blob/master/idea-plugin/README.md) 

[p3c插件idea IDE上安装方法](https://github.com/alibaba/p3c/blob/master/idea-plugin/README_cn.md)

#### checkstyle
[chechstyle-idea-install](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea)

1. Set checkstyle version at least 8.30 and scan scope `All resource(including tests)` in checkstyle plugin.
2. Import `style/NacosCheckStyle.xml` to checkstyle plugin.
3. Scan and check your modified code by plugin.

[chechstyle插件idea安装](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea)

1. 在checkstyle插件中设置checkstyle版本至少为8.30,并将扫描作用域设置为`All resource(including tests)`
2. 倒入源代码下`style/NacosCheckStyle.xml`文件到checkstyle插件。
3. 用checkstyle插件扫描你修改的代码。

### eclipse IDE

#### p3c

[p3c-eclipse-plugin-install](https://github.com/alibaba/p3c/blob/master/eclipse-plugin/README.md)

[p3c插件eclipse IDE上安装方法](https://github.com/alibaba/p3c/blob/master/eclipse-plugin/README_cn.md)

#### checkstyle

Volunteer wanted. 

待补充。

### Acknowledgement [Alibaba p3c](https://github.com/alibaba/p3c)
