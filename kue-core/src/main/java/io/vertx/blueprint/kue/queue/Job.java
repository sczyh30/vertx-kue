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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

  private final String address_id;
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
    this.address_id = UUID.randomUUID().toString();
  }

  public Job(JsonObject json) {
    JobConverter.fromJson(json, this);
    this.address_id = json.getString("address_id");
    // generated converter cannot handle this
    if (this.getJobMetrics().getCreatedAt() <= 0) {
      this.setJobMetrics(new JobMetrics(json.getString("jobMetrics")));
      this.setData(new JsonObject(json.getString("data")));
    }
    if (this.id < 0) {
      this.setId(Long.parseLong(json.getString("id")));
    }
  }

  public Job(Job other) {
    this.id = other.id;
    this.address_id = other.address_id; // correct?
    this.type = other.type;
    this.data = other.data == null ? null : other.data.copy();
    this.priority = other.priority;
    this.state = other.state;
    this.jobMetrics = other.jobMetrics;
  }

  public Job(String type, JsonObject data) {
    this.type = type;
    this.data = data;
    this.address_id = UUID.randomUUID().toString();
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
  public Future<Job> state(JobState newState) { // FIXED: ESSENTIAL BUG: 16-6-11 | NEED REVIEW
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
            client.transaction().zadd(RedisHelper.getKey("jobs:" + newState.name()),
              this.jobMetrics.getPromoteAt(), this.zid, _failure());
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
    // TO REVIEW: emit error => this.emit('error', msg)
    this.emit("error", new JsonObject().put("id", this.id)
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
   *
   * @param complete current value
   * @param total    total value
   */
  public Future<Job> progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.setProgress(n)
      .updateNow();
    this.emit("progress", n);
    return this.setProgress(n)
      .updateNow();
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

  @Fluent
  private Job get(String key, Handler<AsyncResult<String>> handler) {
    client.hget(RedisHelper.getKey("job:" + this.id), key, handler);
    return this;
  }

  /**
   * Attempt once and save attempt times to Redis
   */
  public Future<Job> attempt() {
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

  /**
   * Failed attempt
   *
   * @param err exception
   */
  public Future<Job> failedAttempt(Throwable err) { // TODO: reattempt logic should implement `Failure Backoff`
    Future<Job> future = Future.future();
    this.error(err)
      .compose(Job::failed)
      .compose(Job::attempt)
      .setHandler(r -> {
        if (r.succeeded()) {
          Job j = r.result();
          int remaining = j.maxAttempts - j.attempts;
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
        this.jobMetrics.setCreatedAt(System.currentTimeMillis());
        this.jobMetrics.setPromoteAt(System.currentTimeMillis() + this.delay);
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
    client.transaction().multi(_failure())
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
  public Job onFailure(Handler<Job> failureHandler) {
    this.on("failure", message -> {
      failureHandler.handle(new Job((JsonObject) message.body()));
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
    eventBus.send(Kue.getCertainJobAddress(event, this), msg);
    return this;
  }

  @Fluent
  public Job done() {
    eventBus.send(Kue.workerAddress("done", this), this.toJson());
    return this;
  }

  @Fluent
  public Job removeOnComplete() {
    eventBus.consumer(Kue.getCertainJobAddress("complete", this)).unregister();
    return this;
  }

  // static method, may be removed later

  /**
   * Get job from backend by id
   *
   * @param id job id
   * @return async result
   */
  public static Future<Optional<Job>> getJob(long id) {
    return getJob(id, "");
  }

  public static Future<Optional<Job>> getJob(long id, String jobType) {
    Future<Optional<Job>> future = Future.future();
    String zid = RedisHelper.createFIFO(id);
    client.hgetall(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        try {
          if (!r.result().containsKey("id")) {
            future.complete(Optional.empty());
          } else {
            Job job = new Job(r.result());
            job.id = id;
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
   * Judge whether a job with certain id exists
   *
   * @param id job id
   * @return async result
   */
  public static Future<Boolean> existsJob(long id) {
    Future<Boolean> future = Future.future();
    client.exists(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        if (r.result() == 0)
          future.complete(false);
        else
          future.complete(true);
      } else {
        future.fail(r.cause());
      }
    });
    return future;
  }

  /**
   * Get job log by id
   *
   * @param id job id
   * @return async result
   */
  public static Future<JsonArray> getLog(long id) {
    Future<JsonArray> future = Future.future();
    client.lrange(RedisHelper.getKey("job:" + id + ":log"), 0, -1, future.completer());
    return future;
  }

  private static Future<List<Job>> rangeGeneral(String key, long from, long to, String order) {
    Future<List<Job>> future = Future.future();
    if (to < from) {
      future.fail("to can not be greater than from");
      return future;
    }
    client.zrange(RedisHelper.getKey(key), from, to, r -> {
      if (r.succeeded()) {
        List<Long> list = (List<Long>) r.result().getList().stream()
          .map(e -> RedisHelper.numStripFIFO((String) e))
          .collect(Collectors.toList());
        long max = list.get(list.size() - 1);
        List<Job> jobList = new ArrayList<>();
        list.forEach(e -> {
          Job.getJob(e).setHandler(jr -> {
            if (jr.succeeded()) {
              if (jr.result().isPresent()) {
                jobList.add(jr.result().get());
              }
              if (e >= max) {
                jobList.sort((a1, a2) -> {
                  if (order.equals("asc"))
                    return Long.compare(a1.id, a2.id);
                  else
                    return Long.compare(a2.id, a1.id);
                });
                future.complete(jobList);
              }
            } else {
              future.fail(jr.cause());
            }
          });
        });
      } else {
        future.fail(r.cause());
      }
    });
    return future;
  }

  public static Future<List<Job>> jobRangeByState(String state, long from, long to, String order) {
    return rangeGeneral("jobs:" + state.toUpperCase(), from, to, order);
  }

  public static Future<List<Job>> jobRange(long from, long to, String order) {
    return rangeGeneral("jobs", from, to, order);
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

  public String getAddress_id() {
    return address_id;
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
