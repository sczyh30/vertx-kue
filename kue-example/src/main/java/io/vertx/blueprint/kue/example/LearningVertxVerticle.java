package io.vertx.blueprint.kue.example;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.Priority;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Example - A learning Vert.x process verticle!
 * This example shows the job events
 *
 * @author Eric Zhao
 */
public class LearningVertxVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // create our job queue
    Kue kue = Kue.createQueue(vertx, config());

    // consume queue error
    kue.on("error", message ->
      System.out.println("[Global Error] " + message.body()));

    JsonObject data = new JsonObject()
      .put("title", "Learning Vert.x")
      .put("content", "core");

    // we are going to learn Vert.x, so create a job!
    Job j = kue.createJob("learn vertx", data)
      .priority(Priority.HIGH)
      .onComplete(r -> { // on complete handler
        System.out.println("Feeling: " + r.getResult().getString("feeling", "none"));
      }).onFailure(r -> { // on failure handler
        System.out.println("eee...so difficult...");
      }).onProgress(r -> { // on progress modifying handler
        System.out.println("I love this! My progress => " + r);
      });

    // save the job
    j.save().setHandler(r0 -> {
      if (r0.succeeded()) {
        // start learning!
        kue.processBlocking("learn vertx", 1, job -> {
          job.progress(10, 100);
          // aha...spend 3 seconds to learn!
          vertx.setTimer(3000, r1 -> {
            job.setResult(new JsonObject().put("feeling", "amazing and wonderful!")) // set a result to the job
              .done(); // finish learning!
          });
        });
      } else {
        System.err.println("Wow, something happened: " + r0.cause().getMessage());
      }
    });
  }

}
