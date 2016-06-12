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
import java.util.Optional;

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
    // generated converter cannot handle this
    if (this.getJobMetrics().getCreatedAt() > 0) {
      this.setJobMetrics(new JobMetrics(json.getString("jobMetrics")));
      this.setData(new JsonObject(json.getString("data")));
    }
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

  /**
   * Set job priority
   *
   * @param level job priority level
   */
  @Fluent
  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  /**
   * Set new job state
   *
   * @param newState new job state
   * @return async result of this job
   */
  public Future<Job> state(JobState newState) { // FIXME: ESSENTIAL BUG: 16-6-11
    Future<Job> future = Future.future();
    RedisClient client = RedisHelper.client(vertx, new JsonObject());
    JobState oldState = this.state;
    client.transaction().multi(r0 -> {
      if (r0.succeeded()) {
        if (oldState != null && !oldState.equals(newState)) {
          client.transaction().zrem(RedisHelper.getKey("jobs:" + oldState.name()), this.zid, _failure())
            .zrem(RedisHelper.getKey("jobs:" + this.type + ":" + oldState.name()), this.zid, _failure());
        }
        client.transaction().hset(RedisHelper.getKey("job:" + this.id), "state", newState.name(), _failure())
          .zadd(RedisHelper.getKey("jobs:" + newState.name()), this.priority.getValue(), this.zid, _failure())
          .zadd(RedisHelper.getKey("jobs:" + this.type + ":" + newState.name()), this.priority.getValue(), this.zid, _failure());

        switch (newState) {
          case ACTIVE:
            client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
              this.priority.getValue() < 0 ? this.priority.getValue() : -this.priority.getValue(),
              this.zid, _failure());
            break;
          case DELAYED:
            // TODO:
            break;
          case INACTIVE:
            client.transaction().lpush(RedisHelper.getKey(this.type + ":jobs"), "1", _failure());
            break;
          default:
        }

        this.state = newState;

        client.transaction().exec(r -> {
          if (r.succeeded()) {
            System.out.println("STATE SUCCESS");
          } else {
            System.err.println("STATE FAIL!");
            r.cause().printStackTrace();
            future.fail(r.cause());
          }
        });
      } else {
        System.out.println("F E");
        r0.cause().printStackTrace();
      }
    }); //changed

    return future.compose(Job::updateNow);
  }

  public Future<Job> error(Throwable ex) {
    // TODO: emit error
    return this.set("error", ex.getMessage())
      .compose(j -> j.log("error | " + ex.getMessage()));
  }

  /**
   * Complete a job
   */
  public Future<Job> complete() {
    return this.setProgress(100)
      .set("progress", "100")
      .compose(r -> r.state(JobState.COMPLETE));
  }

  /**
   * Set a job failed
   */
  public Future<Job> failed() {
    this.getJobMetrics().setFailedAt(System.currentTimeMillis());
    return this.updateNow()
      .compose(j -> j.state(JobState.FAILED));
  }

  /**
   * Set a job inactive
   */
  public Future<Job> inactive() {
    return this.state(JobState.INACTIVE);
  }

  /**
   * Set a job active(started)
   */
  public Future<Job> active() {
    return this.state(JobState.ACTIVE);
  }

  /**
   * Set a job delayed
   */
  public Future<Job> delayed() {
    return this.state(JobState.DELAYED);
  }

  /**
   * Log with some messages
   */
  public Future<Job> log(String msg) {
    Future<Job> future = Future.future();
    client.rpush(RedisHelper.getKey("job:" + this.id + ":log"), msg, _completer(future, this));
    return future.compose(Job::updateNow);
  }

  /**
   * Set progress
   * @param complete current value
   * @param total total value
   */
  @Fluent
  public Job progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.setProgress(n)
      .updateNow();
    // TODO: need callback?
    // eventBus.send(Kue.getHandlerAddress("failure", this.type), n);
    return this;
  }

  /**
   * Set a key with value in Redis
   * @param key property key
   * @param value value
   */
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
    return this; // TODO: refactor
    /* this.error(err)
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
      })).setHandler(null); */
  }

  /**
   * Save the job
   */
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

  /**
   * Update the job update time (`updateTime`)
   */
  public Future<Job> updateNow() {
    this.getJobMetrics().updateNow();
    return this.set("jobMetrics", this.getJobMetrics().toJson().encodePrettily());
  }

  /**
   * Update the job
   */
  public Future<Job> update() {
    Future<Job> future = Future.future();
    this.jobMetrics.updateNow();
    client.transaction().multi(null)
      .hmset(RedisHelper.getKey("job:" + this.id), this.toJson(), _failure())
      .zadd(RedisHelper.getKey("jobs"), this.priority.getValue(), this.zid, _failure())
      .exec(_completer(future, this));

    // TODO: search

    return future.compose(r ->
      this.state(this.state));
  }

  /**
   * Remove the job
   */
  public Future<Void> remove() {
    Future<Void> future = Future.future();
    client.transaction().multi(_failure())
      .zrem(RedisHelper.getKey("jobs:" + this.stateName()), this.zid, _failure())
      .zrem(RedisHelper.getKey("jobs:" + this.type + ":" + this.stateName()), this.zid, _failure())
      .zrem(RedisHelper.getKey("jobs"), this.zid, _failure())
      .del(RedisHelper.getKey("job:" + this.id + ":log"), _failure())
      .del(RedisHelper.getKey("job" + this.id), _failure())
      .exec(r -> {
        if (r.succeeded()) {
          // TODO: emit remove event
          future.complete();
        } else {
          future.fail(r.cause());
        }
      });
    return future;
  }

  /**
   * Add on complete handler on event bus
   * @param completeHandler complete handler
   */
  @Fluent
  public Job onComplete(Handler<Job> completeHandler) {
    eventBus.consumer(Kue.getHandlerAddress("complete", this.type), message -> {
      completeHandler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  /**
   * Add on failure handler on event bus
   * @param failureHandler failure handler
   */
  @Fluent
  public Job onFailure(Handler<Job> failureHandler) {
    eventBus.consumer(Kue.getHandlerAddress("failure", this.type), message -> {
      failureHandler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  /**
   * Add on progress changed handler on event bus
   * @param progressHandler progress handler
   */
  @Fluent
  public Job onProgress(Handler<Integer> progressHandler) {
    eventBus.consumer(Kue.getHandlerAddress("progress", this.type), message -> {
      progressHandler.handle((Integer) message.body());
    });
    return this;
  }

  /**
   * Add a certain event handler on event bus
   * @param event event type
   * @param handler event handler
   */
  @Fluent
  public Job on(String event, Handler handler) {
    eventBus.consumer(Kue.getHandlerAddress(event, this.type), message -> {
      handler.handle(message.body());
    });
    return this;
  }

  /**
   * Send an event to event bus with some data
   * @param event event type
   * @param msg data
   */
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

  // static method, may be removed later

  /**
   * Get job from backend by id
   *
   * @param id job id
   * @return async result
   */
  public static Future<Optional<Job>> getJob(long id) { // use `Option`?
    return getJob(id, "");
  }

  public static Future<Optional<Job>> getJob(long id, String jobType) { // use `Option`?
    Future<Optional<Job>> future = Future.future();
    String zid = RedisHelper.createFIFO(id);
    client.hgetall(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        try {
          System.out.println(r.result());
          if (!r.result().containsKey("id")) {
            future.complete(Optional.empty());
          } else {
            Job job = new Job(r.result());
            job.zid = zid;
            future.complete(Optional.of(job));
          }
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

  /**
   * Remove job by id
   *
   * @param id job id
   * @return async result
   */
  public static Future<Void> removeJob(long id) {
    return getJob(id).compose(r -> {
      if (r.isPresent()) {
        return r.get().remove();
      } else {
        return Future.succeededFuture();
      }
    });
  }

  /**
   * Remove bad job by id (absolutely)
   *
   * @param id job id
   * @return async result
   */
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

  // getter and setter

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

  public String stateName() {
    return state.name();
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
  public Job attemptAdd() {
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

  /**
   * Basic failure handler (always throws the exception)
   */
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

  @Override
  public String toString() {
    return this.toJson().encodePrettily();
  }
}
