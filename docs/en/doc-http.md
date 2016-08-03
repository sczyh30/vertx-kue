In this tutorial we are going to take a quick look on implementation of `kue-http`.

# Vert.x Kue REST API

Our `kue-http` component only have one class `KueHttpVerticle`, where both REST API and UI are implemented. For REST API, the approach is quite similar to what we have elaborated in [Vert.x Blueprint - Todo-Backend Tutorial](https://github.com/sczyh30/vertx-blueprint-todo-backend/blob/master/docs/doc-en.md) so here we don't explain the code. You can refer to [Vert.x Blueprint - Todo-Backend Tutorial](https://github.com/sczyh30/vertx-blueprint-todo-backend/blob/master/docs/doc-en.md) for tutorial about **Vert.x Web**.

# Adapt Kue UI with Vert.x Web

Besides the REST API, there is also a user interface in Vert.x Kue. We reused the frontend code of Automattic/Kue's UI so here we should adapt the UI with Vert.x Web.

Frontend materials are static resources so first we need to config the static resources route using `router.route().handler(StaticHandler.create(root))`. Then we can visit static resources directly from the browser.

Notice that Kue UI uses **Jade**(Now renamed as **Pug**) as ui template so we need a Jade engine. Fortunately, Vert.x Web provides an implementation of Jade engine: `io.vertx:vertx-web-templ-jade` so we can make use of it. First we need to create a `JadeTemplateEngine` in class scope:

```java
engine = JadeTemplateEngine.create();
```

Then we write a method that renders ui for certain job state:

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

We need to specify the frontend resource path first (1). Then we get all existing job types and put `state`, `types` and `title` into context (2), which will be used when rendering pages. Next we can call `engine.render(context, path, handler)` to render the pages (3). In the handler, if successfully rendered, we send response with `end` method (4).

Now we can use this `render` handler method:

```java
private void handleUIActive(RoutingContext context) {
  render(context, "active");
}
```

And bind a route for it:

```java
router.route(KUE_UI_ACTIVE).handler(this::handleUIActive);
```

Very convenient, isn't it? Vert.x Web also supports various other kinds of template engine such as *FreeMaker*, *Pebble* and *Thymeleaf 3*. If you are interested in it, you can refer to [the documentation](http://vertx.io/docs/vertx-web/java/#_templates).

# Show time!

Can't wait to see the UI? Now let's demonstrate it! First build the project:

    gradle build

`kue-http` requires `kue-core` starting, so first deploy the `KueVerticle`. Don't forget to start Redis first:

    redis-server
    java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster -ha -conf config/config.json
    java -jar kue-http/build/libs/vertx-blueprint-kue-http.jar -cluster -ha -conf config/config.json

To watch the effects of the job processing, we can also run an example:

    java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster -ha -conf config/config.json

Then visit `http://localhost:8080` and we can inspect the queue very clearly in the browser:

![](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/vertx_kue_ui_1.png)
