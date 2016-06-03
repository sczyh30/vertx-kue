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
      .onComplete(System.out::println)
      .onFailure(Throwable::printStackTrace);

    kue.saveJob(job0);

    kue.process("video", 1, res -> {
      if (res.succeeded()) {
        try {
          Job job = new Job(res.result());
          Thread.sleep(2000);
          job.progress(100, 100);
          System.out.println("Video id: " + job.getId());
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      } else {
        res.cause().printStackTrace();
      }
    });
  }
}
