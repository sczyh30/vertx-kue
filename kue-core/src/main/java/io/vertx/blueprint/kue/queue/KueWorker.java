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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


/**
 * Vert.x Blueprint - Job Queue
 * Kue Worker Verticle
 *
 * @author Eric Zhao
 */
public class KueWorker extends AbstractVerticle { //TODO: UNFINISHED

  private static Logger logger = LoggerFactory.getLogger(KueWorker.class);

  private RedisClient client; // every worker use different client
  private EventBus eventBus;
  private Job job;
  private String type;
  private Handler<AsyncResult<Job>> jobHandler;

  public KueWorker(String type, Handler<AsyncResult<Job>> jobHandler) {
    this.type = type;
    this.jobHandler = jobHandler;
  }

  @Override
  public void start() throws Exception {
    this.eventBus = vertx.eventBus();
    this.client = RedisHelper.client(vertx, config());

    this.getJobFromBackend().setHandler(jr -> {
      if (jr.succeeded()) {
        if (jr.result().isPresent()) {
          this.job = jr.result().get();
          process();
        } else {
          // NOT PRESENT?
        }
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
      }
    });
  }

  private void process() {
    job.active().setHandler(r -> {
      if (r.succeeded()) {
        Job j = r.result();
        this.emitJobEvent("start", j, null);
        jobHandler.handle(Future.succeededFuture(j));
        createDoneCallback().handle(Future.succeededFuture(j.getResult())); // should not do this, refactor
      }
    });
  }

  /**
   * Redis zpop atomic primitive with transaction
   *
   * @param key redis key
   * @return the async result of zpop
   */
  private Future<Long> zpop(String key) {
    Future<Long> future = Future.future();
    client.transaction()
      .multi(_failure())
      .zrange(key, 0, 0, _failure())
      .zremrangebyrank(key, 0, 0, _failure())
      .exec(r -> {
        if (r.succeeded()) {
          JsonArray res = r.result();
          if (res.getJsonArray(0).size() == 0) // empty set
            future.fail(new IllegalStateException("Empty zpop set"));
          else {
            try {
              future.complete(Long.parseLong(RedisHelper.stripFIFO(
                res.getJsonArray(0).getString(0))));
            } catch (Exception ex) {
              future.fail(ex);
            }
          }
        } else {
          future.fail(r.cause());
        }
      });
    return future;
  }

  private Future<Optional<Job>> getJobFromBackend() {
    Future<Optional<Job>> future = Future.future();
    client.blpop(RedisHelper.getKey(this.type + ":jobs"), 0, r1 -> {
      if (r1.failed()) {
        client.lpush(RedisHelper.getKey(this.type + ":jobs"), "1", r2 -> {
          if (r2.failed())
            future.fail(r2.cause());
        });
      } else {
        this.zpop(RedisHelper.getKey("jobs:" + this.type + ":INACTIVE"))
          .compose(r -> Job.getJob(r, this.type))
          .setHandler(r -> {
            if (r.succeeded())
              future.complete(r.result());
            else
              future.fail(r.cause());
          });
      }
    });
    return future;
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

      job.complete().setHandler(r -> {
        if (r.succeeded()) {
          Job j = r.result();
          System.out.println("KueWorker::Job::complete");
          // eventBus.send(Kue.getHandlerAddress("complete", job.getType()), job.toJson());
          this.emitJobEvent("complete", j, null);
        }
      });


    };
  }

  /**
   * Emit job event
   *
   * @param event event type
   * @param job   corresponding job
   * @param other extra data
   */
  private void emitJobEvent(String event, Job job, JsonObject other) {
    eventBus.send(Kue.workerAddress("job_" + event), job.toJson());
    // emit other
  }

  private static <T> Handler<AsyncResult<T>> _failure() {
    return r -> {
      if (r.failed())
        r.cause().printStackTrace();
    };
  }

}