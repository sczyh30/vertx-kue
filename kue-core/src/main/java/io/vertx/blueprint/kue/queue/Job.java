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

  public static void setVertx(Vertx v) {
    vertx = v;
    client = KueVerticle.getRedis();
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

  public static Future<Void> getJob(long id, String jobType, Handler<Job> handler) {
    Future<Void> future = Future.future();
    String zid = RedisHelper.createFIFO(id);
    client.hgetall(RedisHelper.getRedisKey("job:" + id), r -> {
      if (r.succeeded()) {
        try {
          Job job = new Job(r.result());
          job.zid = zid;
          handler.handle(job);
          future.complete(); // TODO: right?
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
    Handler<AsyncResult<String>> fh = r -> {
      if (r.succeeded())
        future.complete();
      else
        future.fail(r.cause());
    };
    String zid = RedisHelper.createFIFO(id);
    client.transaction().multi(_failure(future))
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
      .exec(_failure(future, null));
    // TODO: search
    return future;
  }

  @Fluent
  public Future<Job> save() {
    // check
    Objects.requireNonNull(this.type, "Job type cannot be null");

    if (this.id > 0)
      return update(this);

    Future<Job> future = Future.future();
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
        client.sadd(RedisHelper.getRedisKey("job:types"), this.type, _failure(future));
        this.jobMetrics.setCreatedAt(System.currentTimeMillis());
        this.jobMetrics.setPromoteAt(System.currentTimeMillis() + this.delay);
        // save job
        client.hmset(key, this.toJson(), _failure(future, this));
      }
    });
    // TODO
    return future.compose(this::update);
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
