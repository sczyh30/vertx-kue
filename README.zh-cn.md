# Vert.x Kue

[![Travis Build Status](https://travis-ci.org/sczyh30/vertx-blueprint-job-queue.svg?branch=master)](https://travis-ci.org/sczyh30/vertx-blueprint-job-queue)

Vert.x Kue是Vert.x Blueprint(蓝图)系列的第二个应用。它是一个使用Vert.x开发的分布式优先级工作队列，数据存储使用的是 **Redis** 。
Vert.x Kue是 [Automattic/kue](https://github.com/Automattic/kue) 的Vert.x实现版本。

## 详细文档教程

- [Vert.x Kue Core 教程](http://sczyh30.github.io/vertx-blueprint-job-queue/cn/kue-core/index.html)
- [Vert.x Kue Web 教程](http://sczyh30.github.io/vertx-blueprint-job-queue/cn/kue-http/index.html)

## 特性

- 优先级任务
- 可延迟的任务
- 同时处理多个任务
- 任务事件以及工作队列事件
- 可选的任务重试机制以及延迟恢复机制
- RESTful API
- 简洁明了的用户界面(基于Automattic/kue UI)
- 任务进度实时展示
- 任务日志
- 基于`Future`的异步模式
- 多种语言支持
- 由 **Vert.x** 强力驱动！

特性详情请见[Vert.x Kue 特性介绍](docs/zh-cn/vertx-kue-features.zh-cn.md)。

## 构建/运行

首先构建整个项目：

    gradle build -x test

然后不要忘记启动 Redis：

    redis-server

然后我们就可以运行我们的示例应用了：

    java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster
    java -jar kue-http/build/libs/vertx-blueprint-kue-http.jar -cluster
    java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster

运行成功后，我们可以在浏览器中输入 `http://localhost:8080` 地址来访问Vert.x Kue UI并且查看工作队列的信息了。

![](docs/images/vertx_kue_ui_1.png)

# 架构

![Diagram - How Vert.x Kue works](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/kue_diagram.png)
