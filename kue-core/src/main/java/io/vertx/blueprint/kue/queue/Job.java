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
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisTransaction;

import java.util.Objects;
import java.util.UUID;

/**
 * Vert.x Blueprint - Job Queue
 * Job Class
 *
 * @author Eric Zhao
 */
@DataObject(generateConverter = true)
public class Job {

  private static Logger logger = LoggerFactory.getLogger(Job.class);

  private static Vertx vertx;
  private static RedisClient client;
  private static EventBus eventBus;

  public static void setVertx(Vertx v, RedisClient redisClient) {
    vertx = v;
    client = redisClient;
    eventBus = vertx.eventBus();
  }

  // job properties

  private final String address_id;
  private long id = -1;
  private String type;
  private JsonObject data;
  private Priority priority = Priority.NORMAL;
  private int delay = 0;
  private JobState state = JobState.INACTIVE;
  private int attempts = 0;
  private int max_attempts = 1;
  private boolean removeOnComplete = false;

  private String zid;
  private int progress = 0;
  private JsonObject result;

  // job metrics
  private long created_at;
  private long promote_at;
  private long updated_at;
  private long failed_at;
  private long started_at;
  private long duration;

  public Job() {
    this.address_id = UUID.randomUUID().toString();
    _checkStatic();
  }

  public Job(JsonObject json) {
    JobConverter.fromJson(json, this);
    this.address_id = json.getString("address_id");
    // generated converter cannot handle this
    if (this.data == null) {
      this.data = new JsonObject(json.getString("data"));
      this.progress = Integer.parseInt(json.getString("progress"));
      this.created_at = Long.parseLong(json.getString("created_at"));
      this.updated_at = Long.parseLong(json.getString("updated_at"));
      this.started_at = Long.parseLong(json.getString("started_at"));
      this.promote_at = Long.parseLong(json.getString("promote_at"));
      this.delay = Integer.parseInt(json.getString("delay"));
      this.duration = Long.parseLong(json.getString("duration"));
    }
    if (this.id < 0) {
      if ((json.getValue("id")) instanceof CharSequence)
        this.setId(Long.parseLong(json.getString("id")));
    }
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

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    JobConverter.toJson(this, json);
    return json;
  }

  private void _checkStatic() {
    if (vertx == null) {
      logger.warn("static Vertx instance in Job class is not initialized!");
    }
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
   * Add one attempt time
   */
  @Fluent
  public Job attemptAdd() {
    this.attempts++;
    return this;
  }

  /**
   * Set new job state
   *
   * @param newState new job state
   * @return async result of this job
   */
  public Future<Job> state(JobState newState) {
    Future<Job> future = Future.future();
    RedisClient client = RedisHelper.client(vertx, new JsonObject()); // use a new client to keep transaction
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

        switch (newState) { // dispatch different state
          case ACTIVE:
            client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
              this.priority.getValue() < 0 ? this.priority.getValue() : -this.priority.getValue(),
              this.zid, _failure());
            break;
          case DELAYED:
            client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
              this.promote_at, this.zid, _failure());
            break;
          case INACTIVE:
            client.transaction().lpush(RedisHelper.getKey(this.type + ":jobs"), "1", _failure());
            break;
          default:
        }

        this.state = newState;

