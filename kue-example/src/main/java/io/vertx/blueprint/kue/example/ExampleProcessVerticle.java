package io.vertx.blueprint.kue.example;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.Priority;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Example - A simple job process verticle
 *
 * @author Eric Zhao
 */
public class ExampleProcessVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // must first create kue
    Kue kue = Kue.createQueue(vertx, config());

    Job job0 = kue.createJob("video", new JsonObject().put("id", 3001))
      .priority(Priority.HIGH)
      .onComplete(System.out::println);
    job0.save();

    kue.process("video", 1, res -> {
      if (res.succeeded()) {
          Job job = new Job(res.result());
        // consume 2 seconds
        vertx.setTimer(2000, l -> {
          job.progress(100, 100);
          System.out.println("Video id: " + job.getId());
        });
      } else {
        res.cause().printStackTrace();
      }
    });
  }
}
