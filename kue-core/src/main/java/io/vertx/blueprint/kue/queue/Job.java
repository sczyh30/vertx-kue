package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Vert.x Blueprint - Job Queue
 * Job Class
 *
 * @author Eric Zhao
 */
@DataObject(generateConverter = true)
public class Job {

  private static Vertx vertx;
  private static RedisClient client;
  private static EventBus eventBus;

  public static void setVertx(Vertx v, RedisClient redisClient) {
    vertx = v;
    client = redisClient;
    eventBus = vertx.eventBus();
  }

  private long id = -1;
  private String type;
  private JsonObject data;
  private Priority priority = Priority.NORMAL;
  private int delay = 0;
  private JobState state = JobState.INACTIVE;
  private int attempts = 0;
  private int maxAttempts = 1;

  private String zid;
  private int progress = 0;
  private JsonObject result;
  private JobMetrics jobMetrics = new JobMetrics();

  public Job() {
  }

  public Job(JsonObject json) {
    JobConverter.fromJson(json, this);
  }

  public Job(Job other) {
    this.type = other.type;
    this.data = other.data.copy();
    this.priority = other.priority;
    this.state = other.state;
  }

  public Job(String type, JsonObject data) {
    this.type = type;
    this.data = data;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    JobConverter.toJson(this, json);
    return json;
  }

  @Fluent
  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  public Future<Job> state(JobState newState, Handler<Void> handler) {
    System.out.println("STATE OPEN");
    Future<Job> future = Future.future();

    JobState oldState = this.state;
    RedisTransaction multi = client.transaction().multi(_failure());
    if (oldState != null && !oldState.equals(newState)) {
      multi.zrem(RedisHelper.getKey("jobs:" + oldState.name()), this.zid, _failure())
        .zrem(RedisHelper.getKey("jobs:" + this.type + ":" + oldState.name()), this.zid, _failure());
    }
    multi.hset(RedisHelper.getKey("job:" + this.id), "state", newState.name(), _failure())
      .zadd(RedisHelper.getKey("jobs:" + newState.name()), this.priority.getValue(), this.zid, _failure())
      .zadd(RedisHelper.getKey("jobs:" + this.type + ":" + newState.name()), this.priority.getValue(), this.zid, _failure());

    switch (newState) {
      case ACTIVE:
        multi.zadd(RedisHelper.getKey("jobs:" + newState.name()),
          this.priority.getValue() < 0 ? this.priority.getValue() : -this.priority.getValue(),
          this.zid, null);
        break;
      case DELAYED:
        // TODO:
        break;
      case INACTIVE:
        multi.lpush(RedisHelper.getKey(this.type + ":jobs"), "1", _failure());
        break;
      default:
    }

    this.state = newState;

    multi.exec(r -> {
      if (r.succeeded()) {
        System.out.println("STATE SUCCESS");
        handler.handle(null);
      } else {
        r.cause().printStackTrace();
        future.fail(r.cause());
      }
    });
    return future.compose(Job::updateNow);
  }

  public Future<Job> error(Throwable ex) {
    // TODO: emit error
    return this.set("error", ex.getMessage())
      .compose(j -> j.log("error | " + ex.getMessage()));
  }

  public Future<Job> complete(Handler<Void> handler) {
    return this.setProgress(100)
      .set("progress", "100")
      .compose(r -> r.state(JobState.COMPLETE, handler));
  }

  public Future<Job> failed(Handler<Void> handler) {
    this.getJobMetrics().setFailedAt(System.currentTimeMillis());
    return this.updateNow()
      .compose(j -> j.state(JobState.FAILED, handler));
  }

  public Future<Job> inactive(Handler<Void> handler) {
    return this.state(JobState.INACTIVE, handler);
  }

  public Future<Job> active(Handler<Void> handler) {
    return this.state(JobState.ACTIVE, handler);
  }

  public Future<Job> delayed(Handler<Void> handler) {
    return this.state(JobState.DELAYED, handler);
  }

  public Future<Job> log(String msg) {
    Future<Job> future = Future.future();
    client.rpush(RedisHelper.getKey("job:" + this.id + ":log"), msg, _completer(future, this));
    return future.compose(Job::updateNow);
  }

