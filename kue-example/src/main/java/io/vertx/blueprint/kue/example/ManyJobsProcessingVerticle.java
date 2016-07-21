package io.vertx.blueprint.kue.example;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.Priority;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Example - illustrates many jobs generating and processing
 *
 * @author Eric Zhao
 */
public class ManyJobsProcessingVerticle extends AbstractVerticle {

  private Kue kue;

  @Override
  public void start() throws Exception {
    // create our job queue
    kue = Kue.createQueue(vertx, config());

    create().setHandler(r0 -> {
      if (r0.succeeded()) {
        // process logic start (6 at a time)
        kue.process("video conversion", 6, job -> {
          int frames = job.getData().getInteger("frames", 100);
          next(0, frames, job);
        });
        // process logic end
      } else {
        r0.cause().printStackTrace();
      }
    });
  }

  private Future<Job> create() {
    JsonObject data = new JsonObject()
      .put("title", "converting video to avi")
      .put("user", 1)
      .put("frames", 200);

    // create an video conversion job
    Job job = kue.createJob("video conversion", data)
      .priority(Priority.NORMAL);
    vertx.setTimer(2000, l -> create()); // may cause issue
    return job.save();
  }

  // pretend we are doing some work
  private void next(int i, int frames, Job job) {
    vertx.setTimer(26, r -> {
      job.progress(i, frames);
      if (i == frames)
        job.done();
      else
        next(i + 1, frames, job);
    });
  }

}
