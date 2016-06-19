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

Job-specific logs enable you to expose information to the UI at any point in the job's life-time. To do so we can simply invoke `jog.log(str)` method, which accepts a message string:

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

Vert.x Kue will check the delayed jobs with a timer, promoting them if the scheduled delay has been exceeded, defaulting to a check of top 1000 jobs every second.

## Processing Jobs

```java
kue.process("email", 3, r -> {
    Job job = r.result();
    if (job.getData().getString("address") == null) {
        job.done(new IllegalStateException("invalid address")) // fail
    }

    // process logic...

    job.done(); // finish
});
```

## Error Handling

## Queue Maintenance

## Redis Connection Settings

## User-Interface

## Vert.x Kue REST API
