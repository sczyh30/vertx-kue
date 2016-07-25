package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.queue.KueWorker;
import io.vertx.blueprint.kue.service.JobService;
import io.vertx.blueprint.kue.util.RedisHelper;

import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.op.RangeLimitOptions;

import java.util.List;
import java.util.Optional;

import static io.vertx.blueprint.kue.queue.KueVerticle.EB_JOB_SERVICE_ADDRESS;

/**
 * The Kue class refers to a job queue.
 *
 * @author Eric Zhao
 */
public class Kue {

  private final JsonObject config;
  private final Vertx vertx;
  private final JobService jobService;
  private final RedisClient client;

  public Kue(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.jobService = JobService.createProxy(vertx, EB_JOB_SERVICE_ADDRESS);
    this.client = RedisHelper.client(vertx, config);
    Job.setVertx(vertx, RedisHelper.client(vertx, config)); // init static vertx instance inner job
  }

  /**
   * Generate handler address with certain job on event bus.
   * <p>Format: vertx.kue.handler.job.{handlerType}.{addressId}.{jobType}</p>
   *
   * @return corresponding address
   */
  public static String getCertainJobAddress(String handlerType, Job job) {
    return "vertx.kue.handler.job." + handlerType + "." + job.getAddress_id() + "." + job.getType();
  }

  /**
   * Generate worker address on event bus.
   * <p>Format: vertx.kue.handler.workers.{eventType}</p>
   *
   * @return corresponding address
   */
  public static String workerAddress(String eventType) {
    return "vertx.kue.handler.workers." + eventType;
  }

  /**
   * Generate worker address on event bus.
   * <p>Format: vertx.kue.handler.workers.{eventType}.{addressId}</p>
   *
   * @return corresponding address
   */
  public static String workerAddress(String eventType, Job job) {
    return "vertx.kue.handler.workers." + eventType + "." + job.getAddress_id();
  }

  /**
   * Create a Kue instance.
   *
   * @param vertx  vertx instance
   * @param config config json object
   * @return kue instance
   */
  public static Kue createQueue(Vertx vertx, JsonObject config) {
    return new Kue(vertx, config);
  }

  /**
   * Get the JobService.
   * <em>Notice: only available in package scope</em>
   */
  JobService getJobService() {
    return this.jobService;
  }

  /**
   * Create a job instance.
   *
   * @param type job type
   * @param data job extra data
   * @return a new job instance
   */
  public Job createJob(String type, JsonObject data) {
    return new Job(type, data);
  }

  private void processInternal(String type, Handler<Job> handler, boolean isWorker) {
    KueWorker worker = new KueWorker(type, handler, this);
    vertx.deployVerticle(worker, new DeploymentOptions().setWorker(isWorker), r0 -> {
      if (r0.succeeded()) {
        this.on("job_complete", msg -> {
          long dur = new Job(((JsonObject) msg.body()).getJsonObject("job")).getDuration();
          client.incrby(RedisHelper.getKey("stats:work-time"), dur, r1 -> {
            if (r1.failed())
              r1.cause().printStackTrace();
          });
        });
      }
    });
  }

  /**
   * Queue-level events listener.
   *
   * @param eventType event type
   * @param handler   handler
   */
  public <R> Kue on(String eventType, Handler<Message<R>> handler) {
    vertx.eventBus().consumer(Kue.workerAddress(eventType), handler);
    return this;
  }

