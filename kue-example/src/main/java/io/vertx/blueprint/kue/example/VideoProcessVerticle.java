package io.vertx.blueprint.kue.example;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.Priority;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Example - A video conversion process verticle
 *
 * @author Eric Zhao
 */
public class VideoProcessVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // create our job queue
    Kue kue = Kue.createQueue(vertx, config());

    System.out.println("Creating job: video conversion");

    JsonObject data = new JsonObject()
      .put("title", "converting video to avi")
      .put("user", 1)
      .put("frames", 200);

    // create an video conversion job
    Job job0 = kue.createJob("video conversion", data)
      .priority(Priority.NORMAL);

    // save the job
    job0.save().setHandler(r0 -> {
      if (r0.succeeded()) {
        // process logic start (3 at a time)
        kue.process("video conversion", 3, job -> {
          int frames = job.getData().getInteger("frames", 100);
          next(0, frames, job);
        });
        // process logic end
      } else {
        r0.cause().printStackTrace();
      }
    });
  }

  // pretend we are doing some work
  private void next(int i, int frames, Job job) {
    vertx.setTimer(666, r -> {
      job.progress(i, frames);
      if (i == frames)
        job.done();
      else
        next(i + 1, frames, job);
    });
  }
}