        client.transaction().exec(r -> {
          if (r.succeeded()) {
            future.complete(this);
          } else {
            future.fail(r.cause());
          }
        });
      } else {
        r0.cause().printStackTrace();
      }
    });

    return future.compose(Job::updateNow);
  }

  /**
   * Set error to the job
   *
   * @param ex exception
   */
  public Future<Job> error(Throwable ex) {
    // send this on worker address in order to consume it with `Kue#on` method
    eventBus.send(Kue.workerAddress("error"), new JsonObject().put("id", this.id)
      .put("message", ex.getMessage()));
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
    this.failed_at = System.currentTimeMillis();
    return this.updateNow()
      .compose(j -> j.set("failed_at", String.valueOf(j.failed_at)))
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
   *
   * @param complete current value
   * @param total    total value
   */
  public Future<Job> progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.emit("progress", n);
    return this.setProgress(n)
      .set("progress", String.valueOf(n))
      .compose(Job::updateNow);
  }

  /**
   * Set a key with value in Redis
   *
   * @param key   property key
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

  /**
   * Get a property of the job from backend
   *
   * @param key property key(name)
   * @return async result
   */
  @Fluent
  public Future<String> get(String key) {
    Future<String> future = Future.future();
    client.hget(RedisHelper.getKey("job:" + this.id), key, future.completer());
    return future;
  }

  /**
   * Attempt once and save attempt times to Redis
   */
  Future<Job> attempt() {
    Future<Job> future = Future.future();
    String key = RedisHelper.getKey("job:" + this.id);
    if (this.attempts < this.max_attempts) {
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

  /**
   * Failed attempt
   *
   * @param err exception
   */
  Future<Job> failedAttempt(Throwable err) { // TODO: reattempt logic should implement `Failure Backoff`
    Future<Job> future = Future.future();
    this.error(err)
      .compose(Job::failed)
      .compose(Job::attempt)
      .setHandler(r -> {
        if (r.succeeded()) {
          Job j = r.result();
          int remaining = j.max_attempts - j.attempts;
          if (remaining > 0) {
            // reattempt
            j.inactive().setHandler(r1 -> {
              if (r1.succeeded()) {
                future.complete(r1.result());
              } else {
                future.fail(r1.cause());
              }
            });
          } else if (remaining == 0) {
            future.complete(r.result());
          } else {
            future.fail(new IllegalStateException("Attempts Exceeded"));
          }
        } else {
          this.emit("error", new JsonObject().put("error", r.cause().getMessage()));
          future.fail(r.cause());
        }
      });
    return future;
  }

  /**
   * Save the job
   */
  public Future<Job> save() {
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
        this.created_at = System.currentTimeMillis();
        this.promote_at = this.created_at + this.delay;
        // save job
        multi.hmset(key, this.toJson(), _failure())
          .exec(_completer(future, this));
      } else {
        future.fail(res.cause());
      }
    });

    return future.compose(Job::update);
  }

  /**
   * Update the job update time (`updateTime`)
   */
  Future<Job> updateNow() {
    this.updated_at = System.currentTimeMillis();
    return this.set("updated_at", String.valueOf(updated_at));
  }

  /**
   * Update the job
   */
  Future<Job> update() {
    Future<Job> future = Future.future();
    this.updated_at = System.currentTimeMillis();

    client.transaction().multi(_failure())
      .hmset(RedisHelper.getKey("job:" + this.id), this.toJson(), _failure())
      .zadd(RedisHelper.getKey("jobs"), this.priority.getValue(), this.zid, _failure())
      .exec(_completer(future, this));

    // TODO: add search functionality

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

  /**
   * Add on complete handler on event bus
   *
   * @param completeHandler complete handler
   */
  @Fluent
  public Job onComplete(Handler<Job> completeHandler) {
    this.on("complete", message -> {
      completeHandler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  /**
   * Add on failure handler on event bus
   *
   * @param failureHandler failure handler
   */
  @Fluent
  public Job onFailure(Handler<JsonObject> failureHandler) {
    this.on("failed", message -> {
      failureHandler.handle((JsonObject) message.body());
    });
    return this;
  }

  /**
   * Add on failure attempt handler on event bus
   *
   * @param failureHandler failure handler
   */
  @Fluent
  public Job onFailureAttempt(Handler<JsonObject> failureHandler) {
    this.on("failed_attempt", message -> {
      failureHandler.handle((JsonObject) message.body());
    });
    return this;
  }

  /**
   * Add on promotion handler on event bus
   *
   * @param handler failure handler
   */
  @Fluent
  public Job onPromotion(Handler<Job> handler) {
    this.on("promotion", message -> {
      handler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  /**
   * Add on start handler on event bus
   *
   * @param handler failure handler
   */
  @Fluent
  public Job onStart(Handler<Job> handler) {
    this.on("start", message -> {
      handler.handle(new Job((JsonObject) message.body()));
    });
    return this;
  }

  /**
   * Add on remove handler on event bus
   *
   * @param removeHandler failure handler
   */
  @Fluent
  public Job onRemove(Handler<JsonObject> removeHandler) {
    this.on("start", message -> {
      removeHandler.handle((JsonObject) message.body());
    });
    return this;
  }

  /**
   * Add on progress changed handler on event bus
   *
   * @param progressHandler progress handler
   */
  @Fluent
  public Job onProgress(Handler<Integer> progressHandler) {
    this.on("progress", message -> {
      progressHandler.handle((Integer) message.body());
    });
    return this;
  }

  /**
   * Add a certain event handler on event bus
   *
   * @param event   event type
   * @param handler event handler
   */
  @Fluent
  public <T> Job on(String event, Handler<Message<T>> handler) {
    logger.debug("[LOG] On: " + Kue.getCertainJobAddress(event, this));
    eventBus.consumer(Kue.getCertainJobAddress(event, this), handler);
    return this;
  }

  /**
   * Send an event to event bus with some data
   *
   * @param event event type
   * @param msg   data
   */
  @Fluent
  public Job emit(String event, Object msg) {
    logger.debug("[LOG] Emit: " + Kue.getCertainJobAddress(event, this));
    eventBus.send(Kue.getCertainJobAddress(event, this), msg);
    return this;
  }

  /**
   * Fail a job
   */
  @Fluent
  public Job done(Throwable ex) {
    eventBus.send(Kue.workerAddress("done_fail", this), ex.getMessage());
    return this;
  }

  /**
   * Finish a job
   */
  @Fluent
  public Job done() {
    eventBus.send(Kue.workerAddress("done", this), this.toJson());
    return this;
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
    return this.max_attempts - this.attempts > 0;
  }

  public int getAttempts() {
    return attempts;
  }

  public Job setAttempts(int attempts) {
    this.attempts = attempts;
    return this;
  }

  public long getCreated_at() {
    return created_at;
  }

  public Job setCreated_at(long created_at) {
    this.created_at = created_at;
    return this;
  }

  public long getPromote_at() {
    return promote_at;
  }

  public Job setPromote_at(long promote_at) {
    this.promote_at = promote_at;
    return this;
  }

  public long getUpdated_at() {
    return updated_at;
  }

  public Job setUpdated_at(long updated_at) {
    this.updated_at = updated_at;
    return this;
  }

  public long getFailed_at() {
    return failed_at;
  }

  public Job setFailed_at(long failed_at) {
    this.failed_at = failed_at;
    return this;
  }

  public long getStarted_at() {
    return started_at;
  }

  public Job setStarted_at(long started_at) {
    this.started_at = started_at;
    return this;
  }

  public long getDuration() {
    return duration;
  }

  public Job setDuration(long duration) {
    this.duration = duration;
    return this;
  }

  public int getMax_attempts() {
    return max_attempts;
  }

  public Job setMax_attempts(int max_attempts) {
    this.max_attempts = max_attempts;
    return this;
  }

  public String getAddress_id() {
    return address_id;
  }

  public boolean isRemoveOnComplete() {
    return removeOnComplete;
  }

  public Job setRemoveOnComplete(boolean removeOnComplete) {
    this.removeOnComplete = removeOnComplete;
    return this;
  }

  /**
   * Basic failure handler (always throws the exception)
   */
  private static <T> Handler<AsyncResult<T>> _failure() {
    return r -> {
      if (r.failed())
        r.cause().printStackTrace();
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Job job = (Job) o;

    if (id != job.id) return false;
    if (!address_id.equals(job.address_id)) return false;
    return type.equals(job.type);

  }

  @Override
  public int hashCode() {
    int result = address_id.hashCode();
    result = 31 * result + (int) (id ^ (id >>> 32));
    result = 31 * result + type.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return this.toJson().encodePrettily();
  }
}
