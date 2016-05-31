package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.service.KueService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ProxyHelper;

import static io.vertx.blueprint.kue.Kue.EB_KUE_ADDRESS;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Verticle
 *
 * @author Eric Zhao
 */
public class KueVerticle extends AbstractVerticle {

  private EventBus eventBus;
  private JsonObject config;
  private KueService kueService;

  @Override
  public void start(Future<Void> future) throws Exception {
    config = config();
    eventBus = vertx.eventBus();
    System.out.println("Kue Verticle is running...");

    // register kue service
    ProxyHelper.registerService(KueService.class, vertx, kueService, EB_KUE_ADDRESS);

    future.complete();
  }

}
