# Tutorial: Vert.x Blueprint - Vert.x Kue (Core)

## Table of contents

- [Preface](#preface)
- [Message system in Vert.x](#message-system-in-vert-x)
- [Basic design of Vert.x Kue](#basic-design-of-vert-x-kue)
- [Project structure](#project-structure)
- [Job entity - not only a data object](#)
- [Here we process jobs - KueWorker](#)
- [Kue - The job queue](#)
- [Callback Kue - Polyglot support](#)
- [Show time!](#show-time)
- [Finish!](#finish)
- [From Akka?](from-akka)

## Preface

Hi, welcome back to the Vert.x Blueprint tutorial series~ In this tutorial, we are going to see a message based application - Vert.x Kue.
Vert.x Kue is a priority job queue backed by *Redis*. It's a Vert.x implementation version of [Automattic/kue](https://github.com/Automattic/kue). We can use Vert.x Kue to process various kinds of jobs, e.g. **converting files** or **processing orders**.

What you are going to learn:

- How to make use of **Vert.x Event Bus** (clustered)
- How to develop message based application with Vert.x
- Event pattern of event bus(Pub/sub, point to point)
- How to design clustered Vert.x applications
- How to design a job queue
- How to use **Vert.x Service Proxy**
- More complicated practice about Vert.x Redis

This is the second part of **Vert.x Blueprint Project**. The entire code is available on [GitHub](https://github.com/sczyh30/vertx-blueprint-job-queue/tree/master).

## Message system in Vert.x

We are going to develop a message based application, so let's have a glimpse of the message system in Vert.x. In Vert.x, we could send and receive messages on the **event bus** between the different verticles with different `Vertx` instance. So cool yeah? Messages are sent on the event bus to an **address**, which can be arbitrary string like `foo.bar.nihao`. The event bus supports **publish/subscribe**, **point to point**, and **request-response** messaging. Let's take a look.

### Publish/subscribe messaging

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

### Point to point messaging

If we replace `publish` method with `send` method, that is **point to point(send/recv)** pattern. Messages are sent to an address, Vert.x will then route it to just one of the handlers registered at the address using a non-strict round-robin algorithm. In the point to point example, the program will print only `1: Ni Hao!` or `2: Ni Hao!`.

### Request-response messaging

When a message is received by a handler, can it reply to the sender? Of course!  When we `send` a message, we can specify a reply handler.

Then when a message is received by a consumer, it can reply message to the sender, and then the reply handler on the sender will be called. That is request-response messaging.


Now we are simply aware of event bus in Vert.x, so let' step into the design of Vert.x Kue! For more details about event bus, we can refer to [Vert.x Core Manual - Event Bus](http://vertx.io/docs/vertx-core/java/#event_bus).

## Basic design of Vert.x Kue

### Components of Vert.x Kue

In our project, we divide Vert.x Kue into two components:

- `kue-core`: core component, provides job queue functionality
- `kue-http`: web component, provides UI as well as REST API

And we also provide `kue-example`, a custom example component that illustrates how Vert.x Kue works.

Since we have two components, you may wonder how can they interact with each other. And if we write our own Kue verticles, how can we consume the services of Kue Core? We'll give the answer in the following section :-)

### Vert.x Kue Core

Recall the demand of Vert.x Kue - a priority job queue backed by *Redis*, so in the core part of Vert.x Kue there will be:

- `Job` data object
- `JobService` - service that provides some operations for jobs
- `KueWorker` - verticles that processes jobs
- `Kue` - the job queue class

We need a mechanism to interact between components, and here we'll deploy verticles in **clustered mode**. The event bus will also be clustered so components could interact via the clustered event bus. Wonderful! The cluster requires a `ClusterManager` and here we use the default `HazelcastClusterManager`.

In Vert.x Kue, the `JobService` is registered and exposed on the **clustered** event bus so that other component could consume it on the event bus. We designed a `KueVerticle`, where we can register services. With the help of **Vert.x Service Proxy**, we can easily register services on event bus, then get service proxies in other components.

### Future based asynchronous pattern

In our Vert.x Kue, most of the asynchronous methods are `Future` based. In Vert.x 3.3.0, we support monadic operators for `Future`, like `map` and `compose`. These are very convenient as we can compose many futures in reactive way rather than step into the callback hell.

### Events in Vert.x Kue

As we mentioned in the [feature document](vertx-kue-features-en.md), Vert.x Kue support two kinds of events: **job events** and **queue events**. All events are sent and consumed on the clustered event bus. In Vert.x Kue we designed three kinds of address:

- `vertx.kue.handler.job.{handlerType}.{addressId}.{jobType}`: job event address for a certain job
- `vertx.kue.handler.workers.{eventType}`: queue event address
- `vertx.kue.handler.workers.{eventType}.{addressId}`: queue event address for a certain job

In the [feature document](vertx-kue-features-en.md), we've mentioned several types of events:

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

### Job state

There are five job states in Vert.x Kue:

- `INACTIVE`: the job is inactive and waiting in queue for processing
- `ACTIVE`: the processing procedure of the job is pending
- `COMPLETE`: the job has been successfully processed
- `FAILED`: the processing procedure of the job has failed
- `DELAYED`: the job is delayed to be processed and waiting for promoting to the queue

We use state machine to depict the states:

![]()

### Workflow diagram

Now we've had a rough understanding of Vert.x Kue's design, so it's time to concentrate on the code~~

## Project structure

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

Seems a bit longer than blueprint project? Let's explain that:

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

## Job entity - not only a data object

Our Vert.x Kue is responsible for processing jobs, so let's see the `Job` class first. `Job` class is in `io.vertx.blueprint.kue.queue` package. You may find it a bit long to read the code. Don't worry, we divide it into several parts and then explain.

### Job properties

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

The first field `logger` is a Vert.x Logger instance we are all familar with. But you may wonder why `Vertx` field is present here. Should `Job` class be a data object? Of course it is a data object, but **not only** a data object. In `Job` class there are numerous methods that do some operations about job such as `save` and `progress`. I imitated the design in Automattic/kue and put these logic in `Job` class. They are almost future-based asynchronous methods, so it's convenient to compose the async results like this:

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

### Job event helper methods

As we've mentioned above, we send and receive events on clustered event bus with a specific address format `vertx.kue.handler.job.{handlerType}.{addressId}.{jobType}`. So we provide two helper methods `emit` and `on` (like `EventEmitter` in Node.js) to send and consume events for current job:

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

In our following code, we'll make use of these two helper methods.

### Store format in Redis

Before we explain the logic methods, let's first illustrate the store format in Redis:

- All keys of Vert.x Kue in Redis start with `vertx_kue:`
- `vertx:kue:job:{id}`: a map stores the job entity with certain id
- `vertx:kue:ids`: the id counter, indicating the current max id
- `vertx:kue:job:types`: a list stores all job types
- `vertx:kue:{type}:jobs`: a list indicates the number of inactive jobs with certain type
- `vertx_kue:jobs`: a sorted set stores all `zid` of jobs
- `vertx_kue:job:{state}`: a sorted set stores all `zid` of jobs with certain state
- `vertx_kue:jobs:{type}:{state}`: a sorted set stores all `zid` of jobs with certain type and state
- `vertx:kue:job:{id}:log`: a list stores logs of certain id

Okay, now let's see the important logic methods in `Job` class.

### Change job state

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

### Save job

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

### Remove job

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

### Job events listener

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

### Update progress

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

### Job failure, error and attempt

When a job failed and has remaining attempt times, it can be restarted. Let's see the implementation of `failedAttempt` method:

```java
Future<Job> failedAttempt(Throwable err) {
  return this.error(err)
    .compose(Job::failed)
    .compose(Job::attemptInternal);
}
```

### Job failure and error

## Event bus service - JobService

## Here we process jobs - KueWorker

## Kue - The job queue

## CallbackKue - Polyglot support

## Show time!

## Finish!

## From Akka?
