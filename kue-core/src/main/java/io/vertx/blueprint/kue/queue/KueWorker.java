package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.util.RedisHelper;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.RedisClient;

import java.util.Optional;


/**
 * The verticle for processing Kue tasks.
 *
 * @author Eric Zhao
 */
public class KueWorker extends AbstractVerticle {

  private static Logger logger = LoggerFactory.getLogger(Job.class);

  private final Kue kue;
  private RedisClient client; // Every worker use different clients.
  private EventBus eventBus;
  private Job job;
  private final String type;
  private final Handler<Job> jobHandler;

  private MessageConsumer doneConsumer; // Preserve for unregister the consumer.
  private MessageConsumer doneFailConsumer;

  public KueWorker(String type, Handler<Job> jobHandler, Kue kue) {
    this.type = type;
    this.jobHandler = jobHandler;
    this.kue = kue;
  }

  @Override
  public void start() throws Exception {
    this.eventBus = vertx.eventBus();
    this.client = RedisHelper.client(vertx, config());

    prepareAndStart();
  }

  /**
   * Prepare job and start processing procedure.
   */
  private void prepareAndStart() {
    cleanup();
    this.getJobFromBackend().setHandler(jr -> {
      if (jr.succeeded()) {
        if (jr.result().isPresent()) {
          this.job = jr.result().get();
          process();
        } else {
          this.emitJobEvent("error", null, new JsonObject().put("message", "job_not_exist"));
          throw new IllegalStateException("job not exist");
        }
      } else {
        this.emitJobEvent("error", null, new JsonObject().put("message", jr.cause().getMessage()));
        jr.cause().printStackTrace();
      }
    });
  }

  /**
   * Process the job.
   */
  private void process() {
    long curTime = System.currentTimeMillis();
    this.job.setStarted_at(curTime)
      .set("started_at", String.valueOf(curTime))
      .compose(Job::active)
      .setHandler(r -> {
        if (r.succeeded()) {
          Job j = r.result();
          // emit start event
          this.emitJobEvent("start", j, null);

          logger.debug("KueWorker::process[instance:Verticle(" + this.deploymentID() + ")] with job " + job.getId());
          // process logic invocation
          try {
            jobHandler.handle(j);
          } catch (Exception ex) {
            j.done(ex);
          }
          // subscribe the job done event

          doneConsumer = eventBus.consumer(Kue.workerAddress("done", j), msg -> {
            createDoneCallback(j).handle(Future.succeededFuture(
              ((JsonObject) msg.body()).getJsonObject("result")));
          });
          doneFailConsumer = eventBus.consumer(Kue.workerAddress("done_fail", j), msg -> {
            createDoneCallback(j).handle(Future.failedFuture(
              (String) msg.body()));
          });
        } else {
          this.emitJobEvent("error", this.job, new JsonObject().put("message", r.cause().getMessage()));
          r.cause().printStackTrace();
        }
      });
  }

  private void cleanup() {
    Optional.ofNullable(doneConsumer).ifPresent(MessageConsumer::unregister);
    Optional.ofNullable(doneFailConsumer).ifPresent(MessageConsumer::unregister);
    this.job = null;
  }

  private void error(Throwable ex, Job job) {
    JsonObject err = new JsonObject().put("message", ex.getMessage())
      .put("id", job.getId());
    eventBus.send(Kue.workerAddress("error"), err);
  }

  private void fail(Throwable ex) {
    job.failedAttempt(ex).setHandler(r -> {
      if (r.failed()) {
        this.error(r.cause(), job);
      } else {
        Job res = r.result();
        if (res.hasAttempts()) {
          this.emitJobEvent("failed_attempt", job, new JsonObject().put("message", ex.getMessage())); // shouldn't include err?
        } else {
          this.emitJobEvent("failed", job, new JsonObject().put("message", ex.getMessage()));
        }
        prepareAndStart();
      }
    });
  }

  /**
   * Redis zpop atomic primitive with transaction.
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
   * Get a job from Redis backend by priority.
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
          .compose(kue::getJob)
          .setHandler(r -> {
            if (r.succeeded()) {
              future.complete(r.result());
            } else
              future.fail(r.cause());
          });
      }
    });
    return future;
  }

  private Handler<AsyncResult<JsonObject>> createDoneCallback(Job job) {
    return r0 -> {
      if (job == null) {
        // maybe should warn
        return;
      }
      if (r0.failed()) {
        this.fail(r0.cause());
        return;
      }
      long dur = System.currentTimeMillis() - job.getStarted_at();
      job.setDuration(dur)
        .set("duration", String.valueOf(dur));
      JsonObject result = r0.result();
      if (result != null) {
        job.setResult(result)
          .set("result", result.encodePrettily());
      }

      job.complete().setHandler(r -> {
        if (r.succeeded()) {
          Job j = r.result();
          if (j.isRemoveOnComplete()) {
            j.remove();
          }
          this.emitJobEvent("complete", j, null);

          this.prepareAndStart(); // prepare for next job
        }
      });
    };
  }

  @Override
  public void stop() throws Exception {
    // stop hook
    cleanup();
  }

  /**
   * Emit job event.
   *
   * @param event event type
   * @param job   corresponding job
   * @param extra extra data
   */
  private void emitJobEvent(String event, Job job, JsonObject extra) {
    JsonObject data = new JsonObject().put("extra", extra);
    if (job != null) {
      data.put("job", job.toJson());
    }
    eventBus.send(Kue.workerAddress("job_" + event), data);
    switch (event) {
      case "failed":
      case "failed_attempt":
        eventBus.send(Kue.getCertainJobAddress(event, job), data);
        break;
      case "error":
        eventBus.send(Kue.workerAddress("error"), data);
        break;
      default:
        eventBus.send(Kue.getCertainJobAddress(event, job), job.toJson());
    }
  }

  private static <T> Handler<AsyncResult<T>> _failure() {
    return r -> {
      if (r.failed())
        r.cause().printStackTrace();
    };
  }

}
