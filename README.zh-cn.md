# Vert.x Kue

Vert.x Kue是Vert.x Blueprint(蓝图)系列的第二个应用。它是一个使用Vert.x开发的优先级工作队列，数据存储使用的是 *Redis*。Vert.x Kue是[Automattic/kue](https://github.com/Automattic/kue)的Vert.x实现版本。

## 详细文档教程

- [Vert.x Kue Core 开发教程](docs/zh-cn/doc-core.zh-cn.md)
- [Vert.x Kue Web 开发教程](docs/zh-cn/doc-http.zh-cn.md)

## 特性

特性详情请见[Vert.x Kue 特性介绍](docs/zh-cn/vertx-kue-features.zh-cn.md)

## 构建/运行

首先构建整个项目：

    gradle build

然后不要忘记启动Redis：

    redis-server

然后我们就可以运行我们的示例应用了：

    java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster -ha -conf config/config.json
    java -jar kue-http/build/libs/vertx-blueprint-kue-http.jar -cluster -ha -conf config/config.json
    java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster -ha -conf config/config.json

运行成功后，我们可以在浏览器中输入 `http://localhost:8080` 地址来访问Vert.x Kue UI并且监视工作队列了。

![](docs/images/vertx_kue_ui_1.png)
