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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.JadeTemplateEngine;

/**
 * The verticle serving Kue UI and REST API.
 *
 * @author Eric Zhao
 */
public class KueHttpVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(KueHttpVerticle.class);

  private static final String HOST = "0.0.0.0";
  private static final int PORT = 8080; // Default port

  // Kue REST API
  private static final String KUE_API_JOB_SEARCH = "/job/search/:q";
  private static final String KUE_API_STATS = "/stats";
  private static final String KUE_API_TYPE_STATE_STATS = "/jobs/:type/:state/stats";
  private static final String KUE_API_GET_JOB = "/job/:id";
  private static final String KUE_API_GET_JOB_TYPES = "/job/types";
  private static final String KUE_API_JOB_RANGE = "/jobs/:from/to/:to";
  private static final String KUE_API_JOB_TYPE_RANGE = "/jobs/:type/:state/:from/to/:to/:order";
  private static final String KUE_API_JOB_STATE_RANGE = "/jobs/:state/:from/to/:to/:order";
  private static final String KUE_API_JOB_RANGE_ORDER = "/jobs/:from/to/:to/:order";
  private static final String KUE_API_CREATE_JOB = "/job";
  private static final String KUE_API_UPDATE_JOB_STATE = "/job/:id/state/:state";
  private static final String KUE_API_DELETE_JOB = "/job/:id";
  private static final String KUE_API_GET_JOB_LOG = "/job/:id/log";
  private static final String KUE_API_RESTART_JOB = "/inactive/:id";

  // Kue UI
  private static final String KUE_UI_ROOT = "/";
  private static final String KUE_UI_ACTIVE = "/active";
  private static final String KUE_UI_INACTIVE = "/inactive";
  private static final String KUE_UI_FAILED = "/failed";
  private static final String KUE_UI_COMPLETE = "/complete";
  private static final String KUE_UI_DELAYED = "/delayed";

  private Kue kue;
  private JadeTemplateEngine engine;

  @Override
  public void start(Future<Void> future) throws Exception {
    // init kue
    kue = Kue.createQueue(vertx, config());
    engine = JadeTemplateEngine.create();
    // create route
    final Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());
    // REST API routes
    router.get(KUE_API_JOB_SEARCH).handler(this::apiSearchJob);
    router.get(KUE_API_STATS).handler(this::apiStats);
    router.get(KUE_API_TYPE_STATE_STATS).handler(this::apiTypeStateStats);
    router.get(KUE_API_GET_JOB_TYPES).handler(this::apiJobTypes);
    router.get(KUE_API_JOB_RANGE).handler(this::apiJobRange); // \/jobs\/([0-9]*)\.\.([0-9]*)(\/[^\/]+)?
    router.get(KUE_API_JOB_TYPE_RANGE).handler(this::apiJobTypeRange);
    router.get(KUE_API_JOB_STATE_RANGE).handler(this::apiJobStateRange);
    router.get(KUE_API_JOB_RANGE_ORDER).handler(this::apiJobRange);
    router.put(KUE_API_CREATE_JOB).handler(this::apiCreateJob);
    router.put(KUE_API_UPDATE_JOB_STATE).handler(this::apiUpdateJobState);
    router.get(KUE_API_GET_JOB).handler(this::apiGetJob);
    router.get(KUE_API_GET_JOB_LOG).handler(this::apiFetchLog);
    router.delete(KUE_API_DELETE_JOB).handler(this::apiDeleteJob);
    router.post(KUE_API_RESTART_JOB).handler(this::apiRestartJob);
    // UI routes
    router.route(KUE_UI_ROOT).handler(this::handleUIRoot);
    router.route(KUE_UI_ACTIVE).handler(this::handleUIActive);
    router.route(KUE_UI_INACTIVE).handler(this::handleUIInactive);
    router.route(KUE_UI_COMPLETE).handler(this::handleUIComplete);
    router.route(KUE_UI_FAILED).handler(this::handleUIFailed);
    router.route(KUE_UI_DELAYED).handler(this::handleUIADelayed);
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

  /**
   * Render UI by job state
   *
   * @param state job state
   */
  private void render(RoutingContext context, String state) { // TODO: bug in `types` param
    final String uiPath = "webroot/views/job/list.jade";
    String title = config().getString("kue.ui.title", "Vert.x Kue");
    kue.getAllTypes()
      .setHandler(resultHandler(context, r -> {
        context.put("state", state)
          .put("types", r)
          .put("title", title);
        engine.render(context, uiPath, res -> {
          if (res.succeeded()) {
            context.response()
              .putHeader("content-type", "text/html")
              .end(res.result());
          } else {
            context.fail(res.cause());
          }
        });
      }));
  }

  private void handleUIRoot(RoutingContext context) {
    handleUIActive(context); // by default active
  }

  private void handleUIInactive(RoutingContext context) {
    render(context, "inactive");
  }

  private void handleUIFailed(RoutingContext context) {
    render(context, "failed");
  }

  private void handleUIComplete(RoutingContext context) {
    render(context, "complete");
  }

  private void handleUIActive(RoutingContext context) {
    render(context, "active");
  }

  private void handleUIADelayed(RoutingContext context) {
    render(context, "delayed");
  }

  private void apiSearchJob(RoutingContext context) {
    notImplemented(context); // TODO: Not Implemented
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

  private void apiTypeStateStats(RoutingContext context) {
    try {
      String type = context.request().getParam("type");
      JobState state = JobState.valueOf(context.request().getParam("state").toUpperCase());
      kue.cardByType(type, state).setHandler(resultHandler(context, r -> {
        context.response()
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("count", r).encodePrettily());
      }));
    } catch (Exception e) {
      badRequest(context, e);
    }
  }

  private void apiJobTypes(RoutingContext context) {
    kue.getAllTypes().setHandler(resultHandler(context, r -> {
      context.response()
        .putHeader("content-type", "application/json")
        .end(new JsonArray(r).encodePrettily());
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
      badRequest(context, e);
    }
  }

  private void apiUpdateJobState(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      JobState state = JobState.valueOf(context.request().getParam("state").toUpperCase());
      kue.getJob(id)
        .compose(j1 -> {
          if (j1.isPresent()) {
            return j1.get().state(state)
              .compose(Job::save);
          } else {
            return Future.succeededFuture();
          }
        }).setHandler(resultHandler(context, job -> {
        if (job != null) {
          context.response().putHeader("content-type", "application/json")
            .end(new JsonObject().put("message", "job_state_updated").encodePrettily());
        } else {
          context.response().setStatusCode(404)
            .putHeader("content-type", "application/json")
            .end(new JsonObject().put("message", "job_not_found").encodePrettily());
        }
      }));
    } catch (Exception e) {
      badRequest(context, e);
    }
  }

  private void apiGetJob(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      kue.getJob(id).setHandler(resultHandler(context, r -> {
        if (r.isPresent()) {
          context.response()
            .putHeader("content-type", "application/json")
            .end(r.get().toString());
        } else {
          notFound(context);
        }
      }));
    } catch (Exception e) {
      badRequest(context, e);
    }
  }

  private void apiJobRange(RoutingContext context) {
    try {
      String order = context.request().getParam("order");
      if (order == null || !isOrderValid(order))
        order = "asc";
      Long from = Long.parseLong(context.request().getParam("from"));
      Long to = Long.parseLong(context.request().getParam("to"));
      kue.jobRange(from, to, order)
        .setHandler(resultHandler(context, r -> {
          String result = new JsonArray(r).encodePrettily();
          context.response()
            .putHeader("content-type", "application/json")
            .end(result);
        }));
    } catch (Exception e) {
      e.printStackTrace();
      badRequest(context, e);
    }
  }

  private void apiJobTypeRange(RoutingContext context) {
    try {
      String order = context.request().getParam("order");
      if (order == null || !isOrderValid(order)) {
        order = "asc";
      }
      Long from = Long.parseLong(context.request().getParam("from"));
      Long to = Long.parseLong(context.request().getParam("to"));
      String state = context.request().getParam("state");
      String type = context.request().getParam("type");
      kue.jobRangeByType(type, state, from, to, order)
        .setHandler(resultHandler(context, r -> {
          String result = new JsonArray(r).encodePrettily();
          context.response()
            .putHeader("content-type", "application/json")
            .end(result);
        }));
    } catch (Exception e) {
      e.printStackTrace();
      badRequest(context, e);
    }
  }

  private void apiJobStateRange(RoutingContext context) {
    try {
      String order = context.request().getParam("order");
      if (order == null || !isOrderValid(order))
        order = "asc";
      Long from = Long.parseLong(context.request().getParam("from"));
      Long to = Long.parseLong(context.request().getParam("to"));
      String state = context.request().getParam("state");
      kue.jobRangeByState(state, from, to, order)
        .setHandler(resultHandler(context, r -> {
          String result = new JsonArray(r).encodePrettily();
          context.response()
            .putHeader("content-type", "application/json")
            .end(result);
        }));
    } catch (Exception e) {
      e.printStackTrace();
      badRequest(context, e);
    }
  }

  private boolean isOrderValid(String order) {
    return order.equals("asc") && order.equals("desc");
  }

  private void apiDeleteJob(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      kue.removeJob(id).setHandler(resultHandler(context, r -> {
        context.response().setStatusCode(204)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("message", "job " + id + " removed").encodePrettily());
      }));
    } catch (Exception e) {
      badRequest(context, e);
    }
  }

  private void apiRestartJob(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      kue.getJob(id).setHandler(resultHandler(context, r -> {
        if (r.isPresent()) {
          r.get().inactive().setHandler(resultHandler(context, r1 -> {
            context.response()
              .putHeader("content-type", "application/json")
              .end(new JsonObject().put("message", "job " + id + " restart").encodePrettily());
          }));
        } else {
          notFound(context);
        }
      }));
    } catch (Exception e) {
      badRequest(context, e);
    }
  }

  private void apiFetchLog(RoutingContext context) {
    try {
      long id = Long.parseLong(context.request().getParam("id"));
      kue.getJobLog(id).setHandler(resultHandler(context, r -> {
        context.response().putHeader("content-type", "application/json")
          .end(r.encodePrettily());
      }));
    } catch (Exception e) {
      badRequest(context, e);
    }
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
        serviceUnavailable(context, res.cause());
      }
    };
  }

  private void sendError(int statusCode, RoutingContext context) {
    context.response().setStatusCode(statusCode).end();
  }

  private void badRequest(RoutingContext context, Throwable ex) {
    context.response().setStatusCode(400)
      .putHeader("content-type", "application/json")
      .end(new JsonObject().put("error", ex.getMessage()).encodePrettily());
  }

  private void notFound(RoutingContext context) {
    context.response().setStatusCode(404).end();
  }

  private void notImplemented(RoutingContext context) {
    context.response().setStatusCode(501)
      .end(new JsonObject().put("message", "not_implemented").encodePrettily());
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
