# Vert.x Kue 特性介绍

## 使用方式

我们推荐将应用编写为`Verticle`，比如：

```java
public class KueExampleVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // 在此编写逻辑
  }

}
```

## 创建任务

首先我们需要使用`Kue.createQueue(vertx, config)`方法创建一个工作队列实例`Kue`：

```java
Kue kue = Kue.createQueue(vertx, config());
```

然后我们就可以调用`kue.createJob()`方法来创建一个任务(`Job`)。我们需要指定任务的类型，并且可以给任务绑定各种数据(`JsonObject`格式)。任务创建完成以后，我们就可以调用`job.save()`方法来将此任务存储至Redis中。`save`方法是一个基于`Future`的异步方法，所以我们可以给其返回的`Future`绑定一个`Handler`，这样存储操作完成（不管是成功还是失败）以后，对应的`Handler`都会被调用。我们来看一下示例：

```java
JsonObject data = new JsonObject()
      .put("title", "Learning Vert.x")
      .put("content", "Vert.x Core");

Job job = kue.createJob("learn vertx", data);

job.save().setHandler(r0 -> {
    if (r0.succeeded()) {
        // 处理此job
    } else {
        // 处理错误
    }
});
```

### 任务优先级

我们可以通过`priority`方法给任务指定优先级，需要传递一个`Priority`类型的参数：

```java
JsonObject data = new JsonObject()
      .put("title", "Learning Vert.x")
      .put("content", "Vert.x Core");

Job job = kue.createJob("learn vertx", data)
  .priority(Priority.HIGH);
```

`Priority`是一个枚举类，里面定义了五个优先级等级：

```java
public enum Priority {
  LOW(10),
  NORMAL(0),
  MEDIUM(-5),
  HIGH(-10),
  CRITICAL(-15);
}
```

### 任务日志记录

我们可以通过`log`方法将任务的日志（如错误信息，重要信息）记录到Redis中，并且可以在UI端查看：

```java
job.log("万恶的任务处理失败了");
```

### 任务进度

任务进度对于一些长时间的任务来说很有用，比如格式转换、文件操作等。在处理任务的过程中，我们可以调用`progress`方法来改变任务的进度：

```java
job.progress(frames, totalFrames);
```

`progress`方法的原型是`Future<Job> progress(int completed, int total)`，第一个参数是已经完成的进度，第二个参数是完成时需要的进度。

### 任务事件

任务事件是非常关键的。我们通过Vert.x的Event Bus来发送和接受事件，每个事件都对应一个特定的地址。目前Vert.x Kue支持以下类型的任务事件(job events)：

- `start` 开始处理一个任务 (`onStart`)
- `promotion` 一个延期的任务时间已到，提升至工作队列中 (`onPromotion`)
- `progress` 任务的进度变化 (`onProgress`)
- `failed_attempt` 任务处理失败，但是还可以重试 (`onFailureAttempt`)
- `failed` 任务处理失败并且不能重试 (`onFailure`)
- `complete` 任务完成 (`onComplete`)
- `remove` 任务从后端存储中移除 (`onRemove`)

举个例子：

```java
JsonObject data = new JsonObject()
  .put("title", "学习 Vert.x")
  .put("content", "core");

Job j = kue.createJob("learn vertx", data)
  .onComplete(r -> { // 完成事件监听器
    System.out.println("感觉: " + r.getResult().getString("feeling"));
}).onFailure(r -> { // 失败事件监听器
    System.out.println("呵呵。。。有点难。。。");
}).onProgress(r -> { // 进度变更事件监听器
    System.out.println("吼啊！目前进度：" + r);
  });
```

### 工作队列事件

工作队列事件支持的类型与任务事件支持的类型相同，只不过需要加前缀 `job_`，比如：

```java
kue.on("job_complete", r -> {
    System.out.println("Completed!");
});
```

### 延期的任务

我们可以给一个任务设定延时，这样处理任务的时候它会被延期处理。我们可以通过`setDelay`方法来设定延时的时间，单位为毫秒(ms)。设定了延时以后，任务的状态将变更为`DELAYED`。

```java
Job email = kue.createJob("email", data)
  .setDelay(8888)
  .priority(Priority.HIGH);
```

在底层，Vert.x Kue会设置一个定时器，每隔一段时间就会检查一次延期的任务（`checkJobPromotion`方法），如果有任务到达执行时间，那么就将其提升至工作队列中等待处理。

## 处理任务

