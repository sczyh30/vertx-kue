package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.util.RedisHelper;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;


/**
 * Vert.x Blueprint - Job Queue
 * Kue Worker Verticle
 *
 * @author Eric Zhao
 */
public class KueWorker extends AbstractVerticle { //TODO: UNFINISHED

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
    this.getJobFromBackend(jr -> {
      if (jr.succeeded()) {
        this.job = jr.result();
        process();
      } else {
        jr.cause().printStackTrace(); // fast fail
      }
    });
  }

  @Override
  public void stop() throws Exception {
    // stop hook
  }

  private void fail(Throwable ex) {
    // TODO/UNFINISHED: fail
    job.failedAttempt(ex, r -> {
      if (r.failed()) {
        // error
      } else {
        Job res = r.result();
        if (res.hasAttempts()) {
          this.emitJobEvent("failed_attempt", job, new JsonObject().put("errorMsg", ex.getMessage())); // shouldn't include err?
        } else {
          //
        }
        // fn?
      }
    });
  }

  private void process() {

    job.active(r -> {
      // TODO: emit event
      this.emitJobEvent("start", this.job, null);
      jobHandler.handle(Future.succeededFuture(job));
      createDoneCallback().handle(Future.succeededFuture(job.getResult())); // should not do this, refactor
    });
  }

  /**
   * Redis zpop atomic primitive with transaction
   *
   * @param key redis key
   * @return the async result of zpop
   */
  private Future<String> zpop(String key) {
    Future<String> future = Future.future();
    client.transaction()
      .multi(null)
      .zrange(key, 0, 0, null)
      .zremrangebyrank(key, 0, 0, null)
      .exec(r -> {
        if (r.succeeded()) {
          JsonArray res = r.result();
          if (res.getJsonArray(0).size() == 0) // empty set
            future.fail(new IllegalStateException("Empty zpop set"));
          else
            future.complete(RedisHelper.stripFIFO(res.getJsonArray(0).getString(0)));
        } else {
          future.fail(r.cause());
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
      } else {
        this.zpop(RedisHelper.getKey("jobs:" + this.type + ":INACTIVE"))
          .setHandler(r -> {
            if (r.succeeded()) {
              String _id = r.result();
              Job.getJob(Long.parseLong(_id), this.type)
                .setHandler(handler);
            } else {
              // TODO: maybe should idle
              r.cause().printStackTrace();
            }
          });
      }
    });
  }

  private Handler<AsyncResult<JsonObject>> createDoneCallback() { //TODO
    return r0 -> {
      if (this.job == null) {
        // maybe should warn
        return;
      }
      if (r0.failed()) {
        // TODO: FAIL
        return;
      }
      job.getJobMetrics().setDuration(System.currentTimeMillis() - job.getJobMetrics().getStartedAt());
      JsonObject result = r0.result();
      if (result != null) {
        job.setResult(result)
          .set("result", result.encodePrettily());
      }

      job.complete(e -> {
        eventBus.send(Kue.getHandlerAddress("complete", type), job.toJson());
      });


    };
  }

  private void emitJobEvent(String event, Job job, JsonObject other) {
    if (other == null)
      eventBus.send(Kue.getHandlerAddress("job_" + event, job.getType()), job.toJson());
    else {
      JsonObject json = other.copy().put("job", job.toJson());
      eventBus.send(Kue.getHandlerAddress("job_" + event, job.getType()), json);
    }
  }

}
