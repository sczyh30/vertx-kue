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

  private final Vertx vertx = Vertx.vertx();
  private final KueVerticle kueVerticle;
  private final JsonObject deployConfig;

  private Kue() {
    this(new JsonObject());
  }

  private Kue(JsonObject deployConfig) {
    this.deployConfig = deployConfig;
    this.kueVerticle = new KueVerticle();
    deploy();
  }

  public static Kue createQueue() {
    return new Kue();
  }

  public static Kue createQueue(JsonObject deployConfig) {
    return new Kue(deployConfig);
  }

  private void deploy() {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(kueVerticle, new DeploymentOptions(deployConfig), res -> {
      if (res.succeeded()) {
        // TODO...
      } else {
        res.cause().printStackTrace();
      }
    });
  }

  public Job create(String type, JsonObject data) {
    return new Job(type, data);
  }



}
