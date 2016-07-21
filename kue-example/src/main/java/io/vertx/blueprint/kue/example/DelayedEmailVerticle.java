package io.vertx.blueprint.kue.example;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.Priority;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Example - A delayed email processing verticle
 * This example shows the delayed jobs
 *
 * @author Eric Zhao
 */
public class DelayedEmailVerticle extends AbstractVerticle {

  @Override
  public void start() throws Exception {
    // create our job queue
    Kue kue = Kue.createQueue(vertx, config());

    JsonObject data = new JsonObject()
      .put("title", "Account renewal required")
      .put("to", "qinxin@jianpo.xyz")
      .put("template", "renewal-email");

    // create a delayed email job
    Job email = kue.createJob("email", data)
      .setDelay(8888)
      .priority(Priority.HIGH)
      .onComplete(j -> System.out.println("renewal job completed"));

    kue.createJob("email", new JsonObject().put("title", "Account expired")
      .put("to", "qinxin@jianpo.xyz")
      .put("template", "expired-email"))
      .setDelay(26666)
      .priority(Priority.HIGH)
      .save() // save job
      .compose(c -> email.save()) // save another job
      .setHandler(sr -> {
        if (sr.succeeded()) {
          // process emails
          kue.processBlocking("email", 10, job -> {
            vertx.setTimer(2016, l -> job.done()); // cost 2s to process
          });
        } else {
          sr.cause().printStackTrace();
        }
      });
  }

}
