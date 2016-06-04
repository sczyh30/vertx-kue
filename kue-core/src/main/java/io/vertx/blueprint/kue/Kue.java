package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.KueVerticle;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

import java.util.Objects;

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
  private final RedisClient redis;

  public Kue(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.service = KueService.createProxy(vertx, EB_KUE_ADDRESS);
    this.redis = KueVerticle.getRedis();
    Job.setVertx(vertx);
  }

  /**
   * Format: vertx.kue.handler.job.handlerType.jobType
   */
  public static String getHandlerAddress(String handlerType, String jobType) {
    return "vertx.kue.handler.job." + handlerType + "." + jobType;
  }

  public static Kue createQueue(Vertx vertx, JsonObject config) {
    return new Kue(vertx, config);
  }

  // job stuff
  public Job createJob(String type, JsonObject data) {
    return new Job(type, data);
  }

  @Override
  public void process(String type, int n, Handler<AsyncResult<JsonObject>> handler) {
    service.process(type, n, handler);
  }
}
