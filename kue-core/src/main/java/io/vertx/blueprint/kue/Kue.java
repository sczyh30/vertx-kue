package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.KueVerticle;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 *
 * @author Eric Zhao
 */
public class Kue {

  private final Vertx vertx;

  public Kue(Vertx vertx) {
    this.vertx = vertx;
  }

  public static Kue createQueue(Vertx vertx) {
    return new Kue(vertx);
  }

  public Job createJob(String type, JsonObject data) {
    return new Job(type, data);
  }

}
