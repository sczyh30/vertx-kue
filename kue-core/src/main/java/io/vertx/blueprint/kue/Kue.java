package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

import io.vertx.blueprint.kue.util.RedisHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Vert.x Blueprint - Job Queue
 * Kue class
 *
 * @author Eric Zhao
 */
public class Kue implements KueService {

  public static final String EB_KUE_ADDRESS = "vertx.kue.service.internal";

  private final JsonObject config;
  private final Vertx vertx;
  private final KueService service;
  private final RedisClient client;

  public Kue(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.service = KueService.createProxy(vertx, EB_KUE_ADDRESS);
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
   * Generate worker address on event bus
   * Format: vertx.kue.handler.workers.{eventType}
   *
   * @return corresponding address
   */
  public static String workerAddress(String eventType) {
    return "vertx.kue.handler.workers." + eventType;
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

  @Override
  public void process(String type, int n, Handler<AsyncResult<Job>> handler) {
    service.process(type, n, handler);
  }

  @Override
  public void processBlocking(String type, int n, Handler<AsyncResult<Job>> handler) {
    service.processBlocking(type, n, handler);
  }

  // runtime cardinality metrics

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
        future.complete(Long.parseLong(r.result()));
      } else {
        future.fail(r.cause());
      }
    });
    return future;
  }

}
