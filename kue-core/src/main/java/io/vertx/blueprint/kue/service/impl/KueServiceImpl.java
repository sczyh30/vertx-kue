package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.KueWorker;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Service Impl
 *
 * @author Eric Zhao
 */
public class KueServiceImpl implements KueService {

  private final Vertx vertx;
  private final JsonObject config;
  private final RedisClient redis;

  public KueServiceImpl(Vertx vertx) {
    this(vertx, new JsonObject());
  }

  public KueServiceImpl(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.redis = RedisClient.create(vertx, RedisHelper.options(config));
  }

  @Override
  public void process(String type, int n, Handler<AsyncResult<Job>> handler) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
    while (n-- > 0) {
      KueWorker worker = new KueWorker(type, handler, redis);
      vertx.deployVerticle(worker, new DeploymentOptions().setWorker(true), r -> {
        if (r.succeeded())
          System.out.println("Kue worker created");
      });
    }
  }

}
