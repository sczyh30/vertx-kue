package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.service.KueService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 *
 * @author Eric Zhao
 */
public class Kue implements KueService {

  public static final String EB_KUE_ADDRESS = "vertx.kue.service.internal";

  private final Vertx vertx;
  private final KueService service;

  public Kue(Vertx vertx) {
    this.vertx = vertx;
    this.service = KueService.createProxy(vertx, EB_KUE_ADDRESS);
  }

  public static Kue createQueue(Vertx vertx) {
    return new Kue(vertx);
  }

  public Job createJob(String type, JsonObject data) {
    return new Job(type, data);
  }

  @Override
  public void saveJob(Job job) { // should return Future?
    service.saveJob(job);
  }

  @Override
  public void updateJob(Job job) {
    service.saveJob(job);
  }

  @Override
  public void process(String type, int n, Handler<AsyncResult<JsonObject>> handler) {
    service.process(type, n, handler);
  }
}