使用Vert.x Kue处理任务非常简单。我们可以调用`kue.process(jobType, n, handler)`方法来处理任务。第一个参数对应要处理任务的类型，第二个参数对应同时处理任务的数量上限，第三个参数对应处理该类型任务的逻辑，类型为`Handler<Job>`。

在下面的例子中，我们将要处理类型为`email`的任务，一次最多处理3个任务。当任务完成时，我们调用`job`的`done()`方法完成任务（发送`done`事件）；当处理遇到错误的时候，我们可以调用`done(err)`方法结束处理此任务并标记失败（发送`done_fail`事件）：

```java
kue.process("email", 3, job -> {
    if (job.getData().getString("address") == null) {
        job.done(new IllegalStateException("invalid address")); // fail
    }

    // process logic...

    job.done(); // finish
});
```

## 错误处理

所有的错误事件都会被发送至代表工作队列事件地址的**worker address**。我们可以通过`Kue`的`on`方法监听错误事件：

```java
kue.on("error", event -> {
      // process error
});
```

## 工作队列统计数据

`Kue`实例提供了两种类型的方法来查询每种状态下任务的数量，所有方法都是异步的：

```java
kue.inactiveCount(null) // 其它诸如activeCount这样的方法也类似
  .setHandler(r -> {
    if (r.succeeded()) {
      if (r.result() > 1000)
        System.out.println("It's too bad!");
    }
  });
```

当然我们也可以指定任务的类型：

```java
kue.failedCount("my-job")
  .setHandler(r -> {
    if (r.succeeded()) {
      if (r.result() > 1000)
        System.out.println("It's too bad!");
    }
  });
```

我们也可以通过`getIdsByState`方法获取某个状态下的所有任务的id，返回类型为`Future<List<Long>>`：

```java
kue.getIdsByState(JobState.ACTIVE)
  .setHandler(r -> {
    // ...
  });
```

在实际生产环境下任务数量特别多，获取所有任务可能显得不切实际。因此，我们还可以通过`range`系列的方法来获取某些特定范围内的任务，比如：

```java
kue.jobRangeByState("complete", 0, 10, "asc") // 按顺序获取10个任务
  .setHandler(r -> {
    // ...
  });
```

或者：

```java
kue.jobRangeByType("moha", "complete", 0, 10, "asc")
  .setHandler(r -> {
    // ...
  });
```

## Redis连接设置

Vert.x Kue使用了Vert.x Redis Client作为Redis通信组件，因此我们可以参考[Vert.x-redis document](http://vertx.io/docs/vertx-redis-client/java/)查看配置信息。我们推荐使用JSON格式的配置文件：

```json
{
    "redis.host": "127.0.0.1",
    "redis.port": 6379
}
```

这样当我们部署Verticle的时候，Vert.x Launcher就可以方便地读取这些配置了。

## 用户界面

Vert.x Kue的用户界面复用了[Automattic/kue](https://github.com/Automattic/kue)的用户界面，仅更改了一小部分代码。感谢Automattic以及整个开源社区！

![](../images/vertx_kue_ui_1.png)

整个用户界面和API一起相当于一个Vert.x Web应用。

## REST API

Vert.x Kue同样也提供一组REST API供UI组件和用户调用。

### GET /stats

获取当前的统计数据：

```json
{
  "workTime" : 699960,
  "inactiveCount" : 0,
  "completeCount" : 404,
  "activeCount" : 13,
  "failedCount" : 0,
  "delayedCount" : 0
}
```

### GET /job/:id

获取某个任务的详细信息：
```json
{
  "address_id" : "a245319e-341d-49f9-b6bb-371247a6a358",
  "attempts" : 0,
  "created_at" : 1466348210024,
  "data" : {
    "title" : "Account renewal required",
    "template" : "renewal-email",
    "to" : "qinxin@jianpo.xyz"
  },
  "delay" : 8888,
  "duration" : 2027,
  "failed_at" : 0,
  "id" : 403,
  "max_attempts" : 1,
  "priority" : "HIGH",
  "progress" : 100,
  "promote_at" : 1466348218912,
  "removeOnComplete" : false,
  "started_at" : 1466348219067,
  "state" : "COMPLETE",
  "type" : "email",
  "updated_at" : 1466348221099,
  "zid" : "03|403"
}
```

### GET /job/:id/log

获取指定任务的日志。

### GET /jobs/:from/to/:to/:order?

### GET /jobs/:state/:from/to/:to/:order?

### GET /jobs/:type/:state/:from/to/:to/:order?

### DELETE /job/:id

删除某个特定id的任务

### PUT /job

创建一个新任务