  @Fluent
  public Job progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.setProgress(n)
      .updateNow();
    // TODO: need callback?
    // eventBus.send(Kue.getHandlerAddress("failure", this.type), n);
    return this;
  }

  public Future<Job> set(String key, String value) {
    Future<Job> future = Future.future();
    client.hset(RedisHelper.getKey("job:" + this.id), key, value, r -> {
      if (r.succeeded())
        future.complete(this);
      else
        future.fail(r.cause());
    });
    return future;
  }

  @Fluent
  private Job get(String key, Handler<AsyncResult<String>> handler) {
    client.hget(RedisHelper.getKey("job:" + this.id), key, handler);
    return this;
  }

  private Future<Job> attempt() {
    Future<Job> future = Future.future();
    String key = RedisHelper.getKey("job:" + this.id);
    if (this.attempts < this.maxAttempts) {
      client.hincrby(key, "attempts", 1, r -> {
        if (r.succeeded()) {
          this.attempts = r.result().intValue();
          future.complete(this);
        } else {
          future.fail(r.cause());
        }
      });
    } else {
      future.complete(this);
    }
    return future;
  }

  public Job failedAttempt(Throwable err, Handler<AsyncResult<Job>> handler) {
    this.error(err)
      .compose(j -> j.failed(v -> {
        this.attempt().setHandler(r -> {
          if (r.failed()) {
            this.emit("error", new JsonObject().put("errorMsg", r.cause().getMessage())); // can we emit exception directly?
            handler.handle(Future.failedFuture(r.cause()));
          } else {
            int remaining = maxAttempts - attempts;
            if (remaining > 0) {
              // reattempt
            } else if (remaining == 0) {
              handler.handle(Future.succeededFuture(r.result()));
            } else {
              handler.handle(Future.failedFuture(new IllegalStateException("Attempts Exceeded")));
            }
          }
        });
      })).setHandler(null); // // FIXME: 16-6-8 
    return this;
  }

  private static <T> Handler<AsyncResult<T>> _failure() {
    return r -> {
      if (r.failed())
        r.cause().printStackTrace();
    };
  }

  private static <T> Handler<AsyncResult<T>> _failure(Future future) {
    return r -> {
      if (r.failed())
        future.fail(r.cause());
    };
  }

  private static <T, R> Handler<AsyncResult<T>> _completer(Future<R> future, R result) {
    return r -> {
      if (r.failed())
        future.fail(r.cause());
      else
        future.complete(result);
    };
  }

  private static <T, R> Handler<AsyncResult<T>> _completer(List<Future<R>> list) {
    return r -> {
      if (r.failed())
        list.add(Future.failedFuture(r.cause()));
      else
        list.add(Future.succeededFuture());
    };
  }

  private static <T, R> Handler<AsyncResult<T>> _completer(List<Future<R>> list, R result) {
    return r -> {
      if (r.failed())
        list.add(Future.failedFuture(r.cause()));
      else
        list.add(Future.succeededFuture(result));
    };
  }

  public static Future<Job> getJob(long id, String jobType) {
    Future<Job> future = Future.future();
    String zid = RedisHelper.createFIFO(id);
    client.hgetall(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        try {
          Job job = new Job(r.result());
          job.zid = zid;
          future.complete(job); // this cause bad failure
        } catch (Exception e) {
          removeBadJob(id, jobType);
          future.fail(e);
        }
      } else {
        removeBadJob(id, jobType);
        future.fail(r.cause());
      }
    });
    return future;
  }

  public static Future<Void> removeBadJob(long id, String jobType) {
    Future<Void> future = Future.future();
    String zid = RedisHelper.createFIFO(id);
    client.transaction().multi(null)
      .del(RedisHelper.getKey("job:" + id + ":log"), null)
      .del(RedisHelper.getKey("job:" + id), null)
      .zrem(RedisHelper.getKey("jobs:INACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:ACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:COMPLETE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:FAILED"), zid, null)
      .zrem(RedisHelper.getKey("jobs:DELAYED"), zid, null)
      .zrem(RedisHelper.getKey("jobs"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":INACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":ACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":COMPLETE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":FAILED"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":DELAYED"), zid, null)
      .exec(_failure(future));

    // TODO: search functionality

    return future;
  }

  public Future<Job> save() { // fixme: chain may block, need check
    // check
    Objects.requireNonNull(this.type, "Job type cannot be null");

    if (this.id > 0)
      return update();

    Future<Job> future = Future.future();
    // generate id
    client.incr(RedisHelper.getKey("ids"), res -> {
      if (res.succeeded()) {
        this.id = res.result();
        this.zid = RedisHelper.createFIFO(id);
        String key = RedisHelper.getKey("job:" + this.id);
        // need subscribe
        if (this.delay > 0) {
          this.state = JobState.DELAYED;
        }
        RedisTransaction multi = client.transaction().multi(null);
        multi.sadd(RedisHelper.getKey("job:types"), this.type, _failure());
        this.jobMetrics.setCreatedAt(System.currentTimeMillis());
        this.jobMetrics.setPromoteAt(System.currentTimeMillis() + this.delay);
        System.out.println(this);
        // save job
        multi.hmset(key, this.toJson(), _failure())
          .exec(_completer(future, this));
      } else {
        future.fail(res.cause());
      }
    });
    // TODO: other update logic
    return future.compose(Job::update);
  }

  public Future<Job> updateNow() {
    this.getJobMetrics().updateNow();
    return this.set("jobMetrics", this.getJobMetrics().toJson().encodePrettily());
  }

  public Future<Job> update() {
    Future<Job> future = Future.future();
    this.jobMetrics.updateNow();
    client.transaction().multi(null)
      .hmset(RedisHelper.getKey("job:" + this.id), this.toJson(), _failure())
      .zadd(RedisHelper.getKey("jobs"), this.priority.getValue(), this.zid, _failure())
      .exec(_completer(future, this));

    // TODO: search

    return future.compose(r ->
      this.state(this.state, noop));
  }

  @Fluent
  public Job onComplete(Handler<Job> completeHandler) {
    eventBus.consumer(Kue.getHandlerAddress("complete", this.type), message -> {
      completeHandler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  @Fluent
  public Job onFailure(Handler<Job> failureHandler) {
    eventBus.consumer(Kue.getHandlerAddress("failure", this.type), message -> {
      failureHandler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  @Fluent
  public Job onProgress(Handler<Integer> progressHandler) {
    eventBus.consumer(Kue.getHandlerAddress("progress", this.type), message -> {
      progressHandler.handle((Integer) message.body());
    });
    return this;
  }

  @Fluent
  public Job on(String event, Handler handler) {
    eventBus.consumer(Kue.getHandlerAddress(event, this.type), message -> {
      handler.handle(message.body());
    });
    return this;
  }

  @Fluent
  public Job emit(String event, JsonObject msg) {
    eventBus.send(Kue.getHandlerAddress(event, this.type), msg);
    return this;
  }

  @Fluent
  public Job removeOnComplete() {
    eventBus.consumer(Kue.getHandlerAddress("complete", this.type)).unregister();
    return this;
  }


  public long getId() {
    return id;
  }

  public Job setId(long id) {
    this.id = id;
    return this;
  }

  public JsonObject getData() {
    return data;
  }

  public Job setData(JsonObject data) {
    this.data = data;
    return this;
  }

  public String getType() {
    return type;
  }

  public Job setType(String type) {
    this.type = type;
    return this;
  }

  public Priority getPriority() {
    return priority;
  }

  public Job setPriority(Priority priority) {
    this.priority = priority;
    return this;
  }

  public JsonObject getResult() {
    return result;
  }

  public Job setResult(JsonObject result) {
    this.result = result;
    return this;
  }

  public int getProgress() {
    return progress;
  }

  public Job setProgress(int progress) {
    this.progress = progress;
    return this;
  }

  public JobMetrics getJobMetrics() {
    return jobMetrics;
  }

  public Job setJobMetrics(JobMetrics jobMetrics) {
    this.jobMetrics = jobMetrics;
    return this;
  }

  public int getDelay() {
    return delay;
  }

  public Job setDelay(int delay) {
    if (delay > 0) {
      this.delay = delay;
    }
    return this;
  }

  public JobState getState() {
    return state;
  }

  public Job setState(JobState state) {
    this.state = state;
    return this;
  }

  public String getZid() {
    return zid;
  }

  public Job setZid(String zid) {
    this.zid = zid;
    return this;
  }

  public boolean hasAttempts() {
    return this.maxAttempts - this.attempts > 0;
  }

  public int getAttempts() {
    return attempts;
  }

  public Job setAttempts(int attempts) {
    this.attempts = attempts;
    return this;
  }

  @Fluent
  public Job addAttempts() {
    this.attempts++;
    return this;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public Job setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
    return this;
  }

  private static final Handler noop = r -> {
  };

  @Override
  public String toString() {
    return this.toJson().encodePrettily();
  }
}
