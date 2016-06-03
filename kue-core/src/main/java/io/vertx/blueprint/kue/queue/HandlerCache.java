package io.vertx.blueprint.kue.queue;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

/**
 * Vert.x Blueprint - Job Queue
 * Handler Cache (cache the callbacks)
 *
 * @author Eric Zhao
 */
public class HandlerCache {

  private static final String ON_COMPLETE_MAP = "kue_on_complete_handler_map";
  private static final String ON_FAILURE_MAP = "kue_on_failure_handler_map";

  private final SharedData sd;

  public HandlerCache(Vertx vertx) {
    this.sd = vertx.sharedData();
  }

  public static HandlerCache getInstance(Vertx vertx) {
    return new HandlerCache(vertx);
  }

  private <T1, T2> void doWithClusterMap(String type, Future<Void> future, Handler<AsyncMap<T1, T2>> handler) {
    sd.<T1, T2>getClusterWideMap(type, res -> {
      if (res.succeeded()) {
        handler.handle(res.result());
      } else {
        future.fail(res.cause());
      }
    });
  }

  public <T> Future<Void> addHandler(String eventType, String jobType, Handler<T> handler) {
    Future<Void> future = Future.future();
    doWithClusterMap(eventType, future, map -> map.put(jobType, handler, r -> {
      if (r.succeeded()) {
        future.complete();
      } else {
        future.fail(r.cause());
      }
    }));
    return future;
  }

  public Future<Void> removeHandler(String eventType, String jobType) {
    Future<Void> future = Future.future();
    doWithClusterMap(eventType, future, map -> map.remove(jobType, r -> {
      if (r.succeeded()) {
        future.complete();
      } else {
        future.fail(r.cause());
      }
    }));
    return future;
  }

  public Future<Void> addCompleteHandler(String jobType, Handler<Job> handler) {
    return addHandler(ON_COMPLETE_MAP, jobType, handler);
  }

  public Future<Void> addFailureHandler(String jobType, Handler<Throwable> handler) {
    return addHandler(ON_FAILURE_MAP, jobType, handler);
  }
}
