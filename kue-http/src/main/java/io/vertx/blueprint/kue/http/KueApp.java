package io.vertx.blueprint.kue.http;

import io.vertx.core.Vertx;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Web Application LAUNCHER
 *
 * @author Eric Zhao
 */
public final class KueApp {

  private KueApp() {
  }

  public static void start() {

  }

  public static void start(int port) {
    Vertx vertx = Vertx.vertx();
    KueRestVerticle verticle = new KueRestVerticle();

  }
}
