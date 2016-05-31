package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.blueprint.kue.service.StorageService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Service Impl
 *
 * @author Eric Zhao
 */
public class KueServiceImpl implements KueService {

  private static final String VERTX_KUE_REDIS_PREFIX = "vertx_kue";

  private final Vertx vertx;
  private final JsonObject config;
  private final RedisClient redis;

  public KueServiceImpl(Vertx vertx) {
    this(vertx, new JsonObject());
  }

  public KueServiceImpl(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    RedisOptions redisOptions = new RedisOptions()
      .setHost(config.getString("redis.host", "127.0.0.1"))
      .setPort(config.getInteger("redis.port", 6379));
    this.redis = RedisClient.create(vertx, redisOptions);
  }

  private String getRedisKey(String key) {
    return VERTX_KUE_REDIS_PREFIX + ":" + key;
  }

  @Override
  public void process(String type, int n, Handler<AsyncResult<JsonObject>> handler) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
  }

  @Override
  public void saveJob(Job job) {
    if (job.getId() > 0)
      this.updateJob(job);

    redis.incr(getRedisKey("ids"), res -> {
      if (res.succeeded()) {
        long id = res.result();
        job.setId(id);
      }
    });
    // TODO
  }

  @Override
  public void updateJob(Job job) {

  }
}
