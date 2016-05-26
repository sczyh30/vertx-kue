package io.vertx.blueprint.kue.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

/**
 * Vert.x Blueprint - Job Queue
 * Example - A simple job process verticle
 *
 * @author Eric Zhao
 */
public class ExampleProcessVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> future) throws Exception {

    future.complete();
  }
}
