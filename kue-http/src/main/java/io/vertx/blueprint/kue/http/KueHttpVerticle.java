package io.vertx.blueprint.kue.http;

import io.vertx.blueprint.kue.Kue;
import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.JadeTemplateEngine;

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
  public static final String KUE_API_GET_INACTIVE = "/inactive/:id";
  public static final String KUE_API_GET_JOB_TYPES = "/job/types";
  public static final String KUE_API_JOB_RANGE = "\\/jobs\\/([0-9]*)\\.\\.([0-9]*)"; //"\\/jobs\\/([^\\/]+)\\/([^\\/]+)\\/([^\\/]?)";
  public static final String KUE_API_JOB_RANGE_ORDER = "/jobs/:from..:to/:order"; // TODO
  public static final String KUE_API_CREATE_JOB = "/job";
  public static final String KUE_API_UPDATE_JOB_STATE = "/job/:id/state/:state";
  public static final String KUE_API_DELETE_JOB = "/job/:id";
  public static final String KUE_API_GET_JOB_LOG = "/job/:id/log";

  // Kue UI
  public static final String KUE_UI_ROOT = "/";
  public static final String KUE_UI_ACTIVE = "/active";
  public static final String KUE_UI_INACTIVE = "/inactive";
  public static final String KUE_UI_FAILED = "/failed";
  public static final String KUE_UI_COMPLETE = "/complete";
  public static final String KUE_UI_DELAYED = "/delayed";

  private Kue kue;

  @Override
  public void start(Future<Void> future) throws Exception {
    // init kue
    kue = Kue.createQueue(vertx, config());

    final Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    // REST API routes
    router.get(KUE_API_JOB_SEARCH).handler(this::apiSearchJob);
    router.get(KUE_API_STATS).handler(this::apiStats);
    router.getWithRegex(KUE_API_JOB_RANGE).handler(this::apiJobRange); // \/jobs\/([0-9]*)\.\.([0-9]*)
    router.put(KUE_API_CREATE_JOB).handler(this::apiCreateJob);
    router.put(KUE_API_UPDATE_JOB_STATE).handler(this::apiCreateJob);
    router.get(KUE_API_GET_JOB).handler(this::apiGetJob);
    router.get(KUE_API_GET_JOB_LOG).handler(this::apiFetchLog);
    router.delete(KUE_API_DELETE_JOB).handler(this::apiDeleteJob);
    // UI routes
    router.route(KUE_UI_ROOT).handler(this::handleUIRoot);
    router.route(KUE_UI_ACTIVE).handler(this::handleUIActive);
    // static resources route
    router.route().handler(StaticHandler.create());
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

  private void render(RoutingContext context, String state) {
    final String uiPath = "webroot/views/job/list.jade";
    final JadeTemplateEngine engine = JadeTemplateEngine.create();
    String title = config().getString("title", "Vert.x Kue");
    kue.getAllTypes()
      .setHandler(resultHandler(context, r -> {
        context.put("state", state)
          .put("types", new JsonArray(r))
          .put("title", title);
        engine.render(context, uiPath, res -> {
          if (res.succeeded()) {
            context.response().end(res.result());
          } else {
            context.fail(res.cause());
          }
        });
      }));
  }

  private void handleUIRoot(RoutingContext context) {
    handleUIActive(context); // by default active
  }

  private void handleUIActive(RoutingContext context) {
    render(context, "active");
  }

  private void apiSearchJob(RoutingContext context) { // TODO: delayed to implement
    context.response().setStatusCode(501).end(); // 501 Not Implemented
  }

  private void apiStats(RoutingContext context) {
    JsonObject stats = new JsonObject();
    kue.getWorkTime().compose(r -> {
      stats.put("workTime", r);
      return kue.getIdsByState(JobState.INACTIVE);
    }).compose(r -> {
      stats.put("inactiveCount", r.size());
      return kue.getIdsByState(JobState.COMPLETE);
    }).compose(r -> {
      stats.put("completeCount", r.size());
      return kue.getIdsByState(JobState.ACTIVE);
    }).compose(r -> {
      stats.put("activeCount", r.size());
      return kue.getIdsByState(JobState.FAILED);
    }).compose(r -> {
      stats.put("failedCount", r.size());
      return kue.getIdsByState(JobState.DELAYED);
    }).map(r -> {
      stats.put("delayedCount", r.size());
      return stats;
    }).setHandler(resultHandler(context, r -> {
      context.response()
        .putHeader("content-type", "application/json")
        .end(r.encodePrettily());
    }));
  }

  private void apiCreateJob(RoutingContext context) {
    try {
      Job job = new Job(new JsonObject(context.getBodyAsString())); // TODO: support json array create
      job.save().setHandler(resultHandler(context, r -> {
        String result = new JsonObject().put("message", "job created")
          .put("id", r.getId())
          .encodePrettily();
        context.response().setStatusCode(201)
          .putHeader("content-type", "application/json")
          .end(result);
      }));
    } catch (DecodeException e) {
      badRequest(context);
    }
  }

  private void apiUpdateJobState(RoutingContext context) {
    try {
      context.response().setStatusCode(501).end(); // 501 Not Implemented
    } catch (Exception e) {
      badRequest(context);
    }
  }

  private void apiGetJob(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      Job.getJob(id).setHandler(resultHandler(context, r -> {
        if (r.isPresent()) {
          context.response()
            .putHeader("content-type", "application/json")
            .end(r.toString());
        } else {
          notFound(context);
        }
      }));
    } catch (Exception e) {
      badRequest(context);
    }
  }

  private void apiJobRange(RoutingContext context) {
    try {
      String order = context.request().getParam("param2");
      if (order == null || !(order.equals("/asc") && order.equals("/desc")))
        order = "asc";
      else
        order = order.substring(1);
      Long from = Long.parseLong(context.request().getParam("param0"));
      Long to = Long.parseLong(context.request().getParam("param1"));
      Job.jobRange(from, to, order)
        .setHandler(resultHandler(context, r -> {
          String result = new JsonArray(r).encodePrettily();
          context.response()
            .putHeader("content-type", "application/json")
            .end(result);
        }));
    } catch (Exception e) {
      e.printStackTrace();
      badRequest(context);
    }
  }

  private void apiDeleteJob(RoutingContext context) {

  }

  private void apiFetchLog(RoutingContext context) {

  }

  // helper methods

  /**
   * Wrap the result handler with failure handler (503 Service Unavailable)
   */
  private <T> Handler<AsyncResult<T>> resultHandler(RoutingContext context, Handler<T> handler) {
    return res -> {
      if (res.succeeded()) {
        handler.handle(res.result());
      } else {
        serviceUnavailable(context);
      }
    };
  }

  private void sendError(int statusCode, RoutingContext context) {
    context.response().setStatusCode(statusCode).end();
  }

  private void badRequest(RoutingContext context) {
    context.response().setStatusCode(400).end();
  }

  private void notFound(RoutingContext context) {
    context.response().setStatusCode(404).end();
  }

  private void serviceUnavailable(RoutingContext context) {
    context.response().setStatusCode(503).end();
  }

  private void serviceUnavailable(RoutingContext context, Throwable ex) {
    context.response().setStatusCode(503)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
  }
}
