package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.service.KueService;
import io.vertx.blueprint.kue.service.impl.KueServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;
import io.vertx.serviceproxy.ProxyHelper;

import static io.vertx.blueprint.kue.Kue.EB_KUE_ADDRESS;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Verticle
 *
 * @author Eric Zhao
 */
public class KueVerticle extends AbstractVerticle {

  private EventBus eventBus;
  private JsonObject config;
  private KueService kueService;

  private static RedisClient redisClient;

  @Override
  public void start(Future<Void> future) throws Exception {
    this.config = config();
    this.eventBus = vertx.eventBus();
    this.kueService = new KueServiceImpl(vertx, config);

    RedisOptions redisOptions = new RedisOptions()
      .setHost(config.getString("redis.host", "127.0.0.1"))
      .setPort(config.getInteger("redis.port", 6379));
    redisClient = RedisClient.create(vertx, redisOptions);

    System.out.println("Kue Verticle is running...");

    // register kue service
    ProxyHelper.registerService(KueService.class, vertx, kueService, EB_KUE_ADDRESS);

    future.complete();
  }

  public static RedisClient getRedis() {
    return redisClient;
  }

}
