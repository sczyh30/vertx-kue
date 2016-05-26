package io.vertx.blueprint.kue.http;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Vert.x Blueprint - Job Queue
 * Kue REST API Verticle
 *
 * @author Eric Zhao
 */
public class KueHttpVerticle extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

  }
}
