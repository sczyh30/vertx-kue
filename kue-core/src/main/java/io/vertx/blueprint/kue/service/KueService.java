package io.vertx.blueprint.kue.service;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.service.impl.KueServiceImpl;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Service Interface
 *
 * @author Eric Zhao
 */
@ProxyGen
@VertxGen
public interface KueService {

  static KueService create(Vertx vertx, JsonObject config) {
    return new KueServiceImpl(vertx, config);
  }

  static KueService createProxy(Vertx vertx, String address) {
    return ProxyHelper.createProxy(KueService.class, vertx, address);
  }

  /**
   * Process a job in asynchronous way
   *
   * @param type    job type
   * @param n       job process times
   * @param handler job process handler
   */
  void process(String type, int n, Handler<AsyncResult<Job>> handler);

  /**
   * Process a job in synchronous and blocking way
   *
   * @param type    job type
   * @param n       job process times
   * @param handler job process handler
   */
  void processBlocking(String type, int n, Handler<AsyncResult<Job>> handler);

}
