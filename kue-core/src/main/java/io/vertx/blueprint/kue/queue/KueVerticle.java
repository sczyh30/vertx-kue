package io.vertx.blueprint.kue.queue;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Verticle
 *
 * @author Eric Zhao
 */
public class KueVerticle extends AbstractVerticle {

  public static final String EB_KUE_ADDRESS = "kue.service.internal";

  private EventBus eventBus;

  @Override
  public void start(Future<Void> future) throws Exception {
    eventBus = vertx.eventBus();
    System.out.println("Kue Verticle is running...");
    eventBus.consumer(EB_KUE_ADDRESS, msg -> {
      // TODO...
    });
    future.complete();
  }

}
