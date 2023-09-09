# Nacos

## Code Style
Nacos code style Comply with Alibaba Java Coding Guidelines and code style file customized by Nacos community.

Nacos的编码规范遵从于《阿里巴巴JAVA开发规约》和社区制定的Nacos代码风格文件。

### Guidelines
[Alibaba-Java-Coding-Guidelines](https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/) 

[阿里巴巴JAVA开发规约](https://github.com/alibaba/p3c/blob/master/%E9%98%BF%E9%87%8C%E5%B7%B4%E5%B7%B4Java%E5%BC%80%E5%8F%91%E6%89%8B%E5%86%8C%EF%BC%88%E5%8D%8E%E5%B1%B1%E7%89%88%EF%BC%89.pdf)

[community issue](https://github.com/alibaba/nacos/issues/2992)

## Nacos Code Formatting 

Nacos uses Spotless together with google-java-format to format the Java code.
It is recommended to automatically format your code by applying the following settings:

1. Go to “Settings” → “Other Settings” → “google-java-format Settings”.
2. Tick the checkbox to enable the plugin.
3. Change the code style to “Android Open Source Project (AOSP) style”.
4. Go to “Settings” → “Tools” → “Actions on Save”.
5. Under “Formatting Actions”, select “Optimize imports” and “Reformat file”.
6. From the “All file types list” next to “Reformat code”, select Java.

You can also format the whole project via Maven by using mvn spotless:apply. 
And you could copy the `pre-commit hook` file `style/pre-commit.sh` to your `.git/hooks/` directory 
so that every time you commit your code with `git commit`, `Spotless` will automatically fix things for you.

Nacos 使用 Spotless 和 google-java-format 来格式化 Java 代码。 建议通过应用以下设置自动格式化您的代码：
1. 进入“设置”→“其他设置”→“google-java-format 设置”。
2. 勾选复选框以启用插件。
3. 将代码风格更改为“Android开源项目（AOSP）风格”。
4. 转到“设置”→“工具”→“保存操作”。
5. 在“格式化操作”下，选择“优化导入”和“重新格式化文件”。
6. 从“重新格式化代码”旁边的“所有文件类型列表”中，选择 Java。

您还可以使用 mvn Spotless:apply 通过 Maven 格式化整个项目。您可以将“pre-commit hook”文件“style/pre-commit.sh”复制到“.git/hooks/”目录
这样，每次您使用“git commit”提交代码时，“Spotless”都会自动为您修复问题。

## Nacos Code Style File

### Idea IDE

Nacos Code Style file is `style/nacos-code-style-for-idea.xml` in source code. Developers can import it to Idea IDE and reformat code by IDE.

Nacos代码风格文件在源代码下的`style/nacos-code-style-for-idea.xml`文件中，开发者可以将其导入到Idea IDE中，并让IDE帮助您格式化代码。

#### Import Way/导入方式

```
Preferences/Settings --> Editor --> Code Style --> Schema --> Import Schema --> IntelliJ IDEA code style XML
```

### eclipse IDE

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

1. `Preferences/Settings --> Other Settings --> Checkstyle` OR `Preferences/Settings --> Tools --> Checkstyle`
2. Set checkstyle version at least 8.30 and scan scope `All resource(including tests)` in checkstyle plugin.
3. Import `style/NacosCheckStyle.xml` to checkstyle plugin.
4. Scan and check your modified code by plugin.

[chechstyle插件idea安装](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea)

1. `Preferences/Settings --> Other Settings --> Checkstyle` 或者 `Preferences/Settings --> Tools --> Checkstyle`
2. 在checkstyle插件中设置checkstyle版本至少为8.30,并将扫描作用域设置为`All resource(including tests)`
3. 导入源代码下`style/NacosCheckStyle.xml`文件到checkstyle插件。
4. 用checkstyle插件扫描你修改的代码。

### eclipse IDE

#### p3c

[p3c-eclipse-plugin-install](https://github.com/alibaba/p3c/blob/master/eclipse-plugin/README.md)

[p3c插件eclipse IDE上安装方法](https://github.com/alibaba/p3c/blob/master/eclipse-plugin/README_cn.md)

#### checkstyle

Volunteer wanted. 

待补充。

### Acknowledgement [Alibaba p3c](https://github.com/alibaba/p3c)
