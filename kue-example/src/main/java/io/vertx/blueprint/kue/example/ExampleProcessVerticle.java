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

    Job job0 = kue.createJob("video", new JsonObject().put("id", 3009).put("intro", "great movie").put("title", "Video maker"))
      .priority(Priority.NORMAL)
      .onComplete(e -> {
        System.out.println("Video result: " + e.getResult().encodePrettily());
        System.out.println("Haha");
      });

    Job job1 = kue.createJob("video", new JsonObject().put("type", "VR").put("number", 616161).put("title", "Video maker"))
      .priority(Priority.NORMAL)
      .onComplete(e -> {
        System.out.println("Our VR trip finish!");
        System.out.println("=> " + e.getResult().encodePrettily());
      });

    job0.save()
      .compose(x -> job1.save())
      .setHandler(r0 -> {
      if (r0.succeeded()) {
        // process logic start
        kue.process("video", 2, r -> {
          if (r.succeeded()) {
            Job job = r.result();
            job.setResult(new JsonObject().put("res", "success"))
              .done()
              .progress(100, 100);
            System.out.println("Video id: " + job.getId());
          }
        });
        // process logic end
      } else {
        r0.cause().printStackTrace();
      }
    });
  }

}
