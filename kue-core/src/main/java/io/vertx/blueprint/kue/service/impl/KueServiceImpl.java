package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.KueWorker;
import io.vertx.blueprint.kue.service.KueService;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Service Impl
 *
 * @author Eric Zhao
 */
public final class KueServiceImpl implements KueService {

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

  /**
   * Process a job with a KueWorker Verticle
   *
   * @param type     job type
   * @param n        job process times
   * @param handler  job process handler
   * @param isWorker the flag indicates if the handler do blocking procedure
   */
  private void processInternal(String type, int n, Handler<AsyncResult<Job>> handler, boolean isWorker) {
    if (n <= 0) {
      throw new IllegalStateException("The process times must be positive");
    }
    while (n-- > 0) {
      KueWorker worker = new KueWorker(type, handler);
      vertx.deployVerticle(worker, new DeploymentOptions().setWorker(isWorker), r0 -> {
        if (r0.succeeded()) {
          this.on("job_complete", msg -> {
            long dur = new Job((JsonObject) msg.body()).getJobMetrics().getDuration();
            redis.incrby(RedisHelper.getKey("stats:work-time"), dur, r1 -> {
              if (r1.failed())
                r1.cause().printStackTrace();
            });
          });
        }
      });
    }
  }

  private <R> void on(String eventType, Handler<Message<R>> handler) {
    vertx.eventBus().consumer(Kue.workerAddress(eventType), handler);
  }

  @Override
  public void process(String type, int n, Handler<AsyncResult<Job>> handler) {
    processInternal(type, n, handler, false);
  }

  @Override
  public void processBlocking(String type, int n, Handler<AsyncResult<Job>> handler) {
    processInternal(type, n, handler, true);
  }
}
