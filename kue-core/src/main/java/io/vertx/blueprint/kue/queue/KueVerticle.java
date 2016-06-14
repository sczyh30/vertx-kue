package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.service.JobService;
import io.vertx.blueprint.kue.service.KueService;
import io.vertx.blueprint.kue.service.impl.KueServiceImpl;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.serviceproxy.ProxyHelper;


/**
 * Vert.x Blueprint - Job Queue
 * Kue Verticle
 *
 * @author Eric Zhao
 */
public class KueVerticle extends AbstractVerticle {

  public static final String EB_KUE_SERVICE_ADDRESS = "vertx.kue.service.internal";
  public static final String EB_JOB_SERVICE_ADDRESS = "vertx.kue.service.job.internal";

  private EventBus eventBus;
  private JsonObject config;
  private KueService kueService;
  private JobService jobService;

  @Override
  public void start(Future<Void> future) throws Exception {
    this.config = config();
    this.eventBus = vertx.eventBus();
    this.kueService = KueService.create(vertx, config);
    this.jobService = JobService.create(vertx, config);
    // create redis client
    RedisClient redisClient = RedisClient.create(vertx, RedisHelper.options(config));
    redisClient.ping(pr -> {
      if (pr.succeeded()) {
        System.out.println("Kue Verticle is running...");

        // register kue service and job service
        ProxyHelper.registerService(KueService.class, vertx, kueService, EB_KUE_SERVICE_ADDRESS);
        ProxyHelper.registerService(JobService.class, vertx, jobService, EB_JOB_SERVICE_ADDRESS);

        future.complete();
      } else {
        System.err.println("[ERROR] Redis service is noe running!");
        future.fail(pr.cause());
      }
    });
  }

}
