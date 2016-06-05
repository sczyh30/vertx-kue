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

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
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

  public void setZid(String zid) {
    this.zid = zid;
  }

  @Fluent
  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  public Future<Job> state(JobState newState, Handler<Void> handler) {
    List<Future<Job>> futureList = new ArrayList<>();

    Handler<AsyncResult<String>> _fn = _failure(futureList, null); // no use of prev result
    JobState oldState = this.state;
    RedisTransaction multi = client.transaction().multi(_failure());
    if (oldState != null && !oldState.equals(newState)) {
      multi.zrem(RedisHelper.getKey("jobs:" + oldState.state()), this.zid, _fn)
        .zrem(RedisHelper.getKey("jobs:" + this.type + ":" + oldState.state()), this.zid, _fn);
    }
    multi.hset(RedisHelper.getKey("job:" + this.id), "state", newState.state(), _fn)
      .zadd(RedisHelper.getKey("jobs:" + newState.state()), this.priority.getValue(), this.zid, _fn)
      .zadd(RedisHelper.getKey("jobs:" + this.type + ":" + newState.state()), this.priority.getValue(), this.zid, _fn);

    switch (newState) {
      case ACTIVE:
        multi.zadd(RedisHelper.getKey("jobs:" + newState.state()),
          this.priority.getValue() < 0 ? this.priority.getValue() : -this.priority.getValue(),
          this.zid, _fn);
        break;
      case DELAYED:
        // TODO:
        break;
      case INACTIVE:
        multi.lpush(RedisHelper.getKey(this.type + ":jobs"), "1", _fn);
        break;
      default:
    }

    this.state = newState;

    multi.exec(r -> {
      if (r.succeeded()) {
        handler.handle(null);
      } else {
        futureList.add(Future.failedFuture(r.cause()));
      }
    });
    return futureList.stream()
      .reduce(Future.succeededFuture(), (a, b) -> a.compose(r -> b))
      .compose(Job::update);
  }

  public Future<Job> complete(Handler<Void> handler) {
    return this.setProgress(100)
      .state(JobState.COMPLETE, handler);
  }

  public Future<Job> failed(Handler<Void> handler) {
    this.getJobMetrics().setFailedAt(System.currentTimeMillis());
    return this.state(JobState.FAILED, handler);
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
    client.rpush(RedisHelper.getKey("job:" + this.id + ":log"), msg, _failure(future, this));
    return future.compose(Job::update);
  }

  @Fluent
  public Job progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.setProgress(n)
      .update();
    // TODO: need callback?
    eventBus.send(Kue.getHandlerAddress("failure", this.type), n);
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

  private static <T, R> Handler<AsyncResult<T>> _failure(Future<R> future, R result) {
    return r -> {
      if (r.failed())
        future.fail(r.cause());
      else
        future.complete(result);
    };
  }

  private static <T, R> Handler<AsyncResult<T>> _failure(List<Future<R>> list) {
    return r -> {
      if (r.failed())
        list.add(Future.failedFuture(r.cause()));
      else
        list.add(Future.succeededFuture());
    };
  }

  private static <T, R> Handler<AsyncResult<T>> _failure(List<Future<R>> list, R result) {
    return r -> {
      if (r.failed())
        list.add(Future.failedFuture(r.cause()));
      else
        list.add(Future.succeededFuture(result));
    };
  }

  public static void getJob(long id, String jobType, Handler<Job> handler, Handler<Throwable> failureHandler) {
    String zid = RedisHelper.createFIFO(id);
    client.hgetall(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        try {
          Job job = new Job(r.result());
          job.zid = zid;
          handler.handle(job);
        } catch (Exception e) {
          removeBadJob(id, jobType);
          if (failureHandler != null)
            failureHandler.handle(r.cause());
          else
            r.cause().printStackTrace();
        }
      } else {
        removeBadJob(id, jobType);
        if (failureHandler != null)
          failureHandler.handle(r.cause());
        else
          r.cause().printStackTrace();
      }
    });
  }

  public static Future<Void> removeBadJob(long id, String jobType) {
    List<Future<Void>> list = new ArrayList<>();
    Handler<AsyncResult<String>> fh = r -> {
      if (r.succeeded()) {
        list.add(Future.succeededFuture());
      } else {
        list.add(Future.failedFuture(r.cause()));
      }
    };
    String zid = RedisHelper.createFIFO(id);
    client.transaction().multi(fh)
      .del(RedisHelper.getKey("job:" + id + ":log"), fh)
      .del(RedisHelper.getKey("job:" + id), fh)
      .zrem(RedisHelper.getKey("jobs:inactive"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:active"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:complete"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:failed"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:delayed"), zid, fh)
      .zrem(RedisHelper.getKey("jobs"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":inactive"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":active"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":complete"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":failed"), zid, fh)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":delayed"), zid, fh)
      .exec(_failure(list));
    // TODO: search functionality
    return list.stream()
      .reduce(Future.succeededFuture(), (a, b) -> a.compose(r -> b));
  }

  public Future<Job> save() {
    // check
    Objects.requireNonNull(this.type, "Job type cannot be null");

    if (this.id > 0)
      return update();

    List<Future<Job>> futureList = new ArrayList<>();
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
        client.sadd(RedisHelper.getKey("job:types"), this.type, _failure(futureList, this));
        this.jobMetrics.setCreatedAt(System.currentTimeMillis());
        this.jobMetrics.setPromoteAt(System.currentTimeMillis() + this.delay);
        System.out.println(this.toJson().encodePrettily());
        // save job
        client.hmset(key, this.toJson(), _failure(futureList, this));
      } else {
        futureList.add(Future.failedFuture(res.cause()));
      }
    });
    // TODO: other update logic
    return futureList.stream()
      .reduce(Future.succeededFuture(), (a, b) -> a.compose(r -> b))
      .compose(r -> update());
  }

  /* public Future<Job> updateNow() {
    this.getJobMetrics().updateNow();
    return this.set("jobMetrics", this.getJobMetrics().toJson().encodePrettily());
  } */

  public Future<Job> update() {
    List<Future<Job>> futureList = new ArrayList<>();
    this.jobMetrics.updateNow();
    client.hmset(RedisHelper.getKey("job:" + this.id), this.toJson(), _failure(futureList, this));
    client.zadd(RedisHelper.getKey("jobs"), this.priority.getValue(), this.zid, _failure(futureList, this));
    // TODO: search
    return futureList.stream()
      .reduce(Future.succeededFuture(), (a, b) -> a.compose(r -> b));
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

  public Job removeOnComplete() {
    eventBus.consumer(Kue.getHandlerAddress("complete", this.type)).unregister();
    return this;
  }

}
