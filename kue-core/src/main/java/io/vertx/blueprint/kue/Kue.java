package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.service.JobService;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

import io.vertx.blueprint.kue.util.RedisHelper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.vertx.blueprint.kue.queue.KueVerticle.*;

/**
 * Vert.x Blueprint - Job Queue
 * Kue class
 *
 * @author Eric Zhao
 */
public class Kue {

  private final JsonObject config;
  private final Vertx vertx;
  private final KueService kueService;
  private final JobService jobService;
  private final RedisClient client;

  public Kue(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.kueService = KueService.createProxy(vertx, EB_KUE_SERVICE_ADDRESS);
    this.jobService = JobService.createProxy(vertx, EB_JOB_SERVICE_ADDRESS);
    this.client = RedisHelper.client(vertx, config);
    Job.setVertx(vertx, RedisHelper.client(vertx, config)); // init static vertx instance inner job
  }

  /**
   * Generate handler address on event bus
   * Format: vertx.kue.handler.job.{handlerType}.{jobType}
   *
   * @return corresponding address
   */
  public static String getHandlerAddress(String handlerType, String jobType) {
    return "vertx.kue.handler.job." + handlerType + "." + jobType;
  }

  /**
   * Generate handler address with certain job on event bus
   * Format: vertx.kue.handler.job.{handlerType}.{addressId}.{jobType}
   *
   * @return corresponding address
   */
  public static String getCertainJobAddress(String handlerType, Job job) {
    return "vertx.kue.handler.job." + handlerType + "." + job.getAddress_id() + "." + job.getType();
  }

  @Deprecated
  public static String workerAddress(String eventType) {
    return "vertx.kue.handler.workers." + eventType;
  }

  /**
   * Generate worker address on event bus
   * Format: vertx.kue.handler.workers.{eventType}
   *
   * @return corresponding address
   */
  public static String workerAddress(String eventType, Job job) {
    return "vertx.kue.handler.workers." + eventType + "." + job.getAddress_id();
  }

  /**
   * Create a Kue instance
   *
   * @param vertx  vertx instance
   * @param config config json object
   * @return kue instance
   */
  public static Kue createQueue(Vertx vertx, JsonObject config) {
    return new Kue(vertx, config);
  }

  /**
   * Create a job instance
   * @param type job type
   * @param data job extra data
   * @return a new job instance
   */
  public Job createJob(String type, JsonObject data) {
    return new Job(type, data);
  }

