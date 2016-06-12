package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

import io.vertx.blueprint.kue.util.RedisHelper;

/**
 * Vert.x Blueprint - Job Queue
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
    this.client = RedisClient.create(vertx, RedisHelper.options(config));
    Job.setVertx(vertx, client);
  }

  public RedisClient getRedis() {
    return this.client;
  }

  /**
   * Format: vertx.kue.handler.job.handlerType.jobType
   */
  public static String getHandlerAddress(String handlerType, String jobType) {
    return "vertx.kue.handler.job." + handlerType + "." + jobType;
  }

  public static String workerAddress(String eventType) {
    return "vertx.kue.handler.workers." + eventType;
  }

  public static Kue createQueue(Vertx vertx, JsonObject config) {
    return new Kue(vertx, config);
  }

  // job stuff
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

}
