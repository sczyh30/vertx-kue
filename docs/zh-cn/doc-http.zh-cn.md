在这部分教程中，我们将粗略地探索一下`kue-http`模块的实现。

# Vert.x Kue REST API

`kue-http`模块中只有一个类 `KueHttpVerticle`，作为整个REST API以及UI服务的实现。对REST API部分来说，如果看过我们之前的 [Vert.x 蓝图 | 待办事项服务开发教程](http://sczyh30.github.io/vertx-blueprint-todo-backend/cn/) 的话，你应该对这一部分非常熟悉了，因此这里我们就不详细解释了。有关使用Vert.x Web实现REST API的教程可参考 [Vert.x 蓝图 | 待办事项服务开发教程](http://sczyh30.github.io/vertx-blueprint-todo-backend/cn/)。

# 将Kue UI与Vert.x Web进行适配

除了REST API之外，我们还给Vert.x Kue提供了一个用户界面。我们复用了Automattic/Kue的用户界面所以我们就不用写前端代码了（部分API有变动的地方我已进行了修改）。我们只需要将前端代码与Vert.x Web适配即可。

首先，前端的代码都属于静态资源，因此我们需要配置路由来允许访问静态资源：

```java
router.route().handler(StaticHandler.create(root));
```

这样我们就可以直接访问静态资源咯～

注意到Kue UI使用了**Jade**（最近貌似改名叫Pug了）作为模板引擎，因此我们需要一个Jade模板解析器。好在Vert.x Web提供了一个Jade模板解析的实现: `io.vertx:vertx-web-templ-jade`，所以我们可以利用这个实现来渲染UI。首先在类中定义一个`JadeTemplateEngine`并在`start`方法中初始化：

```java
engine = JadeTemplateEngine.create();
```

然后我们就可以写一个处理器方法来根据不同的任务状态来渲染UI：

```java
private void render(RoutingContext context, String state) {
  final String uiPath = "webroot/views/job/list.jade"; // (1)
  String title = config().getString("kue.ui.title", "Vert.x Kue");
  kue.getAllTypes()
    .setHandler(resultHandler(context, r -> {
      context.put("state", state) // (2)
        .put("types", r)
        .put("title", title);
      engine.render(context, uiPath, res -> { // (3)
        if (res.succeeded()) {
          context.response()
            .putHeader("content-type", "text/html") // (4)
            .end(res.result());
        } else {
          context.fail(res.cause());
        }
      });
    }));
}
```

首先我们需要给渲染引擎指定我们前端代码的地址 (1)。然后我们从Redis中获取其中所有的任务类型，然后向解析器context中添加任务状态、网页标题、任务类型等信息供渲染器渲染使用 (2)。接着我们就可以调用`engine.render(context, path, handler)`方法进行渲染 (3)。如果渲染成功，我们将页面写入HTTP Response (4)。

现在我们可以利用`render`方法去实现其它的路由函数了：

```java
private void handleUIActive(RoutingContext context) {
  render(context, "active");
}
```

然后我们给它绑个路由就可以了：

```java
router.route(KUE_UI_ACTIVE).handler(this::handleUIActive);
```

是不是非常方便呢？不仅如此，Vert.x Web还提供了其它各种模板引擎的支持，比如 *FreeMaker*, *Pebble* 以及 *Thymeleaf 3*。如果感兴趣的话，你可以查阅[官方文档](http://vertx.io/docs/vertx-web/java/#_templates)来获取详细的使用指南。

# 展示时间！

是不是等不及要看UI长啥样了？现在我们就来展示一下！首先构建项目：

    gradle build

`kue-http`需要`kue-core`运行着（因为`kue-core`里注册了Event Bus服务），因此我们先运行`kue-core`，再运行`kue-http`。不要忘记运行Redis:

    redis-server
    java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster -ha -conf config/config.json
    java -jar kue-http/build/libs/vertx-blueprint-kue-http.jar -cluster -ha -conf config/config.json

为了更好地观察任务处理的流程，我们再运行一个示例：

    java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster -ha -conf config/config.json

好啦！现在在浏览器中访问`http://localhost:8080`，我们的Kue UI就呈现在我们眼前啦！

![](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/vertx_kue_ui_1.png)
