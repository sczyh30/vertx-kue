package io.vertx.blueprint.kue.http;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * Vert.x Blueprint - Job Queue
 * Kue REST API Verticle
 *
 * @author Eric Zhao
 */
public class KueHttpVerticle extends AbstractVerticle {

  private static final String HOST = "0.0.0.0";
  private static final int PORT = 3000;

  // Kue REST API
  public static final String KUE_API_JOB_SEARCH = "/job/search/:q";
  public static final String KUE_API_STATS = "/stats";
  public static final String KUE_API_GET_JOB = "/job/:id";
  public static final String KUE_API_CREATE_JOB = "/job";
  public static final String KUE_API_DELETE_JOB = "/job/:id";
  public static final String KUE_API_GET_JOB_LOG = "/job/:id/log";

  private Kue kue;

  @Override
  public void start(Future<Void> future) throws Exception {

    kue = Kue.createQueue(vertx, config());

    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    // routes
    router.get(KUE_API_JOB_SEARCH).handler(this::handleSearchJob);
    router.get(KUE_API_STATS).handler(this::handleStats);
    router.put(KUE_API_CREATE_JOB).handler(this::handleCreateJob);
    router.get(KUE_API_GET_JOB).handler(this::handleGetJob);
    router.delete(KUE_API_DELETE_JOB).handler(this::handleDeleteJob);
    // create server
    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(config().getInteger("http.port", PORT),
        config().getString("http.address", HOST), result -> {
          if (result.succeeded()) {
            System.out.println("Kue http server is running on " + PORT + " port...");
            future.complete();
          } else {
            future.fail(result.cause());
          }
        });
  }

  private void handleSearchJob(RoutingContext context) { // TODO: delayed to implement
    context.response().setStatusCode(501).end(); // 501 Not Implemented
  }

  private void handleStats(RoutingContext context) {

  }

  private void handleCreateJob(RoutingContext context) {
    try {
      Job job = new Job(new JsonObject(context.getBodyAsString())); // TODO: support json array create
      job.save().setHandler(r -> {
        if (r.succeeded()) {
          String result = new JsonObject().put("message", "job created")
            .put("id", r.result().getId())
            .encodePrettily();
          context.response().setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end(result);
        } else {
          internalError(context.response(), r.cause());
        }
      });
    } catch (DecodeException e) {
      badRequest(context.response());
    }
  }

  private void handleGetJob(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      System.out.println("id -> " + id);
      Job.getJob(id).setHandler(r -> {
        if (r.succeeded()) {
          if (r.result().isPresent()) {
            context.response()
              .putHeader("content-type", "application/json")
              .end(r.result().toString());
          } else {
            notFound(context.response());
          }
        } else {
          internalError(context.response(), r.cause());
        }
      });
    } catch (Exception e) {
      badRequest(context.response());
    }
  }

  private void handleDeleteJob(RoutingContext context) {

  }

  private void sendError(int statusCode, HttpServerResponse response) {
    response.setStatusCode(statusCode).end();
  }

  private void badRequest(HttpServerResponse response) {
    response.setStatusCode(400).end();
  }

  private void notFound(HttpServerResponse response) {
    response.setStatusCode(404).end();
  }

  private void internalError(HttpServerResponse response) {
    response.setStatusCode(503).end();
  }

  private void internalError(HttpServerResponse response, Throwable ex) {
    response.setStatusCode(503)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
  }
}