  /**
   * Process a job in asynchronous way.
   *
   * @param type    job type
   * @param n       job process times
   * @param handler job process handler
   */
  public Kue process(String type, int n, Handler<Job> handler) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
    while (n-- > 0) {
      processInternal(type, handler, false);
    }
    setupTimers();
    return this;
  }

  /**
   * Process a job in asynchronous way (once).
   *
   * @param type    job type
   * @param handler job process handler
   */
  public Kue process(String type, Handler<Job> handler) {
    processInternal(type, handler, false);
    setupTimers();
    return this;
  }

  /**
   * Process a job that may be blocking.
   *
   * @param type    job type
   * @param n       job process times
   * @param handler job process handler
   */
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

  // job logic

  /**
   * Get job from backend by id.
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
   * Remove a job by id.
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
   * Judge whether a job with certain id exists.
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
   * Get job log by id.
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
   * Get a list of job in certain state in range (from, to) with order.
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
   * Get a list of job in certain state and type in range (from, to) with order.
   *
   * @return async result
   * @see JobService#jobRangeByType(String, String, long, long, String, Handler)
   */
  public Future<List<Job>> jobRangeByType(String type, String state, long from, long to, String order) {
    Future<List<Job>> future = Future.future();
    jobService.jobRangeByType(type, state, from, to, order, future.completer());
    return future;
  }

  /**
   * Get a list of job in range (from, to) with order.
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

  /**
   * Get cardinality by job type and state.
   *
   * @param type  job type
   * @param state job state
   * @return corresponding cardinality (Future)
   */
  public Future<Long> cardByType(String type, JobState state) {
    Future<Long> future = Future.future();
    jobService.cardByType(type, state, future.completer());
    return future;
  }

  /**
   * Get cardinality by job state.
   *
   * @param state job state
   * @return corresponding cardinality (Future)
   */
  public Future<Long> card(JobState state) {
    Future<Long> future = Future.future();
    jobService.card(state, future.completer());
    return future;
  }

  /**
   * Get cardinality of completed jobs.
   *
   * @param type job type; if null, then return global metrics.
   */
  public Future<Long> completeCount(String type) {
    Future<Long> future = Future.future();
    jobService.completeCount(type, future.completer());
    return future;
  }

  /**
   * Get cardinality of failed jobs.
   *
   * @param type job type; if null, then return global metrics.
   */
  public Future<Long> failedCount(String type) {
    Future<Long> future = Future.future();
    jobService.failedCount(type, future.completer());
    return future;
  }

  /**
   * Get cardinality of inactive jobs.
   *
   * @param type job type; if null, then return global metrics.
   */
  public Future<Long> inactiveCount(String type) {
    Future<Long> future = Future.future();
    jobService.inactiveCount(type, future.completer());
    return future;
  }

  /**
   * Get cardinality of active jobs.
   *
   * @param type job type; if null, then return global metrics.
   */
  public Future<Long> activeCount(String type) {
    Future<Long> future = Future.future();
    jobService.activeCount(type, future.completer());
    return future;
  }

  /**
   * Get cardinality of delayed jobs.
   *
   * @param type job type; if null, then return global metrics.
   */
  public Future<Long> delayedCount(String type) {
    Future<Long> future = Future.future();
    jobService.delayedCount(type, future.completer());
    return future;
  }

  /**
   * Get the job types present.
   *
   * @return async result list
   */
  public Future<List<String>> getAllTypes() {
    Future<List<String>> future = Future.future();
    jobService.getAllTypes(future.completer());
    return future;
  }

  /**
   * Return job ids with the given `state`.
   *
   * @param state job state
   * @return async result list
   */
  public Future<List<Long>> getIdsByState(JobState state) {
    Future<List<Long>> future = Future.future();
    jobService.getIdsByState(state, future.completer());
    return future;
  }

  /**
   * Get queue work time in milliseconds.
   *
   * @return async result
   */
  public Future<Long> getWorkTime() {
    Future<Long> future = Future.future();
    jobService.getWorkTime(future.completer());
    return future;
  }

  /**
   * Set up timers for checking job promotion and active job ttl.
   */
  private void setupTimers() {
    this.checkJobPromotion();
    this.checkActiveJobTtl();
  }

  /**
   * Check job promotion.
   * Promote delayed jobs, checking every `ms`.
   */
  private void checkJobPromotion() { // TODO: TO REVIEW
    int timeout = config.getInteger("job.promotion.interval", 1000);
    int limit = config.getInteger("job.promotion.limit", 1000);
    // need a mechanism to stop the circuit timer
    vertx.setPeriodic(timeout, l -> {
      client.zrangebyscore(RedisHelper.getKey("jobs:DELAYED"), String.valueOf(0), String.valueOf(System.currentTimeMillis()),
        new RangeLimitOptions(new JsonObject().put("offset", 0).put("count", limit)), r -> {
          if (r.succeeded()) {
            r.result().forEach(r1 -> {
              long id = Long.parseLong(RedisHelper.stripFIFO((String) r1));
              this.getJob(id).compose(jr -> jr.get().inactive())
                .setHandler(jr -> {
                  if (jr.succeeded()) {
                    jr.result().emit("promotion", jr.result().getId());
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

  /**
   * Check active job ttl.
   */
  private void checkActiveJobTtl() {  // TODO
    int timeout = config.getInteger("job.ttl.interval", 1000);
    int limit = config.getInteger("job.ttl.limit", 1000);
    // need a mechanism to stop the circuit timer
    vertx.setPeriodic(timeout, l -> {
      client.zrangebyscore(RedisHelper.getKey("jobs:ACTIVE"), String.valueOf(100000), String.valueOf(System.currentTimeMillis()),
        new RangeLimitOptions(new JsonObject().put("offset", 0).put("count", limit)), r -> {

        });
    });
  }

}
