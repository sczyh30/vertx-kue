package io.vertx.blueprint.kue.service;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.service.impl.JobServiceImpl;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

import java.util.List;

/**
 * Service interface for task operations.
 *
 * @author Eric Zhao
 */
@ProxyGen
@VertxGen
public interface JobService {

  /**
   * Factory method for creating a {@link JobService} instance.
   *
   * @param vertx  Vertx instance
   * @param config configuration
   * @return the new {@link JobService} instance
   */
  static JobService create(Vertx vertx, JsonObject config) {
    return new JobServiceImpl(vertx, config);
  }

  /**
   * Factory method for creating a {@link JobService} service proxy.
   * This is useful for doing RPCs.
   *
   * @param vertx Vertx instance
   * @param address event bus address of RPC
   * @return the new {@link JobService} service proxy
   */
  static JobService createProxy(Vertx vertx, String address) {
    return ProxyHelper.createProxy(JobService.class, vertx, address);
  }

  /**
   * Get the certain from backend by id.
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService getJob(long id, Handler<AsyncResult<Job>> handler);

  /**
   * Remove a job by id.
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService removeJob(long id, Handler<AsyncResult<Void>> handler);

  /**
   * Judge whether a job with certain id exists.
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService existsJob(long id, Handler<AsyncResult<Boolean>> handler);

  /**
   * Get job log by id.
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService getJobLog(long id, Handler<AsyncResult<JsonArray>> handler);

  /**
   * Get a list of job in certain state in range (from, to) with order.
   *
   * @param state   expected job state
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRangeByState(String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

  /**
   * Get a list of job in certain state and type in range (from, to) with order.
   *
   * @param type    expected job type
   * @param state   expected job state
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRangeByType(String type, String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

  /**
   * Get a list of job in range (from, to) with order.
   *
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRange(long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

  // Runtime cardinality metrics

  /**
   * Get cardinality by job type and state.
   *
   * @param type    job type
   * @param state   job state
   * @param handler async result handler
   */
  @Fluent
  JobService cardByType(String type, JobState state, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality by job state.
   *
   * @param state   job state
   * @param handler async result handler
   */
  @Fluent
  JobService card(JobState state, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of completed jobs.
   *
   * @param type    job type; if null, then return global metrics
   * @param handler async result handler
   */
  @Fluent
  JobService completeCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of failed jobs.
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService failedCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of inactive jobs.
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService inactiveCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of active jobs.
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService activeCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get cardinality of delayed jobs.
   *
   * @param type job type; if null, then return global metrics
   */
  @Fluent
  JobService delayedCount(String type, Handler<AsyncResult<Long>> handler);

  /**
   * Get the job types present.
   *
   * @param handler async result handler
   */
  @Fluent
  JobService getAllTypes(Handler<AsyncResult<List<String>>> handler);

  /**
   * Return job ids with the given {@link JobState}.
   *
   * @param state   job state
   * @param handler async result handler
   */
  @Fluent
  JobService getIdsByState(JobState state, Handler<AsyncResult<List<Long>>> handler);

  /**
   * Get queue work time in milliseconds.
   *
   * @param handler async result handler
   */
  @Fluent
  JobService getWorkTime(Handler<AsyncResult<Long>> handler);
}
