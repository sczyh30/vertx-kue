package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.service.JobService;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;
import io.vertx.serviceproxy.ProxyHelper;


/**
 * Vert.x Blueprint - Job Queue
 * Kue Verticle
 *
 * @author Eric Zhao
 */
public class KueVerticle extends AbstractVerticle {

  private static Logger logger = LoggerFactory.getLogger(Job.class);

  public static final String EB_JOB_SERVICE_ADDRESS = "vertx.kue.service.job.internal";

  private JsonObject config;
  private JobService jobService;

  @Override
  public void start(Future<Void> future) throws Exception {
    this.config = config();
    this.jobService = JobService.create(vertx, config);
    // create redis client
    RedisClient redisClient = RedisHelper.client(vertx, config);
    redisClient.ping(pr -> { // test connection
      if (pr.succeeded()) {
        logger.info("Kue Verticle is running...");

        // register job service
        ProxyHelper.registerService(JobService.class, vertx, jobService, EB_JOB_SERVICE_ADDRESS);

        future.complete();
      } else {
        logger.error("oops!", pr.cause());
        future.fail(pr.cause());
      }
    });
  }

}
