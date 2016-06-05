package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.util.functional.Either;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Worker Verticle
 *
 * @author Eric Zhao
 */
public class KueWorker extends AbstractVerticle {

  private Job job;

  @Override
  public void start() throws Exception {

  }

  @Override
  public void stop() throws Exception {

  }

  private Handler<Either<Throwable, JsonObject>> createDoneCallback(Job job, Handler<Job> handler) {
    return either -> {
      if (this.job == null || this.job.getId() != job.getId()) {
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

}
