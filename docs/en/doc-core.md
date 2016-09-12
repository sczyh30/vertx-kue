# Preface

Hi, welcome back to the Vert.x Blueprint tutorial series~ In this tutorial, we are going to see a message based application - Vert.x Kue.
Vert.x Kue is a priority job queue backed by *Redis*. It's a Vert.x implementation version of [Automattic/kue](https://github.com/Automattic/kue). We can use Vert.x Kue to process various kinds of jobs, e.g. **converting files** or **processing orders**.

What you are going to learn:

- How to make use of **Vert.x Event Bus** (clustered)
- How to develop message based application with Vert.x
- Event and message patterns with the event bus (Pub/sub, point to point)
- How to design clustered Vert.x applications
- How to design and implement a job queue
- How to use **Vert.x Service Proxy**
- More complicated usage of [Vert.x Redis](http://vertx.io/docs/vertx-redis-client/java/)

This is the second part of [Vert.x Blueprint Project](http://vertx.io/blog/vert-x-blueprint-tutorials/). The entire code is available on [GitHub](https://github.com/sczyh30/vertx-blueprint-job-queue/tree/master).

# Message system in Vert.x

We are going to develop a message based application, so let's have a glimpse of the message system in Vert.x. In Vert.x, we could send and receive messages on the **event bus** between the different verticles with different `Vertx` instance. So cool yeah? Messages are sent on the event bus to an **address**, which can be arbitrary string like `foo.bar.nihao`. The event bus supports **publish/subscribe**, **point to point**, and **request-response** messaging. Let's take a look.

## Publish/subscribe messaging

First is **pub/sub** pattern. Messages are published to an address, and all handlers that registered to the address (aka. subscribe) will be notified with the message. Let's see how to do this:

```java
EventBus eventBus = vertx.eventBus();

eventBus.consumer("foo.bar.baz", r -> { // subscribe to `foo.bar.baz` address
  System.out.println("1: " + r.body());
});
eventBus.consumer("foo.bar.baz", r -> { // subscribe to `foo.bar.baz` address
  System.out.println("2: " + r.body());
});

eventBus.publish("foo.bar.baz", "Ni Hao!"); // now publish the message to the address
```

We could get a reference to the event bus with `vertx.eventBus()` method. And then we could subscribe(consume) messages on certain address with a handler. Then we could publish a message to an address with `publish` method. In the example above, the program will print:

```
2: Ni Hao!
1: Ni Hao!
```

## Point to point messaging

If we replace `publish` method with `send` method, that is **point to point(send/recv)** pattern. Messages are sent to an address, Vert.x will then route it to just one of the handlers registered at the address using a non-strict round-robin algorithm. In the point to point example, the program will print only `1: Ni Hao!` or `2: Ni Hao!`.

## Request-response messaging

When a message is received by a handler, can it reply to the sender? Of course!  When we `send` a message, we can specify a reply handler.

Then when a message is received by a consumer, it can reply message to the sender, and then the reply handler on the sender will be called. That is request-response messaging.


Now we are simply aware of event bus in Vert.x, so let' step into the design of Vert.x Kue! For more details about event bus, we can refer to [Vert.x Core Manual - Event Bus](http://vertx.io/docs/vertx-core/java/#event_bus).

# Basic design of Vert.x Kue

## Components of Vert.x Kue

In our project, we divide Vert.x Kue into two components:

- `kue-core`: core component, provides job queue functionality
- `kue-http`: web component, provides UI as well as REST API

And we also provide `kue-example`, a custom example component that illustrates how Vert.x Kue works.

Since we have two components, you may wonder how can they interact with each other. And if we write our own Kue verticles, how can we consume the services of Kue Core? We'll give the answer in the following section :-)

## Vert.x Kue Core

Recall the demand of Vert.x Kue - a priority job queue backed by *Redis*, so in the core part of Vert.x Kue there will be:

- `Job` data object
- `JobService` - an asynchronous service interface that provides some operations for jobs
- `KueWorker` - verticles that processes jobs
- `Kue` - the job queue class

We need a mechanism to interact between components, and here we'll deploy verticles in **clustered mode**. The event bus will also be clustered so components could interact via the clustered event bus. Wonderful! The cluster requires a `ClusterManager` and here we use the default `HazelcastClusterManager`.

In Vert.x Kue, the `JobService` is registered and exposed on the **clustered** event bus so that other component could consume it on the event bus. We designed a `KueVerticle`, where we can register services. With the help of **Vert.x Service Proxy**, we can easily register services on event bus, then get service proxies in other components.

## Future based asynchronous pattern

In our Vert.x Kue, most of the asynchronous methods are `Future` based. In Vert.x 3.3.0, we support monadic operators for `Future`, like `map` and `compose`. These are very convenient as we can compose many futures in reactive way rather than step into the callback hell.

## Events in Vert.x Kue

As we've mentioned in the [feature document](https://github.com/sczyh30/vertx-blueprint-job-queue/blob/master/docs/en/vertx-kue-features-en.md), Vert.x Kue support two kinds of events: **job events** and **queue events**. All events are sent and consumed on the clustered event bus. In Vert.x Kue we designed three kinds of address:

- `vertx.kue.handler.job.{handlerType}.{addressId}.{jobType}`: job event address for a certain job
- `vertx.kue.handler.workers.{eventType}`: queue event address
- `vertx.kue.handler.workers.{eventType}.{addressId}`: queue event address for a certain job

In the [feature document](https://github.com/sczyh30/vertx-blueprint-job-queue/blob/master/docs/en/vertx-kue-features-en.md), we've mentioned several types of events:

- `start` the job is now running (`onStart`)
- `promotion` the job is promoted from delayed state to queued (`onPromotion`)
- `progress` the job's progress ranging from 0-100 (`onProgress`)
- `failed_attempt` the job has failed, but has remaining attempts yet (`onFailureAttempt`)
- `failed` the job has failed and has no remaining attempts (`onFailure`)
- `complete` the job has completed (`onComplete`)
- `remove` the job has been removed (`onRemove`)

Queue events are similar, but add a prefix `job_` like `job_complete`. These events are sent to send event on the event bus with `send` method (point to point messaging).
Every job has it own address so that it can receive the corresponding event.

Specially, We also have two types of internal queue events: `done` referring to a job's finish and `done_fail` referring to a job's fail.
These two events use the third address.

## Job state

There are five job states in Vert.x Kue:

- `INACTIVE`: the job is inactive and waiting in queue for processing
- `ACTIVE`: the processing procedure of the job is pending
- `COMPLETE`: the job has been successfully processed
- `FAILED`: the processing procedure of the job has failed
- `DELAYED`: the job is delayed to be processed and waiting for promoting to the queue

We use state machine to depict the states:

![Job State Machine](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/job_state_machine.png)

And here is the diagram of events between each state change:

![Events with state change](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/event_emit_state_machine.png)

## Workflow diagram

To make it clear, we use this diagram to briefly illustrate how Vert.x Kue works at high level:

![Diagram - How Vert.x Kue works](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/kue_diagram.png)

Now we've had a rough understanding of Vert.x Kue's design, so it's time to concentrate on the code~~

# Project structure

Let's start our journey with Vert.x Kue! First get the code from GitHub:

    git clone https://github.com/sczyh30/vertx-blueprint-job-queue.git

You can import the code in your IDE as a Gradle project.

As we have mentioned above, we have three components in Vert.x Kue, so we are going to define three sub-projects in Gradle.

Let's take a look at `build.gradle`:

```gradle
configure(allprojects) { project ->

  ext {
    vertxVersion = "3.3.0"
  }

  apply plugin: 'java'

  repositories {
    jcenter()
  }

  dependencies {
    compile("io.vertx:vertx-core:${vertxVersion}")
    compile("io.vertx:vertx-codegen:${vertxVersion}")
    compile("io.vertx:vertx-rx-java:${vertxVersion}")
    compile("io.vertx:vertx-hazelcast:${vertxVersion}")
    compile("io.vertx:vertx-lang-ruby:${vertxVersion}")

    testCompile("io.vertx:vertx-unit:${vertxVersion}")
    testCompile group: 'junit', name: 'junit', version: '4.12'
  }

  sourceSets {
    main {
      java {
        srcDirs += 'src/main/generated'
      }
    }
  }

  compileJava {
    targetCompatibility = 1.8
    sourceCompatibility = 1.8
  }
}

project("kue-core") {

  dependencies {
    compile("io.vertx:vertx-redis-client:${vertxVersion}")
    compile("io.vertx:vertx-service-proxy:${vertxVersion}")
  }

  jar {
    archiveName = 'vertx-blueprint-kue-core.jar'
    from { configurations.compile.collect { it.isDirectory() ? it : zipTree(it) } }
    manifest {
      attributes 'Main-Class': 'io.vertx.core.Launcher'
      attributes 'Main-Verticle': 'io.vertx.blueprint.kue.queue.KueVerticle'
    }
  }

  task annotationProcessing(type: JavaCompile, group: 'build') { // codegen
    source = sourceSets.main.java
    classpath = configurations.compile
    destinationDir = project.file('src/main/generated')
    options.compilerArgs = [
      "-proc:only",
      "-processor", "io.vertx.codegen.CodeGenProcessor",
      "-AoutputDirectory=${project.projectDir}/src/main"
    ]
  }

  compileJava {
    targetCompatibility = 1.8
    sourceCompatibility = 1.8

    dependsOn annotationProcessing
  }
}

project("kue-http") {

  dependencies {
    compile(project(":kue-core"))
    compile("io.vertx:vertx-web:${vertxVersion}")
    compile("io.vertx:vertx-web-templ-jade:${vertxVersion}")
  }

  jar {
    archiveName = 'vertx-blueprint-kue-http.jar'
    from { configurations.compile.collect { it.isDirectory() ? it : zipTree(it) } }
    manifest {
      attributes 'Main-Class': 'io.vertx.core.Launcher'
      attributes 'Main-Verticle': 'io.vertx.blueprint.kue.http.KueHttpVerticle'
    }
  }
}

project("kue-example") {

  dependencies {
    compile(project(":kue-core"))
  }

  jar {
    archiveName = 'vertx-blueprint-kue-example.jar'
    from { configurations.compile.collect { it.isDirectory() ? it : zipTree(it) } }
    manifest {
      attributes 'Main-Class': 'io.vertx.core.Launcher'
      attributes 'Main-Verticle': 'io.vertx.blueprint.kue.example.LearningVertxVerticle'
    }
  }
}

task wrapper(type: Wrapper) {
  gradleVersion = '2.12'
}
```

Seems a bit longer than the previous blueprint project? Let's explain that:

- In `configure(allprojects)`, we configure settings for all projects (global).
In global dependencies, `vertx-hazelcast` is for clustered Vert.x and `vertx-codegen` is for code generating.
- We defined three sub-projects, `kue-core`, `kue-http` and `kue-example`, using syntax `project(name) {...}`. In dependency list of `kue-core`, `vertx-redis-client` is for Redis interaction and `vertx-service-proxy` is for service proxy on event bus. In dependency list of `kue-http`, we referenced dependencies in `kue-core` project. `vertx-web` and `vertx-web-templ-jade` is for web development.
- Task `annotationProcessing` is for Vert.x Codegen. We've introduced it in todo backend tutorial.

We also need `settings.gradle` file to indicate the projects:

```gradle
rootProject.name = 'vertx-blueprint-job-queue'

include "kue-core"
include "kue-http"
include "kue-example"
```

As we've explained gradle files, let's see the directory tree of the project:

```
.
├── build.gradle
├── kue-core
│   └── src
│       ├── main
│       │   ├── java
│       │   └── resources
│       └── test
│           ├── java
│           └── resources
├── kue-example
│   └── src
│       ├── main
│       │   ├── java
│       │   └── resources
│       └── test
│           ├── java
│           └── resources
├── kue-http
│   └── src
│       ├── main
│       │   ├── java
│       │   └── resources
│       └── test
│           ├── java
│           └── resources
└── settings.gradle
```

In Gradle with multi-projects, `{projectName}/src/main/java` is the source directory of the project.
In this blog, we talk about Vert.x Kue Core so our code is all in `kue-core` directory.

We are now aware of the structrue of Vert.x Kue project, so start watching code!

# Job entity - not only a data object

Our Vert.x Kue is responsible for processing jobs, so let's see the `Job` class first. `Job` class is in `io.vertx.blueprint.kue.queue` package. You may find it a bit long to read the code. Don't worry, we divide it into several parts and then explain.

## Job properties

First we have a look of the job properties:

```java
@DataObject(generateConverter = true)
public class Job {
    // job properties

    private final String address_id;
    private long id = -1;
    private String zid;
    private String type;
    private JsonObject data;
    private Priority priority = Priority.NORMAL;
    private JobState state = JobState.INACTIVE;
    private long delay = 0;
    private int max_attempts = 1;
    private boolean removeOnComplete = false;
    private int ttl = 0;
    private JsonObject backoff;

    private int attempts = 0;
    private int progress = 0;
    private JsonObject result;

    // job metrics
    private long created_at;
    private long promote_at;
    private long updated_at;
    private long failed_at;
    private long started_at;
    private long duration;


    // ...
}
```

Wow, so many properties! Let's explain these properties:

- `address_id`: a UUID string for event bus address
- `id`: the id of a job
- `type`: the type of a job
- `data`: arbitrary data of a job, in `JsonObject` format
- `priority`: the priority of a job, in `Priority` enum type. By default `NORMAL`
- `delay`: the delay time of a job, by default `0`
- `state`: the state of a job, in `JobState` enum type. By default `INACTIVE`
- `attempts`: already attempted processing times
- `max_attempts`: max attempt times threshold
- `removeOnComplete`: a flag that indicates whether the job will be removed as soon as it is completed
- `zid`: an id for the `zset` operation to preserve FIFO order
- `ttl`: time to live
- `backoff`: retry backoff settings, in `JsonObject` format
- `progress`: the progress of a job
- `result`: the process result of a job, in `JsonObject` format

And these metrics:

- `created_at`: the timestamp when the job is created
- `promote_at`: the timestamp when the delayed job is promoted
- `updated_at`: the timestamp when the job is updated(or completed)
- `failed_at`: the timestamp when the job is failed
- `started_at`: the timestamp when the job is started
- `duration`: the duration of the processing procedure(in `ms`)

You may have noticed that there are several static fields in `Job` class:

```java
private static Logger logger = LoggerFactory.getLogger(Job.class);

private static Vertx vertx;
private static RedisClient client;
private static EventBus eventBus;

public static void setVertx(Vertx v, RedisClient redisClient) {
  vertx = v;
  client = redisClient;
  eventBus = vertx.eventBus();
}
```

The first field `logger` is a Vert.x Logger instance we are all familiar with. But you may wonder why `Vertx` field is present here. Should `Job` class be a data object? Of course it is a data object, but **not only** a data object. In `Job` class there are numerous methods that do some operations about job such as `save` and `progress`. I imitated the design in Automattic/kue and put these logic in `Job` class. They are almost future-based asynchronous methods, so it's convenient to compose the async results like this:

```java
job.save()
    .compose(Job::updateNow)
    .compose(j -> j.log("good!"));
```

Owing to the fact that we cannot get `Vertx` instance when the `Job` class is loaded by JVM, we must set a `Vertx` instance to the field in `Job` class manually. This will be done in `Kue` class. When we create a job queue, the static fields in `Job` class will be initilized. And we also need a check method. When we create a job without static fields initilized, the logger should give a warning message:

```java
private void _checkStatic() {
  if (vertx == null) {
    logger.warn("static Vertx instance in Job class is not initialized!");
  }
}
```

The `Job` class is annotated with `@DataObject` annotation. The Vert.x Codegen can generate json converter for data objects, and the service proxy also requires data object. We have four constructors in `Job` class:

```java
public Job() {
  this.address_id = UUID.randomUUID().toString();
  _checkStatic();
}

public Job(JsonObject json) {
  JobConverter.fromJson(json, this);
  this.address_id = json.getString("address_id");
  _checkStatic();
}

public Job(Job other) {
  this.id = other.id;
  this.zid = other.zid;
  this.address_id = other.address_id;
  this.type = other.type;
  this.data = other.data == null ? null : other.data.copy();
  this.priority = other.priority;
  this.state = other.state;
  this.delay = other.delay;
  // job metrics
  this.created_at = other.created_at;
  this.promote_at = other.promote_at;
  this.updated_at = other.updated_at;
  this.failed_at = other.failed_at;
  this.started_at = other.started_at;
  this.duration = other.duration;
  this.attempts = other.attempts;
  this.max_attempts = other.max_attempts;
  this.removeOnComplete = other.removeOnComplete;
  _checkStatic();
}

public Job(String type, JsonObject data) {
  this.type = type;
  this.data = data;
  this.address_id = UUID.randomUUID().toString();
  _checkStatic();
}
```

The `address_id` field must be assigned when a job is created. By default it is a UUID string. And in every constructors, we should call `_checkStatic` method to inspect whether the static fields have been assigned.

## Job event helper methods

As we've mentioned above, we send and receive job events on clustered event bus with a specific address format `vertx.kue.handler.job.{handlerType}.{addressId}.{jobType}`. So we provide two helper methods `emit` and `on` (like `EventEmitter` in Node.js) to send and consume events for current job:

```java
@Fluent
public <T> Job on(String event, Handler<Message<T>> handler) {
  logger.debug("[LOG] On: " + Kue.getCertainJobAddress(event, this));
  eventBus.consumer(Kue.getCertainJobAddress(event, this), handler);
  return this;
}

@Fluent
public Job emit(String event, Object msg) {
  logger.debug("[LOG] Emit: " + Kue.getCertainJobAddress(event, this));
  eventBus.send(Kue.getCertainJobAddress(event, this), msg);
  return this;
}
```

In our following code, we'll frequently make use of these two helper methods.

## Store format in Redis

Before we explain the logic methods, let's first illustrate the store format in Redis:

- All keys of Vert.x Kue in Redis start with `vertx_kue:` (namespace)
- `vertx:kue:job:{id}`: a map stores the job entity with certain id
- `vertx:kue:ids`: the id counter, indicating the current max id
- `vertx:kue:job:types`: a list stores all job types
- `vertx:kue:{type}:jobs`: a list indicates the number of inactive jobs with certain type
- `vertx_kue:jobs`: a sorted set stores all `zid` of jobs
- `vertx_kue:job:{state}`: a sorted set stores all `zid` of jobs with certain state
- `vertx_kue:jobs:{type}:{state}`: a sorted set stores all `zid` of jobs with certain type and state
- `vertx:kue:job:{id}:log`: a list stores logs of certain id

Okay, now let's see the important logic methods in `Job` class.

## Change job state

As we have mentioned above, there are five kinds of state in `Job` class. All job operations are accompanied by a job state change. So let's first see the `state` method:

```java
public Future<Job> state(JobState newState) {
  Future<Job> future = Future.future();
  RedisClient client = RedisHelper.client(vertx, new JsonObject()); // use a new client to keep transaction
  JobState oldState = this.state;
  client.transaction().multi(r0 -> { // (1)
    if (r0.succeeded()) {
      if (oldState != null && !oldState.equals(newState)) { // (2)
        client.transaction().zrem(RedisHelper.getStateKey(oldState), this.zid, _failure())
          .zrem(RedisHelper.getKey("jobs:" + this.type + ":" + oldState.name()), this.zid, _failure());
      }
      client.transaction().hset(RedisHelper.getKey("job:" + this.id), "state", newState.name(), _failure()) // (3)
        .zadd(RedisHelper.getKey("jobs:" + newState.name()), this.priority.getValue(), this.zid, _failure())
        .zadd(RedisHelper.getKey("jobs:" + this.type + ":" + newState.name()), this.priority.getValue(), this.zid, _failure());

      switch (newState) { // dispatch different state
        case ACTIVE: // (4)
          client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
            this.priority.getValue() < 0 ? this.priority.getValue() : -this.priority.getValue(),
            this.zid, _failure());
          break;
        case DELAYED: // (5)
          client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
            this.promote_at, this.zid, _failure());
          break;
        case INACTIVE: // (6)
          client.transaction().lpush(RedisHelper.getKey(this.type + ":jobs"), "1", _failure());
          break;
        default:
      }

      this.state = newState;

      client.transaction().exec(r -> { // (7)
        if (r.succeeded()) {
          future.complete(this);
        } else {
          future.fail(r.cause());
        }
      });
    } else {
      future.fail(r0.cause());
    }
  });

  return future.compose(Job::updateNow);
}
```

First we create a `Future`. And we create a `RedisClient` in order to keep transaction correct. Then we called `client.transaction().multi(handler)` (1). In Vert.x 3.3.0, the Redis transaction operations are moved into `RedisTransaction` class so we should call `client.transaction()` to get a `RedisTransaction`, then `multi` marks the start of a transaction block.

In the `multi` handler, if the state of current job is not empty and does not equal to the new state, we will remove the old state info in Redis (2). We created a `RedisHelper` class to give helper methods such as wrapping a key with a format:

```java
package io.vertx.blueprint.kue.util;

import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;


public final class RedisHelper {

  private static final String VERTX_KUE_REDIS_PREFIX = "vertx_kue";

  private RedisHelper() {
  }

  public static RedisClient client(Vertx vertx, JsonObject config) {
    return RedisClient.create(vertx, options(config));
  }

  public static RedisOptions options(JsonObject config) {
    return new RedisOptions()
      .setHost(config.getString("redis.host", "127.0.0.1"))
      .setPort(config.getInteger("redis.port", 6379));
  }

  public static String getKey(String key) {
    return VERTX_KUE_REDIS_PREFIX + ":" + key;
  }

  public static String getStateKey(JobState state) {
    return VERTX_KUE_REDIS_PREFIX + ":jobs:" + state.name();
  }

  public static String createFIFO(long id) {
    String idLen = "" + ("" + id).length();
    int len = 2 - idLen.length();
    while (len-- > 0)
      idLen = "0" + idLen;
    return idLen + "|" + id;
  }

  public static String stripFIFO(String zid) {
    return zid.substring(zid.indexOf('|') + 1);
  }

  public static long numStripFIFO(String zid) {
    return Long.parseLong(zid.substring(zid.indexOf('|') + 1));
  }
}
```

All keys of Vert.x Kue in Redis start with `vertx_kue:`, and we implemented a method `getKey` to wrap this. We also implemented `createFIFO` and `stripFIFO` method to generate and strip zid(the format is from Automattic/Kue).

Back to `state` method. We use `zrem(String key, String member, Handler<AsyncResult<String>> handler)` command to remove the specified members from the sorted set stored at key. Two keys are `vertx_kue:job:{state}` and `vertx_kue:jobs:{type}:{state}` and the member is the corresponding `zid`.

Then we use `hset` to modify the new state in Redis (3), and then we use `zadd` to add the zid to sorted set `vertx_kue:job:{state}` and `vertx_kue:jobs:{type}:{state}` with **a score**.
The *score* is the significant thing to implement *priority*. We use the value of the priority as score.
Then when we need to get jobs from Redis, we could use a `zpop` operation to pop the high score members. We'll mention it later.

Next we do different for different new states. For `ACTIVE` state, we `zadd` the zid to sorted set `vertx_kue:jobs:ACTIVE` with the priority score (4). For `DELAYED` state, we `zadd` the zid to sorted set `vertx_kue:jobs:DELAYED` with `promote_at` field as the score (5). For `INACTIVE` state, we use `lpush` push an element to `vertx:kue:{type}:jobs` list, which indicating a job waiting for processing (6). The operations mentioned above are all in transaction scope. And finally we execute these operations with `exec` method (7). If successful, we complete the future with current job, else fail the future with error message. Finally we return the future and compose it with method `updateNow`.

The `updateNow` method is simple: just set the `updated_at` field to current time and then save to Redis:

```java
Future<Job> updateNow() {
  this.updated_at = System.currentTimeMillis();
  return this.set("updated_at", String.valueOf(updated_at));
}
```

## Save job

Now let's take a look on an important method - `save` method:

```java
public Future<Job> save() {
  // check
  Objects.requireNonNull(this.type, "Job type cannot be null"); // (1)

  if (this.id > 0)
    return update(); // (2)

  Future<Job> future = Future.future();

  // generate id
  client.incr(RedisHelper.getKey("ids"), res -> { // (3)
    if (res.succeeded()) {
      this.id = res.result();
      this.zid = RedisHelper.createFIFO(id); // (4)
      String key = RedisHelper.getKey("job:" + this.id);
      // need subscribe
      if (this.delay > 0) {
        this.state = JobState.DELAYED;
      }

      client.sadd(RedisHelper.getKey("job:types"), this.type, _failure()); // (5)
       this.created_at = System.currentTimeMillis();
       this.promote_at = this.created_at + this.delay;
       // save job
       client.hmset(key, this.toJson(), _completer(future, this)); // (6)
    } else {
      future.fail(res.cause());
    }
  });

  return future.compose(Job::update); // (7)
}
```

First the `type` field can't be null so we need to check it (1).
Then if current id is present(`> 0`), we'll regard the job as saved job so just need to `update` it (2).
Then we create the `Future`, and use `incr` operation on `vertx_kue:ids` to get a new id (3).
We also set the new `zid` using `RedisHelper.createFIFO(id)` (4).
And if the delay is positive, set current state to `DELAYED`. Next we `sadd` the job type into `vertx:kue:job:types` list (5) and set `created_at` and `promote_at` time.
Now all properties are prepared ok so we use `hmset` to save the job into the map `vertx:kue:job:{id}` (6).
If the operation is successful, complete the future with current job, or else fail with message.
Finally we return the future and then `compose` it with `update` method.

So next see the `update` method. It's simple:

```java
Future<Job> update() {
  Future<Job> future = Future.future();
  this.updated_at = System.currentTimeMillis();

  client.transaction().multi(_failure())
    .hset(RedisHelper.getKey("job:" + this.id), "updated_at", String.valueOf(this.updated_at), _failure())
    .zadd(RedisHelper.getKey("jobs"), this.priority.getValue(), this.zid, _failure())
    .exec(_completer(future, this));

  return future.compose(r ->
    this.state(this.state));
}
```

First we create the future and set `updated_at` time with current time.
Then we start a transaction and save `updated_at` time to Redis and `zadd` the zid with priority score into `vertx_kue:jobs` sorted set. Finally return the future and compose it with `state` method.

So the procedure of saving a job is simple: `save -> update -> state`.

## Remove job

Removing a job is simple. Just use `zrem` and `del` to remove corresponding things in Redis. Let's see the implementation:

```java
public Future<Void> remove() {
  Future<Void> future = Future.future();
  client.transaction().multi(_failure())
    .zrem(RedisHelper.getKey("jobs:" + this.stateName()), this.zid, _failure())
    .zrem(RedisHelper.getKey("jobs:" + this.type + ":" + this.stateName()), this.zid, _failure())
    .zrem(RedisHelper.getKey("jobs"), this.zid, _failure())
    .del(RedisHelper.getKey("job:" + this.id + ":log"), _failure())
    .del(RedisHelper.getKey("job:" + this.id), _failure())
    .exec(r -> {
      if (r.succeeded()) {
        this.emit("remove", new JsonObject().put("id", this.id));
        future.complete();
      } else {
        future.fail(r.cause());
      }
    });
  return future;
}
```

Here we only look at the `remove` event. If the job is successfully removed, the job will send `remove` event on event bus. The sent data contains the id of removed job.

## Job events listener

We can consume job events with several `onXXX` methods:

```java
@Fluent
public Job onComplete(Handler<Job> completeHandler) {
  this.on("complete", message -> {
    completeHandler.handle(new Job((JsonObject) message.body()));
  });
  return this;
}

@Fluent
public Job onFailure(Handler<JsonObject> failureHandler) {
  this.on("failed", message -> {
    failureHandler.handle((JsonObject) message.body());
  });
  return this;
}

@Fluent
public Job onFailureAttempt(Handler<JsonObject> failureHandler) {
  this.on("failed_attempt", message -> {
    failureHandler.handle((JsonObject) message.body());
  });
  return this;
}

@Fluent
public Job onPromotion(Handler<Job> handler) {
  this.on("promotion", message -> {
    handler.handle(new Job((JsonObject) message.body()));
  });
  return this;
}

@Fluent
public Job onStart(Handler<Job> handler) {
  this.on("start", message -> {
    handler.handle(new Job((JsonObject) message.body()));
  });
  return this;
}

@Fluent
public Job onRemove(Handler<JsonObject> removeHandler) {
  this.on("start", message -> {
    removeHandler.handle((JsonObject) message.body());
  });
  return this;
}

@Fluent
public Job onProgress(Handler<Integer> progressHandler) {
  this.on("progress", message -> {
    progressHandler.handle((Integer) message.body());
  });
  return this;
}
```

Notice that the data sent in different events is different so let's explain:

- `onComplete`, `onPromotion` and `onStart`: the data is corresponding `Job`
- `onFailure` and `onFailureAttempt`: the data is a `JsonObject`. Here is the format:

```json
{
    "job": {},
    "extra": {
        "message": "some_error"
    }
}
```

- `onProgress`: the data is current complete rate
- `onRemove`: the data is a `JsonObject`, with a field `id` indicating the id of removed job

## Update progress

We can also update job progress with `progress` method. Here is its implementation:

```java
public Future<Job> progress(int complete, int total) {
  int n = Math.min(100, complete * 100 / total); // (1)
  this.emit("progress", n); // (2)
  return this.setProgress(n) // (3)
    .set("progress", String.valueOf(n))
    .compose(Job::updateNow);
}
```

The `progress` method takes two parameters, one is current value, other is total value. We first calculate the complete rate (1) and then send `progress` event to event bus (2). Finally we save `progress` to Redis, composing `updateNow` method, then return `Future` result.

## Job failure, error and attempt

When a job failed and has remaining attempt times, it can be retried with `failAttempt` method. Let's see the implementation of `failedAttempt` method:

```java
Future<Job> failedAttempt(Throwable err) {
  return this.error(err)
    .compose(Job::failed)
    .compose(Job::attemptInternal);
}
```

Wow, so short! In fact, the `failedAttempt` method is a composition of three asynchronous methods: `error`, `failed` and `attemptInternal`.
When we want to have a retry, we first emit `error` queue event and log it in Redis, then change current job status to `FAILED`, finally retry.
Let's first see `error` method:

```java
public Future<Job> error(Throwable ex) {
  // send this on worker address in order to consume it with `Kue#on` method
  return this.emitError(ex) // (1)
    .set("error", ex.getMessage()) // (2)
    .compose(j -> j.log("error | " + ex.getMessage())); // (3)
}
```

First we emit `error` event (1), then set `error` field with error message (2) and log it in Redis (3). We encapsulated a `emitError` method to help emit `error` event:

```java
@Fluent
public Job emitError(Throwable ex) {
  JsonObject errorMessage = new JsonObject().put("id", this.id)
    .put("message", ex.getMessage());
  eventBus.send(Kue.workerAddress("error"), errorMessage);
  eventBus.send(Kue.getCertainJobAddress("error", this), errorMessage);
  return this;
}
```

The error message format resembles this example:

```json
{
    "id": 2052,
    "message": "some error"
}
```

Let's then look at the `failed` method:

```java
public Future<Job> failed() {
  this.failed_at = System.currentTimeMillis();
  return this.updateNow() // (1)
    .compose(j -> j.set("failed_at", String.valueOf(j.failed_at))) // (2)
    .compose(j -> j.state(JobState.FAILED)); // (3)
}
```

It's very simple. First we update the `updated_at` field (1) and `failed_at` field (2), then set the state to `FAILED` with `state` method.

The core logic of attempting is in `attemptInternal` method:

```java
private Future<Job> attemptInternal() {
  int remaining = this.max_attempts - this.attempts; // (1)
  if (remaining > 0) { // has remaining
    return this.attemptAdd() // (2)
      .compose(Job::reattempt) // (3)
      .setHandler(r -> {
        if (r.failed()) {
          this.emitError(r.cause()); // (4)
        }
      });
  } else if (remaining == 0) { // (5)
    return Future.failedFuture("No more attempts");
  } else { // (6)
    return Future.failedFuture(new IllegalStateException("Attempts Exceeded"));
  }
}
```

In our job data object, we have `max_attempts` for max retry threshold and `attempts` for already retry times, so we calculate `remaining` times (1). If we have `remaining` times, we return a composed `Future` of `attemptAdd` and `reattempt` method. We first call `attemptAdd` to increase attempts in Redis and set new attempts to current job (2). After that we call `reattempt` method.

But before we step into the `reattempt` method, we move our focus to another stuff. We know that Vert.x Kue supports **retry backoff**, which means that we can delay the retry with a certain strategy(e.g. **fixed** or **exponential**). We have a `backoff` field in `Job` class and its format should resemble this:

```json
{
    "type": "fixed",
    "delay": 5000
}
```

The implementation of backoff support is in `getBackoffImpl` method, which returns a `Function` that takes a integer(refers to `attempts`) and returns generated delay time:

```java
private Function<Integer, Long> getBackoffImpl() {
  String type = this.backoff.getString("type", "fixed"); // (1) by default `fixed` type
  long _delay = this.backoff.getLong("delay", this.delay); // (2)
  switch (type) {
    case "exponential": // (3)
      return attempts -> Math.round(_delay * 0.5 * (Math.pow(2, attempts) - 1));
    case "fixed":
    default: // (4)
      return attempts -> _delay;
  }
}
```

First we get the backoff type. We have two types of backoff currently: `fixed` and `exponential`. The former one's delay time is fixed, while the later one is exponential. If we don't indicate the type, Vert.x Kue will use `fixed` type by  default (1). Then we get `delay` from the backoff json object and if it is not present, we'll use `delay` field of current job (2). Then for `exponential` type, we calculate `[delay * 0.5 * 2^attempts]` for the delay (3). And for `fixed` type, we simply use original `delay` (4).

Ok, now back to our `reattempt` method:

```java
private Future<Job> reattempt() {
  if (this.backoff != null) {
    long delay = this.getBackoffImpl().apply(attempts); // (1) calc delay time
    return this.setDelay(delay)
      .setPromote_at(System.currentTimeMillis() + delay)
      .update() // (2)
      .compose(Job::delayed); // (3)
  } else {
    return this.inactive(); // (4) only restart the job
  }
}
```

First we check if the `backoff` field is present. If valid, we get the generated `delay` value (1) and then set `delay` and `promote_at` time to current job. Next we `update` the job and finally set the job state to `DELAYED` with `delayed` method (3). This is another async method composition! And if the backoff is not available, we'll simply `inactive` the job, which represents prompting the job into job queue and then restarting (4).

That's the entire retry implementation, not very complicated yeah?
From the methods above we could find `Future` composition everywhere. If we write these async methods in callback-based model, we may fall into the callback hell (⊙o⊙)
That's not very concise and elegant... So by contrast, we'll find it convenient to use the **monadic** and **reactive** `Future`.


Great! We've completed our journey with `Job` class, and next let's march into the `JobService`!

# Event bus service - JobService

## Async RPC

In this section let's see `JobService` - including common logic for jobs. As this is a common service interface, in order to make every component in cluster accessible to the service, we'd like to expose it on event bus then consume it every where. This kind of interaction is known as *Remote Procedure Call*. With RPC, a component can send messages to another component by doing a local procedure call. Similarly, the result can be sent to the caller with RPC.

But traditional RPC has a drawback: the caller has to wait until the response from the callee has been received. You may want to say: this is a blocking model, which does not fit for Vert.x asynchronous model. In addition, the traditional RPC isn't really *Failure-Oriented*.

Fortunately, Vert.x provides an effective and reactive kind of RPC: asynchronous RPC. Instead of waiting for the response, we could pass a `Handler<AsyncResult<R>>` to the method and when the result is arrived, the handler will be called. That corresponds to the asynchronous model of Vert.x.

So you may wonder how to register services on event bus. Does we have to wrap and send messages to event bus, while in the other side decoding the message and then call the service? No, we needn't! Vert.x provides us a component to automatically generate service proxy - **Vert.x Service Proxy**. With the help of Vert.x Service Proxy, we can simply design our asynchronous service interface and then only need to place `@ProxyGen` annotation.

[NOTE Constraint of `@ProxyGen` | There are constraints of asynchronous methods in `@ProxyGen`. The asynchronous methods should be callback-based - that is, the methods should take a `Handler<AsyncResult<R>>` parameter. The type of R is also restricted for some certain types or data objects. Please check the [documentation](http://vertx.io/docs/vertx-service-proxy/) for details. ]

## Async service interface

Let's see the code of `JobService` interface:

```java
@ProxyGen
@VertxGen
public interface JobService {

  static JobService create(Vertx vertx, JsonObject config) {
    return new JobServiceImpl(vertx, config);
  }

  static JobService createProxy(Vertx vertx, String address) {
    return ProxyHelper.createProxy(JobService.class, vertx, address);
  }

  /**
   * Get job from backend by id
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService getJob(long id, Handler<AsyncResult<Job>> handler);

  /**
   * Remove a job by id
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService removeJob(long id, Handler<AsyncResult<Void>> handler);

  /**
   * Judge whether a job with certain id exists
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService existsJob(long id, Handler<AsyncResult<Boolean>> handler);

  /**
   * Get job log by id
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService getJobLog(long id, Handler<AsyncResult<JsonArray>> handler);

  /**
   * Get a list of job in certain state in range (from, to) with order
   *
   * @param state   expected job state
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRangeByState(String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

  /**
   * Get a list of job in certain state and type in range (from, to) with order
   *
   * @param type    expected job type
   * @param state   expected job state
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRangeByType(String type, String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

  /**
   * Get a list of job in range (from, to) with order
   *
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRange(long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

  // runtime cardinality metrics

  /**
   * Get cardinality by job type and state
   *
   * @param type    job type
   * @param state   job state
   * @param handler async result handler
   */
  @Fluent
  JobService cardByType(String type, JobState state, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality by job state
   *
   * @param state   job state
   * @param handler async result handler
   */
  @Fluent
  JobService card(JobState state, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of completed jobs
   *
   * @param type    job type; if null, then return global metrics
   * @param handler async result handler
   */
  @Fluent
  JobService completeCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of failed jobs
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService failedCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of inactive jobs
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService inactiveCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of active jobs
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService activeCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of delayed jobs
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService delayedCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get the job types present
   *
   * @param handler async result handler
   */
  @Fluent
  JobService getAllTypes(Handler<AsyncResult<List<String>>> handler);

  /**
   * Return job ids with the given `state`
   *
   * @param state   job state
   * @param handler async result handler
   */
  @Fluent
  JobService getIdsByState(JobState state, Handler<AsyncResult<List<Long>>> handler);

  /**
   * Get queue work time in milliseconds
   *
   * @param handler async result handler
   */
  @Fluent
  JobService getWorkTime(Handler<AsyncResult<Long>> handler);
}
```

In addition, we added `@VertxGen` annotation to `JobService`, which is for polyglot languages support.

In `JobService` we also defined two static methods: `create` for create a `JobService` instance; `createProxy` for create a service proxy.

As we've mentioned above, the `JobService` contains common logic for `Job`. The functionality of each method has been described in the comment so let's directly explain the implementation.

## Job service implementation

The code is long... So we don't show the code here. We just explain. You can look it up on [GitHub](https://github.com/sczyh30/vertx-blueprint-job-queue/blob/master/kue-core/src/main/java/io/vertx/blueprint/kue/service/impl/JobServiceImpl.java).

- `getJob`: This is very simple. Just use `hgetall` operation to retrieve the certain job from Redis. Once the result is retrieved, the handler will be called. If failure happens, we need to call `removeBadJob` to remove the bad job entity.
- `removeJob`: We can regard this method as a combination of `getJob` and `Job#remove`.
- `existsJob`: Use `exists` operation to judge whether a job with certain `id` exists.
- `getJobLog`: Use `lrange` operation to retrieve job log from `vertx_kue:job:{id}:log` list.
- `rangeGeneral`: This is a general method to retrieve jobs in certain range (range of a sorted set) and certain order. This method takes five parameters. The first refers to the key, the second and third refers to range `from` and `to` respectively. The forth refers to the order(`asc` or `desc`), and the fifth refers to the result handler. In this implementation, we first call `zrange` to get the `zid` list of our expected jobs. If the list size is 0, then indicates empty; Else use reactive operations to convert the list into a `List<Long>` ids. In order to show jobs in order, we need to `sort` the list by the given order. Then we create a `List<Job>` job list and traverse the `ids` using `foreach`. In each traverse we call `getJob` to get the corresponding job and add it into the job list. Finally we call the handler with the `jobList`.

[NOTE `zrange` operation | `zrange` returns the specified range of elements in the sorted set stored at key. See [ZRANGE - Redis](http://redis.io/commands/zrange) for details. ]

The following three methods make use of `rangeGeneral` method:

- `jobRangeByState`: Get a range of jobs by state. The corresponding key is `vertx_kue:jobs:{state}`.
- `jobRangeByType`: Get a range of jobs by state and type. The corresponding key is `vertx_kue:jobs:{type}:{state}`.
- `jobRange`: Get a range of jobs. The corresponding key is `vertx_kue:jobs`.

The following two basic methods are about cardinality metrics:

- `cardByType`: Use `zcard` to get job counts of a certain `type` and `state`.
- `card`: Use `zcard` to get job counts of a certain `state`.

The following five helper methods are based on the two basic methods:

- `completeCount`
- `failedCount`
- `delayedCount`
- `inactiveCount`
- `activeCount`

- `getAllTypes`: Use `smembers` to get all job types stored in `vertx_kue:job:types` set.
- `getIdsByState`: Use `zrange` to get job ids with given `state`.
- `getWorkTime`: Simply `get` current Vert.x Kue's work time from `vertx_kue:stats:work-time` field.

## Register the job service

Now that the job service implementation is complete, let's see how to register it on the event bus! We need a `KueVerticle` that creates the actual service instance, and then registers the service on the event bus.

Open the `io.vertx.blueprint.kue.queue.KueVerticle` class and we will see:

```java
package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.service.JobService;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.serviceproxy.ProxyHelper;


public class KueVerticle extends AbstractVerticle {

  private static Logger logger = LoggerFactory.getLogger(Job.class);

  public static final String EB_JOB_SERVICE_ADDRESS = "vertx.kue.service.job.internal"; // (1)

  private JsonObject config;
  private JobService jobService;

  @Override
  public void start(Future<Void> future) throws Exception {
    this.config = config();
    this.jobService = JobService.create(vertx, config); // (2)
    // create redis client
    RedisClient redisClient = RedisHelper.client(vertx, config);
    redisClient.ping(pr -> { // (3) test connection
      if (pr.succeeded()) {
        logger.info("Kue Verticle is running...");

        // (4) register job service
        ProxyHelper.registerService(JobService.class, vertx, jobService, EB_JOB_SERVICE_ADDRESS);

        future.complete();
      } else {
        logger.error("oops!", pr.cause());
        future.fail(pr.cause());
      }
    });
  }

}
```

First we need an event bus address where the service is published (1). In `start` method, we first create an actual job service instance (2). Then create a `RedisClient` instance and test connection with `ping` (3). If the connection is okay, we then register the service by using the `registerService` method in `ProxyHelper` class (4).

Then as soon as the verticle is deployed in clustered mode, the service will be publish on the event bus and we can consume the service in other components. Wonderful!

# Kue - The job queue

The `Kue` class represents a job queue. Let's see the implementation of `Kue` class. First look at its constructor:

```java
public Kue(Vertx vertx, JsonObject config) {
  this.vertx = vertx;
  this.config = config;
  this.jobService = JobService.createProxy(vertx, EB_JOB_SERVICE_ADDRESS);
  this.client = RedisHelper.client(vertx, config);
  Job.setVertx(vertx, RedisHelper.client(vertx, config)); // init static vertx instance inner job
}
```

Here we should focus on two points. One is that we use `createProxy` helper method to create a proxy for `JobService`. Another is that we need to init the static fields of `Job` class as we've mentioned above.

## Future-based encapsulation

In `Kue`, we encapsulated future-based asynchronous methods with callback-based methods of `JobService`. This is simple. For example,

```java
@Fluent
JobService getJob(long id, Handler<AsyncResult<Job>> handler);
```

can be encapsulated as:

```java
public Future<Optional<Job>> getJob(long id) {
  Future<Optional<Job>> future = Future.future();
  jobService.getJob(id, r -> {
    if (r.succeeded()) {
      future.complete(Optional.ofNullable(r.result()));
    } else {
      future.fail(r.cause());
    }
  });
  return future;
}
```

Other future-based methods are similar so we don't explain one by one. You could refer to the code.

![](https://raw.githubusercontent.com/sczyh30/vertx-blueprint-job-queue/master/docs/images/kue_future_based_methods.png)

## Process and processBlocking

Our Vert.x Kue is intended to **process jobs**, so in `Kue` we implemented some process methods:

```java
public Kue process(String type, int n, Handler<Job> handler) {
  if (n <= 0) {
    throw new IllegalStateException("The process times must be positive");
  }
  while (n-- > 0) {
    processInternal(type, handler, false);
  }f
  setupTimers();
  return this;
}

public Kue process(String type, Handler<Job> handler) {
  processInternal(type, handler, false);
  setupTimers();
  return this;
}

public Kue processBlocking(String type, int n, Handler<Job> handler) {
  if (n <= 0) {
    throw new IllegalStateException("The process times must be positive");
  }
  while (n-- > 0) {
    processInternal(type, handler, true);
  }
  setupTimers();
  return this;
}
```

The first and the second are similar - both can process jobs in **Event Loop**. In addition, we can specify the threshold of processing jobs number `n` in the first `process` method. Recall the feature of **Event Loop** - we can't block them or the events would not be handled. So if we need to do some blocking procedure when processing jobs, we could use `processBlocking` method. Their code looks similar, so what's the difference? As weve mentioned above, we designed `KueWorker` - verticles that process jobs. So for `process` method, the `KueWorker` is an ordinary verticle, while for `processBlocking` method, the `KueWorker` is a **worker verticle**. What is the difference between two kinds of verticles? That is - the worker verticle will use **worker threads** so even if we do some blocking procedure, the event loop thread would not be blocked.

The three process methods make use of `processInternal`:

```java
private void processInternal(String type, Handler<Job> handler, boolean isWorker) {
  KueWorker worker = new KueWorker(type, handler, this); // (1)
  vertx.deployVerticle(worker, new DeploymentOptions().setWorker(isWorker), r0 -> { // (2)
    if (r0.succeeded()) {
      this.on("job_complete", msg -> {
        long dur = new Job(((JsonObject) msg.body()).getJsonObject("job")).getDuration();
        client.incrby(RedisHelper.getKey("stats:work-time"), dur, r1 -> { // (3)
          if (r1.failed())
            r1.cause().printStackTrace();
        });
      });
    }
  });
}
```

First we create a `KueWorker` instance (1). We'll see the detail of `KueWorker` class later. Then we deploy the kue worker with configuration `DeploymentOptions`. The third parameter of `processInternal` indicates whether the verticle should be deployed as worker verticle and we set the value with `setWorker` (2). If succeeded deployed, we'll consume global `complete` event on the event bus. When every message arrives, we decode it as the duration of the processing. Then we increase the kue work time using `incrby` (3).

Back to the public process methods. Besides deploying kue workers, we also call `setupTimers` method. That means setting up timers for checking job promotion as well as checking active job ttl.

## Check job promotion

Our Vert.x Kue supports delayed jobs, so we need to promote job to the job queue as soon as the delay timeout. Let's see the `checkJobPromotion` method:

```java
private void checkJobPromotion() {
  int timeout = config.getInteger("job.promotion.interval", 1000); // (1)
  int limit = config.getInteger("job.promotion.limit", 1000); // (2)
  vertx.setPeriodic(timeout, l -> { // (3)
    client.zrangebyscore(RedisHelper.getKey("jobs:DELAYED"), String.valueOf(0), String.valueOf(System.currentTimeMillis()),
      new RangeLimitOptions(new JsonObject().put("offset", 0).put("count", limit)), r -> {  // (4)
        if (r.succeeded()) {
          r.result().forEach(r1 -> {
            long id = Long.parseLong(RedisHelper.stripFIFO((String) r1));
            this.getJob(id).compose(jr -> jr.get().inactive())  // (5)
              .setHandler(jr -> {
                if (jr.succeeded()) {
                  jr.result().emit("promotion", jr.result().getId()); // (6)
                } else {
                  jr.cause().printStackTrace();
                }
              });
          });
        } else {
          r.cause().printStackTrace();
        }
      });
  });
}
```

First we get `timeout` and `limit` from the config. The `timeout` refers to the interval between each check (1), while the `limit` attribute refers to the max job promotion count threshold (2). Then we use `vertx.setPeriodic` to set a periodic timer to fire every `timeout` (3) and when every timeout arrives, we retrieve jobs that need promoting from Redis (4). We do this with `zrangebyscore` operation, which returns all the elements in the sorted set at key with a score between min and max (including elements with score equal to min or max). The elements are considered to be ordered from low to high scores. Let's see the signature of `zrangebyscore`:

```java
RedisClient zrangebyscore(String key, String min, String max, RangeLimitOptions options, Handler<AsyncResult<JsonArray>> handler);
```

- `key`: the key of a sorted set. In this usage, it is `vertx_kue:jobs:DELAYED`.
- `min` and `max`: Pattern defining a minimum value and a maximum value. Here the `min` is **0** and the `max` is current timestamp.

Recall the `state` method in `Job` class, when the new state is `DELAYED`, we'll set the corresponding score to the `promote_at` time:

```java
case DELAYED:
  client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
    this.promote_at, this.zid, _failure());
```

So as we specify the `max` as `System.currentTimeMillis()`, once is greater than `promote_at`, that means that the job can be promoted.

- `options`: range and limit options. In this usage, we need to specify the `LIMIT` so we created a `new RangeLimitOptions(new JsonObject().put("offset", 0).put("count", limit)`

The result of `zrangebyscore` is an `JsonArray` including zids of each wait-to-promote job. Next we traverse each zid , convert them to `id`, and then get the corresponding job, and finally call `inactive` method to set job state to `INACTIVE` (5). If the promotion is successful, we emit `promotion` event to the job-specific address (6).

# Here we process jobs - KueWorker

We've explored numerous parts of Vert.x Kue Core and here we'll explore another most important part - `KueWorker`, where we process jobs. We've briefly mentioned it in the previous chapter and we know it's a vertice.

Every worker is binded with a specific `type` and `jobHandler` so we need to specify them when creating new workers.

## Prepare and start

In `KueWorker`, we use `prepareAndStart()` to prepare job and start processing procedure:

```java
private void prepareAndStart() {
  this.getJobFromBackend().setHandler(jr -> { // (1)
    if (jr.succeeded()) {
      if (jr.result().isPresent()) {
        this.job = jr.result().get(); // (2)
        process(); // (3)
      } else {
        this.emitJobEvent("error", null, new JsonObject().put("message", "job_not_exist"));
        throw new IllegalStateException("job not exist");
      }
    } else {
        this.emitJobEvent("error", null, new JsonObject().put("message", jr.cause().getMessage()));
        jr.cause().printStackTrace();
    }
  });
}
```

The logic is obvious. First we get a job from Redis backend using `getJobFromBackend` method (1). If we successfully get the job, we set the `job` field to the job got (2) and then `process` (3). If we are faced with failure, we should emit `error` job event with the failure message.

## Get appropriate job with zpop command

Let's step to `getJobFromBackend` method to see how we fetch a job by priority from Redis backend:

```java
private Future<Optional<Job>> getJobFromBackend() {
  Future<Optional<Job>> future = Future.future();
  client.blpop(RedisHelper.getKey(this.type + ":jobs"), 0, r1 -> { // (1)
    if (r1.failed()) {
      client.lpush(RedisHelper.getKey(this.type + ":jobs"), "1", r2 -> {
        if (r2.failed())
          future.fail(r2.cause());
      });
    } else {
      this.zpop(RedisHelper.getKey("jobs:" + this.type + ":INACTIVE")) // (2)
        .compose(kue::getJob) // (3)
        .setHandler(r -> {
          if (r.succeeded()) {
            future.complete(r.result());
          } else
            future.fail(r.cause());
        });
    }
  });
  return future;
}
```

From our previoud design, we know as soon as we save a common job, we'll `lpush` a value to the `vertx_kue:{type}:jobs` to indicate a new job available. The `blpop` Redis command can remove and get the first element in a list, or block until one is available, so we make use of it to wait until a job available (1). As soon as jobs are available, we use a `zpop` command to retrieve the appropriate job zid (high priority first). The `zpop` command pops the element with the lower score from a sorted set in an atomic way. It isn't implemented in Redis, so we need to solve it by ourselves.

The documention of Redis introduced [a simple way](http://redis.io/topics/transactions#using-a-hrefcommandswatchwatcha-to-implement-zpop) to implement `ZPOP`: using `WATCH`. But in Vert.x Kue, we use a different approach to implement `zpop`:

```java
private Future<Long> zpop(String key) {
  Future<Long> future = Future.future();
  client.transaction()
    .multi(_failure())
    .zrange(key, 0, 0, _failure())
    .zremrangebyrank(key, 0, 0, _failure())
    .exec(r -> {
      if (r.succeeded()) {
        JsonArray res = r.result();
        if (res.getJsonArray(0).size() == 0) // empty set
          future.fail(new IllegalStateException("Empty zpop set"));
        else {
          try {
            future.complete(Long.parseLong(RedisHelper.stripFIFO(
              res.getJsonArray(0).getString(0))));
          } catch (Exception ex) {
            future.fail(ex);
          }
        }
      } else {
        future.fail(r.cause());
      }
    });
  return future;
}
```

In our own `zpop` implementation, we first start a transaction and then call `zrange` and `zremrangebyrank` commands. You can refer to [the documention of Redis](http://redis.io/commands) for details. Then we execute all previous commands in the transaction scope. If success, we'll get a `JsonArray` result. If there are no problems, our expected job zid could be retrieved using `res.getJsonArray(0).getString(0)`. After that we convert it to job `id` and parse it to `long` type.

Once the `zpop` operation is okay, we get zid of an expected job waiting to be processed. So next step is getting the job entity (3). We put the result into a `Future` and then return.

## The true "process"

Well, now get back to `prepareAndStart` method. We've already got a job, then the next step is our task - `process` the job. Let's explore this important method:

```java
private void process() {
  long curTime = System.currentTimeMillis();
  this.job.setStarted_at(curTime)
    .set("started_at", String.valueOf(curTime)) // (1) set start time
    .compose(Job::active) // (2) set the job state to ACTIVE
    .setHandler(r -> {
      if (r.succeeded()) {
        Job j = r.result();
        // emit start event
        this.emitJobEvent("start", j, null);  // (3) emit job `start` event
        // (4) process logic invocation
        try {
          jobHandler.handle(j);
        } catch (Exception ex) {
          j.done(ex);
        }
        // (5) consume the job done event

        eventBus.consumer(Kue.workerAddress("done", j), msg -> {
          createDoneCallback(j).handle(Future.succeededFuture(
            ((JsonObject) msg.body()).getJsonObject("result")));
        });
        eventBus.consumer(Kue.workerAddress("done_fail", j), msg -> {
          createDoneCallback(j).handle(Future.failedFuture(
            (String) msg.body()));
        });
      } else {
          this.emitJobEvent("error", this.job, new JsonObject().put("message", r.cause().getMessage()));
          r.cause().printStackTrace();
      }
    });
}
```

The most significant method! First we set the start time (1) and then change the job state to `ACTIVE` (2). If these two actions are both successful, we emit job `start` event to event bus (3). And then we invoke the true "process" handler `jobHandler` (4). If there were failure thrown, we would call `job.done(ex)`, which will send `done_fail` internal event to notify the worker that the job has failed. But where do we consume the `done` and `done_fail` event? That's here (5)! Once we receive these two events, we'll call corresponding handler to complete or fail the job. The handler is generated by `createDoneCallback` method:

```java
private Handler<AsyncResult<JsonObject>> createDoneCallback(Job job) {
  return r0 -> {
    if (job == null) {
      return;
    }
    if (r0.failed()) {
      this.fail(r0.cause()); // (1)
      return;
    }
    long dur = System.currentTimeMillis() - job.getStarted_at();
    job.setDuration(dur)
      .set("duration", String.valueOf(dur)); // (2)
    JsonObject result = r0.result();
    if (result != null) {
      job.setResult(result)
        .set("result", result.encodePrettily()); // (3)
    }

    job.complete().setHandler(r -> { // (4)
      if (r.succeeded()) {
        Job j = r.result();
        if (j.isRemoveOnComplete()) { // (5)
          j.remove();
        }
        this.emitJobEvent("complete", j, null); // (6)

        this.prepareAndStart(); // (7) prepare for next job
      }
    });
  };
}
```

Now that there are two situations: complete and fail, let's first see complete. We first set the processing duration (2) and if our complete job contains result, set the result (3). Next we can call `job.complete` to set the state to `COMPLETE` (4). If success, we'll check whether `removeOnComplete` field (5). If true, we'll `remove` the job. Then we emit job `complete` event (and global `job_complete` event) to event bus (6). Now the worker should prepare for next job so we call `this.prepareAndStart()` in the end (7).

## What if the job fails?

If the job failed, we call `fail` method in `KueWorker`:

```java
private void fail(Throwable ex) {
  job.failedAttempt(ex).setHandler(r -> { // (1)
    if (r.failed()) {
      this.error(r.cause(), job); // (2)
    } else {
      Job res = r.result();
      if (res.hasAttempts()) { // (3)
        this.emitJobEvent("failed_attempt", job, new JsonObject().put("message", ex.getMessage()));
      } else {
        this.emitJobEvent("failed", job, new JsonObject().put("message", ex.getMessage())); // (4)
      }
      prepareAndStart(); // (5)
    }
  });
}
```

When facing failure, we first try to recover from failure using `failedAttempt` method (1). If attempt fails(e.g. no more attempt chance), we send `error` queue event (2). If success, we should inspect whether there are remaining retry chance, then send corresponding events(`failed` or `failed_attempt`). Similarly, the worker should prepare for next job so we call `this.prepareAndStart()` in the end (5).

So that's all of the `KueWorker`. Very interesing and cool, isn't it? You may wonder: when can we actually run our application? Be patient, we'll show it very soon~~

# CallbackKue - Polyglot support

A bit closer to success! But as Vert.x supports polyglot languages, we could develop another `Kue` interface that supports Vert.x Codegen to automatically generate polyglot code. As we've mentioned above, due to the constraints of Vert.x Codegen, we need to use callback-based asynchronous model, so let's name it `CallbackKue`. For example, as logic methods in `Job` class can't be generated, we need to implement equalivant methods such as `saveJob`. Its original signature:

```java
public Future<Job> save();
```

We could convert to the equalivant callback-based signature:

```java
@Fluent
CallbackKue saveJob(Job job, Handler<AsyncResult<Job>> handler);
```

Other methods are similar so we don't elaborate here. You can visit the code on [GitHub](https://github.com/sczyh30/vertx-blueprint-job-queue/blob/master/kue-core/src/main/java/io/vertx/blueprint/kue/CallbackKue.java).

The `CallbackKue` interface should be annotated with `@VertxGen` annotation so that Vert.x Codegen could process the interface and then generate polyglot code:

```java
@VertxGen
public interface CallbackKue extends JobService {

  static CallbackKue createKue(Vertx vertx, JsonObject config) {
    return new CallbackKueImpl(vertx, config);
  }

  // ...

}
```

The polyglot generation also requires corresponding dependency. For example, if we want Ruby version, we should add `compile("io.vertx:vertx-lang-ruby:${vertxVersion}")` to `build.gradle`.

After correct configuration we could run `kue-core:annotationProcessing` task to generate the code. The generated Rx code will be in `generated` directory, while JS and Ruby code will be generated in `resources` directory. Then we can use Vert.x Kue in other languages! Amazing!

As for implementation of `CallbackKue`, that's very simple as we could reuse `Kue` and `JobService`. You can visit the code on [GitHub](https://github.com/sczyh30/vertx-blueprint-job-queue/blob/master/kue-core/src/main/java/io/vertx/blueprint/kue/CallbackKueImpl.java).

# Show time!

The entire core component of Vert.x Kue is completed! Now it's time to write an application then run! We create a `LearningVertxVerticle` class in `io.vertx.blueprint.kue.example` package (`kue-example` project) and write:

```java
package io.vertx.blueprint.kue.example;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.Priority;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;


public class LearningVertxVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // create our job queue
    Kue kue = Kue.createQueue(vertx, config());

    // consume queue error
    kue.on("error", message ->
      System.out.println("[Global Error] " + message.body()));

    JsonObject data = new JsonObject()
      .put("title", "Learning Vert.x")
      .put("content", "core");

    // we are going to learn Vert.x, so create a job!
    Job j = kue.createJob("learn vertx", data)
      .priority(Priority.HIGH)
      .onComplete(r -> { // on complete handler
        System.out.println("Feeling: " + r.getResult().getString("feeling", "none"));
      }).onFailure(r -> { // on failure handler
        System.out.println("eee...so difficult...");
      }).onProgress(r -> { // on progress modifying handler
        System.out.println("I love this! My progress => " + r);
      });

    // save the job
    j.save().setHandler(r0 -> {
      if (r0.succeeded()) {
        // start learning!
        kue.processBlocking("learn vertx", 1, job -> {
          job.progress(10, 100);
          // aha...spend 3 seconds to learn!
          vertx.setTimer(3000, r1 -> {
            job.setResult(new JsonObject().put("feeling", "amazing and wonderful!")) // set a result to the job
              .done(); // finish learning!
          });
        });
      } else {
        System.err.println("Wow, something happened: " + r0.cause().getMessage());
      }
    });
  }

}
```

In common, a Vert.x Kue application contains several parts: create job queue, create and save jobs, process the job. We recommand developers write code in an verticle.

In this example, we are going to simulate a task of learning Vert.x (so cool)! First we create a job queue with `Kue.createQueue` method and listen to queue `error` events with `on(error, handler)`. Then we create the learning task with `kue.createJob` and set priority to `HIGH`. Simultaneously we listen to job `complete`, `failed` and `progress` events. Next we save the job and if successful, we start learning Vert.x! As it may cost some time, we use `processBlocking` to do stuff that may spend long time. In the process handler, we first set progress to `10` with `job.progress`. Then we use `vertx.setTimer` to set a timeout with setting results and then completing.

Then we need to set the `Main-Verticle` attribute in `kue-example` subproject to `io.vertx.blueprint.kue.example.LearningVertxVerticle`.

Ok, it's time to show! Open the terminal and build the project:

    gradle build

Don't forget to run Redis:

    redis-server

Then we first run the Vert.x Kue Core Verticle:

    java -jar kue-core/build/libs/vertx-blueprint-kue-core.jar -cluster -ha -conf config/config.json

Finally run our example:

    java -jar kue-example/build/libs/vertx-blueprint-kue-example.jar -cluster -ha -conf config/config.json

Then in terminal you can see the output:

```
INFO: Kue Verticle is running...
I love this! My progress => 10
Feeling: amazing and wonderful!
```

# Finish!

Great! We have finished our journey with **Vert.x Kue Core**! In this long tutorial, you have learned how to develop a message based application with Vert.x. So cool!

To learn the implementation of `kue-http`, please visit [Tutorial: Vert.x Blueprint - Vert.x Kue (Web)](http://sczyh30.github.io/vertx-blueprint-job-queue/kue-http/index.html). To learn more about Vert.x Kue features, please refer to [Vert.x Kue features documentation](https://github.com/sczyh30/vertx-blueprint-job-queue/blob/master/docs/en/vertx-kue-features-en.md).

Vert.x can do various kinds of stuff. To learn more about Vert.x, you can visit [Vert.x Documentation](http://vertx.io/docs/) - this is always the most comprehensive material :-)
