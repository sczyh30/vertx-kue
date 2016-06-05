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
import io.vertx.redis.RedisOptions;

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

  public Job state() {

    return this;
  }

  @Fluent
  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  @Fluent
  public Job progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.setProgress(n);
    this.jobMetrics.updateNow();
    // TODO: need callback?
    eventBus.send(Kue.getHandlerAddress("failure", this.type), n);
    return this;
  }

  private int resolvePriority() {
    switch (this.priority) {
      case LOW:
        return 10;
      case NORMAL:
        return 0;
      case MEDIUM:
        return -5;
      case HIGH:
        return -10;
      case CRITICAL:
        return -15;
      default:
        return 0;
    }
  }

  @Fluent
  private Job set(String key, String value, Handler<AsyncResult<Long>> handler) {
    client.hset(RedisHelper.getRedisKey("job:" + this.id), key, value, handler);
    return this;
  }

  @Fluent
  private Job get(String key, Handler<AsyncResult<String>> handler) {
    client.hget(RedisHelper.getRedisKey("job:" + this.id), key, handler);
    return this;
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
    client.hgetall(RedisHelper.getRedisKey("job:" + id), r -> {
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
      .del(RedisHelper.getRedisKey("job:" + id + ":log"), fh)
      .del(RedisHelper.getRedisKey("job:" + id), fh)
      .zrem(RedisHelper.getRedisKey("jobs:inactive"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:active"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:complete"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:failed"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:delayed"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:" + jobType + ":inactive"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:" + jobType + ":active"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:" + jobType + ":complete"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:" + jobType + ":failed"), zid, fh)
      .zrem(RedisHelper.getRedisKey("jobs:" + jobType + ":delayed"), zid, fh)
      .exec(_failure(list));
    // TODO: search functionality
    return list.stream()
      .reduce(Future.succeededFuture(), (a, b) -> a.compose(r -> b));
  }

  @Fluent
  public Future<Job> save() {
    // check
    Objects.requireNonNull(this.type, "Job type cannot be null");

    if (this.id > 0)
      return update(this);

    List<Future<Job>> futureList = new ArrayList<>();
    // generate id
    client.incr(RedisHelper.getRedisKey("ids"), res -> {
      if (res.succeeded()) {
        this.id = res.result();
        this.zid = RedisHelper.createFIFO(id);
        String key = RedisHelper.getRedisKey("job:" + this.id);
        // need subscribe
        if (this.delay > 0) {
          this.state = JobState.DELAYED;
        }
        client.sadd(RedisHelper.getRedisKey("job:types"), this.type, _failure(futureList));
        this.jobMetrics.setCreatedAt(System.currentTimeMillis());
        this.jobMetrics.setPromoteAt(System.currentTimeMillis() + this.delay);
        System.out.println(this.toJson().encodePrettily());
        // save job
        client.hmset(key, this.toJson(), _failure(futureList));
      } else {
        futureList.add(Future.failedFuture(res.cause()));
      }
    });
    // TODO: other update logic
    return futureList.stream()
      .reduce(Future.succeededFuture(), (a, b) -> a.compose(r -> b))
      .compose(this::update);
  }

  @Fluent
  public Future<Job> update(Job job) {
    Future<Job> future = Future.future();
    this.jobMetrics.setUpdatedAt(System.currentTimeMillis());
    client.zadd(RedisHelper.getRedisKey("jobs"), resolvePriority(), this.zid, _failure(future, job));
    // TODO: search
    return future;
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

  public Job removeOnComplete() {
    eventBus.consumer(Kue.getHandlerAddress("complete", this.type)).unregister();
    return this;
  }

}