  /**
   * Process a job in asynchronous way
   *
   * @param type    job type
   * @param n       job process times
   * @param handler job process handler
   */
  public Kue process(String type, int n, Handler<AsyncResult<Job>> handler) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
    while (n-- > 0) {
      kueService.process(type, handler);
    }
    return this;
  }

  /**
   * Process a job that may be blocking
   *
   * @param type     job type
   * @param n        job process times
   * @param handler  job process handler
   */
  public Kue processBlocking(String type, int n, Handler<AsyncResult<Job>> handler) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
    while (n-- > 0) {
      kueService.processBlocking(type, handler);
    }
    return this;
  }

  // job logic

  /**
   * Get job from backend by id
   *
   * @param id job id
   * @return async result
   */
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

  /**
   * Remove a job by id
   *
   * @param id job id
   * @return async result
   */
  public Future<Void> removeJob(long id) {
    return this.getJob(id).compose(r -> {
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
  public Future<Boolean> existsJob(long id) {
    Future<Boolean> future = Future.future();
    jobService.existsJob(id, future.completer());
    return future;
  }

  /**
   * Get job log by id
   *
   * @param id job id
   * @return async result
   */
  public Future<JsonArray> getJobLog(long id) {
    Future<JsonArray> future = Future.future();
    jobService.getJobLog(id, future.completer());
    return future;
  }

  /**
   * Get a list of job in certain state in range (from, to) with order
   *
   * @return async result
   * @see JobService#jobRangeByState(String, long, long, String, Handler)
   */
  public Future<List<Job>> jobRangeByState(String state, long from, long to, String order) {
    Future<List<Job>> future = Future.future();
    jobService.jobRangeByState(state, from, to, order, future.completer());
    return future;
  }

  /**
   * Get a list of job in range (from, to) with order
   *
   * @return async result
   * @see JobService#jobRange(long, long, String, Handler)
   */
  public Future<List<Job>> jobRange(long from, long to, String order) {
    Future<List<Job>> future = Future.future();
    jobService.jobRange(from, to, order, future.completer());
    return future;
  }


  // runtime cardinality metrics
  // TODO: move to service proxy

  /**
   * Get cardinality by job type and state
   *
   * @param type  job type
   * @param state job state
   * @return corresponding cardinality (Future)
   */
  public Future<Long> cardByType(String type, JobState state) {
    Future<Long> future = Future.future();
    client.zcard(RedisHelper.getKey("jobs:" + type + ":" + state.name()), future.completer());
    return future;
  }

  /**
   * Get cardinality by job state
   *
   * @param state job state
   * @return corresponding cardinality (Future)
   */
  public Future<Long> card(JobState state) {
    Future<Long> future = Future.future();
    client.zcard(RedisHelper.getKey("jobs:" + state.name()), future.completer());
    return future;
  }

  /**
   * Get cardinality of completed jobs
   *
   * @param type job type; if null, then return global metrics
   */
  public Future<Long> completeCount(String type) {
    if (type == null)
      return this.card(JobState.COMPLETE);
    else
      return this.cardByType(type, JobState.COMPLETE);
  }

  /**
   * Get cardinality of failed jobs
   *
   * @param type job type; if null, then return global metrics
   */
  public Future<Long> failedCount(String type) {
    if (type == null)
      return this.card(JobState.FAILED);
    else
      return this.cardByType(type, JobState.FAILED);
  }

  /**
   * Get cardinality of inactive jobs
   *
   * @param type job type; if null, then return global metrics
   */
  public Future<Long> inactiveCount(String type) {
    if (type == null)
      return this.card(JobState.INACTIVE);
    else
      return this.cardByType(type, JobState.INACTIVE);
  }

  /**
   * Get cardinality of active jobs
   *
   * @param type job type; if null, then return global metrics
   */
  public Future<Long> activeCount(String type) {
    if (type == null)
      return this.card(JobState.ACTIVE);
    else
      return this.cardByType(type, JobState.ACTIVE);
  }

  /**
   * Get cardinality of delayed jobs
   *
   * @param type job type; if null, then return global metrics
   */
  public Future<Long> delayedCount(String type) {
    if (type == null)
      return this.card(JobState.DELAYED);
    else
      return this.cardByType(type, JobState.DELAYED);
  }

  /**
   * Get the job types present
   *
   * @return async result list
   */
  public Future<List<String>> getAllTypes() {
    Future<List<String>> future = Future.future();
    client.smembers(RedisHelper.getKey("job:types"), r -> {
      if (r.succeeded()) {
        future.complete(r.result().getList());
      } else {
        future.fail(r.cause());
      }
    });
    return future;
  }

  /**
   * Return job ids with the given `state`
   *
   * @param state job state
   * @return async result list
   */
  public Future<List<Long>> getIdsByState(JobState state) {
    Future<List<Long>> future = Future.future();
    client.zrange(RedisHelper.getStateKey(state), 0, -1, r -> {
      if (r.succeeded()) {
        List<Long> list = r.result().stream()
          .map(e -> RedisHelper.numStripFIFO((String) e))
          .collect(Collectors.toList());
        future.complete(list);
      } else {
        future.fail(r.cause());
      }
    });
    return future;
  }

  /**
   * Get queue work time in milliseconds
   *
   * @return async result
   */
  public Future<Long> getWorkTime() {
    Future<Long> future = Future.future();
    client.get(RedisHelper.getKey("stats:work-time"), r -> {
      if (r.succeeded()) {
        future.complete(Long.parseLong(r.result() == null ? "0" : r.result()));
      } else {
        future.fail(r.cause());
      }
    });
    return future;
  }

}
