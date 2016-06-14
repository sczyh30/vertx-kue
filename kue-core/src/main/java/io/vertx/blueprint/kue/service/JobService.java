package io.vertx.blueprint.kue.service;

import io.vertx.blueprint.kue.queue.Job;
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
 * Vert.x Blueprint - Job Queue
 * Job Service Interface
 *
 * @author Eric Zhao
 */
@ProxyGen
@VertxGen
public interface JobService {

  static JobService create(Vertx vertx, JsonObject config) {
    return new JobServiceImpl(vertx, config);
  }

  static JobService createProxy(Vertx vertx, String address) {
    return ProxyHelper.createProxy(JobService.class, vertx, address);
  }

  /**
   * Get job from backend by id
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService getJob(long id, Handler<AsyncResult<Job>> handler);

  /**
   * Remove a job by id
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService removeJob(long id, Handler<AsyncResult<Void>> handler);

  /**
   * Judge whether a job with certain id exists
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService existsJob(long id, Handler<AsyncResult<Boolean>> handler);

  /**
   * Get job log by id
   *
   * @param id      job id
   * @param handler async result handler
   */
  @Fluent
  JobService getJobLog(long id, Handler<AsyncResult<JsonArray>> handler);

  /**
   * Get a list of job in certain state in range (from, to) with order
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
   * Get a list of job in range (from, to) with order
   *
   * @param from    from
   * @param to      to
   * @param order   range order
   * @param handler async result handler
   */
  @Fluent
  JobService jobRange(long from, long to, String order, Handler<AsyncResult<List<Job>>> handler);

}
