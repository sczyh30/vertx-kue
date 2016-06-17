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

  private final Kue kue;
  private RedisClient client; // every worker use different client
  private EventBus eventBus;
  private Job job;
  private final String type;
  private final Handler<AsyncResult<Job>> jobHandler;

  public KueWorker(String type, Handler<AsyncResult<Job>> jobHandler, Kue kue) {
    this.type = type;
    this.jobHandler = jobHandler;
    this.kue = kue;
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    this.eventBus = vertx.eventBus();
    this.client = RedisHelper.client(vertx, config());

    this.getJobFromBackend().setHandler(jr -> {
      if (jr.succeeded()) {
        startFuture.complete();
        if (jr.result().isPresent()) {
          this.job = jr.result().get();
          process();
        } else {
          // NOT PRESENT?
        }
      } else {
        startFuture.fail(jr.cause());
      }
    });
  }

  @Override
  public void stop() throws Exception {
    // stop hook
  }

  private void fail(Throwable ex) {
    // TODO/UNFINISHED: fail
    job.failedAttempt(ex).setHandler(r -> {
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
    this.job.active().setHandler(r -> {
      if (r.succeeded()) {
        Job j = r.result();
        this.emitJobEvent("start", j, null);
        jobHandler.handle(Future.succeededFuture(j));
        eventBus.consumer(Kue.workerAddress("done", j), msg -> {
          createDoneCallback(j).handle(Future.succeededFuture(
            ((JsonObject) msg.body()).getJsonObject("result")));
        });
      } else {
        r.cause().printStackTrace();
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

  /**
   * Get a job from Redis backend by priority
   *
   * @return async result of job
   */
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
          .compose(r -> kue.getJob(r))
          .setHandler(r -> {
            if (r.succeeded()) {
              future.complete(r.result());
            }
            else
              future.fail(r.cause());
          });
      }
    });
    return future;
  }

  private Handler<AsyncResult<JsonObject>> createDoneCallback(Job job) { //TODO
    return r0 -> {
      if (job == null) {
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
    eventBus.send(Kue.workerAddress("job_" + event, job), job.toJson());
    eventBus.send(Kue.getCertainJobAddress(event, job), job.toJson());
    // emit other
  }

  private static <T> Handler<AsyncResult<T>> _failure() {
    return r -> {
      if (r.failed())
        r.cause().printStackTrace();
    };
  }

}
