package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.blueprint.kue.util.functional.Either;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisTransaction;


/**
 * Vert.x Blueprint - Job Queue
 * Kue Worker Verticle
 *
 * @author Eric Zhao
 */
public class KueWorker extends AbstractVerticle {

  private RedisClient client; // every worker use a client?
  private EventBus eventBus;
  private Job job;
  private String type;
  private Handler<AsyncResult<Job>> jobHandler;

  public KueWorker(String type, Handler<AsyncResult<Job>> jobHandler, RedisClient client) {
    this.client = client;
    this.type = type;
    this.jobHandler = jobHandler;
  }

  @Override
  public void start() throws Exception {
    this.eventBus = vertx.eventBus();

    this.getJobFromBackend(job -> {

    });
  }

  @Override
  public void stop() throws Exception {

  }

  private void process() {
    // TODO: get job
    Handler<Either<Throwable, JsonObject>> doneCallback = createDoneCallback();

    job.active(r -> {
      // TODO: emit event
      this.emitJobEvent("start", this.job);
      jobHandler.handle(Future.succeededFuture(job));
    });
  }

  private Future<Void> zpop(String key) {
    Future<Void> future = Future.future();
    RedisTransaction multi = client.transaction()
      .watch(key, null)
      .zrange(key, 0, 0, r -> {
        if (r.succeeded()) {
          // TODO
        }
      });
    return future;
  }

  private void getJobFromBackend(Handler<AsyncResult<Job>> handler) {
    client.blpop(RedisHelper.getKey(this.type + ":jobs"), 0, r1 -> {
      if (r1.failed()) {
        client.lpush(RedisHelper.getKey(this.type + ":jobs"), "1", r2 -> {
          if (r2.failed())
            handler.handle(Future.failedFuture(r2.cause()));
        });
        // failureHandler.handle(r1.cause());
      } else {
        // TODO: zpop

        // TODO: get job
      }
    });
  }

  private Handler<Either<Throwable, JsonObject>> createDoneCallback() {
    return either -> {
      if (this.job == null) {
        // maybe should warn
        return;
      }
      if (either.isLeft()) {
        // TODO: FAIL
        return;
      }
      job.getJobMetrics().setDuration(System.currentTimeMillis() - job.getJobMetrics().getStartedAt());
      job.setResult(either.right())
        .update()
        .compose(r1 -> r1.complete(r2 -> {
        })); // TODO


    };
  }

  private void emitJobEvent(String event, Job job) {
    eventBus.send(Kue.getHandlerAddress(event, job.getType()), job.toJson());
  }

}
