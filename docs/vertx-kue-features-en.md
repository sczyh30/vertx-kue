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

### Job Progress

### Job Events

### Delayed Jobs

## Processing Jobs

## Error Handling

## Queue Maintenance

## Redis Connection Settings

## User-Interface

## Vert.x Kue REST API
