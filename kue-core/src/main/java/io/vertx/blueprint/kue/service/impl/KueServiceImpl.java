package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.KueVerticle;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

import static io.vertx.blueprint.kue.util.RedisHelper.getRedisKey;

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
  public void process(String type, int n, Handler<AsyncResult<JsonObject>> handler) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
    while (n-- > 0) {
      vertx.executeBlocking(f -> {
        // TODO: process the job
      }, false, r -> {
        // TODO: process the maybe result
      });
    }
  }

  private void getJob(Handler<Job> handler) {
    // TODO
  }



}
