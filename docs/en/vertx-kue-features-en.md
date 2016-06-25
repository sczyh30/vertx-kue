# Vert.x Kue Features

## Scope

We recommend the applications are wrapped in verticles.

```java
public class KueExampleVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // write your logic here
  }

}
```

## Creating Jobs

First we should create a job queue instance(`Kue`) with `Kue.createQueue(vertx, config)`:

```java
Kue kue = Kue.createQueue(vertx, config());
```

Then we could call `kue.createJob()` to create a job, with a specific job type and arbitrary job data.
After that, e could save a `Job` into Redis backend with a default priority level of "normal" using `Job#save` method.
The `save` method is a `Future` based asynchronous method so we could attach a `Handler` on it and once the save operation is successful or failed,
the logic in attached `Handler` will be called.

```java
JsonObject data = new JsonObject()
      .put("title", "Learning Vert.x")
      .put("content", "core");

Job job = kue.createJob("learn vertx", data);

job.save().setHandler(r0 -> {
    if (r0.succeeded()) {
        // process the job
    } else {
        // process failure
    }
});
```

### Job Priority

To specify the priority of a job, simply invoke the `priority` method with a `Priority` enum class:

```java
JsonObject data = new JsonObject()
      .put("title", "Learning Vert.x")
      .put("content", "core");

Job job = kue.createJob("learn vertx", data)
  .priority(Priority.HIGH);
```

There are five levels in `Priority` class:

```java
public enum Priority {
  LOW(10),
  NORMAL(0),
  MEDIUM(-5),
  HIGH(-10),
  CRITICAL(-15);
}
```

### Job Logs

Job-specific logs enable you to expose information to the UI at any point in the job's life-time. To do so we can simply invoke `job.log(str)` method, which accepts a message string:

```java
job.log("job ttl failed");
```

### Job Progress

Job progress is extremely useful for long-running jobs such as video conversion. To update the job's progress we can invoke `job.progress(completed, total)`:

```java
job.progress(frames, totalFrames);
```

The `progress` method returns `Future<Job>` so we can also set a handler on it.

### Job Events

Job-specific events are registered on the Event Bus of Vert.x. We support the following events:

- `start` the job is now running (`onStart`)
- `promotion` the job is promoted from delayed state to queued (`onPromotion`)
- `progress` the job's progress ranging from 0-100 (`onProgress`)
- `failed_attempt` the job has failed, but has remaining attempts yet (`onFailureAttempt`)
- `failed` the job has failed and has no remaining attempts (`onFailure`)
- `complete` the job has completed (`onComplete`)
- `remove` the job has been removed (`onRemove`)

Here is an example:

```java
JsonObject data = new JsonObject()
  .put("title", "Learning Vert.x")
  .put("content", "core");

Job j = kue.createJob("learn vertx", data)
  .onComplete(r -> { // on complete handler
    System.out.println("Feeling: " + r.getResult().getString("feeling", "none"));
  }).onFailure(r -> { // on failure handler
    System.out.println("eee...so difficult...");
  }).onProgress(r -> { // on progress modifying handler
    System.out.println("I love this! My progress => " + r);
  });
```

### Queue Events

With prefix `job_`:

```java
kue.on("job_complete", r -> {
    System.out.println("Completed!");
});
```

### Delayed Jobs

We can schedule to process a job with delays by calling `job.setDelay(ms)` method. The job's state will be `DELAYED`.

```java
Job email = kue.createJob("email", data)
  .setDelay(8888)
  .priority(Priority.HIGH);
```

Vert.x Kue will check the delayed jobs(`checkJobPromotion`) with a timer, promoting them if the scheduled delay has been exceeded, defaulting to a check of top 1000 jobs every second.

## Processing Jobs

It's very simple to process jobs in Vert.x Kue. We can use `kue.process(jobType, n, handler)` to process jobs concurrently. The first parameter refers to the type of job and the second parameter refers to the maximum active job count, while the third parameter refers to the handler that process the job.

In the following example we are going to process jobs in `email` type. We process 3 jobs at the same time. In the handler, we could invoke `done()` method to finish the job. If we encountered error, the job will automatically fail:

```java
kue.process("email", 3, r -> {
    Job job = r.result();
    if (job.getData().getString("address") == null) {
        throw new IllegalStateException("invalid address"); // fail
    }

    // process logic...

    job.done(); // finish
});
```

## Error Handling

Error events will send on the worker address and we could add listener on it using `Kue#on(error, handler)`:

```java
kue.on("error", event -> {
      // process error
});
```

## Queue Metrics

`Kue` object has two type of methods to tell us about the number of jobs in each state:

```java
kue.inactiveCount(null)
  .setHandler(r -> {
    if (r.succeeded()) {
      if (r.result() > 1000)
        System.out.println("It's too bad!");
    }
  });
```

It also supports query on a specific job type:

```java
kue.failedCount("my-job")
  .setHandler(r -> {
    if (r.succeeded()) {
      if (r.result() > 1000)
        System.out.println("It's too bad!");
    }
  });
```

and iterating over job ids:

```java
kue.getIdsByState(JobState.ACTIVE)
  .setHandler(r -> {
    // ...
  });
```

## Redis Connection Settings

In Vert.x Kue, we use Vert.x Redis Client as the redis component so we can refer to [Vert.x-redis document](http://vertx.io/docs/vertx-redis-client/java/). We recommend to use a json config file like this:

```json
{
    "redis.host": "127.0.0.1",
    "redis.port": 6379
}
```

Then we can pass the config file to Vert.x Launcher when we deploy our verticles.

## User-Interface

The UI of Vert.x Kue is from the original [Automattic/kue](https://github.com/Automattic/kue). Thanks to Automattic/kue and the open-source community!

## Vert.x Kue REST API

### GET /stats

```json
{
  "workTime" : 0,
  "inactiveCount" : 0,
  "completeCount" : 404,
  "activeCount" : 13,
  "failedCount" : 0,
  "delayedCount" : 0
}
```

### GET /job/:id

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

### GET /jobs/:from/to/:to/:order?

### GET /jobs/:state/:from/to/:to/:order?

### GET /jobs/:type/:state/:from/to/:to/:order?

### DELETE /job/:id

### PUT /job
