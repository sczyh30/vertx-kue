package io.vertx.blueprint.kue.queue;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Job Class
 *
 * @author Eric Zhao
 */
@DataObject(generateConverter = true)
public class Job {

  // TODO: not a good design
  private static Vertx vertx;
  private static HandlerCache handlerCache;

  private long id = -1;
  private String type;
  private JsonObject data;
  private Priority priority = Priority.NORMAL;

  private int progress = 0;

  private JsonObject result;

  public Job() {
  }

  public Job(JsonObject json) {
    JobConverter.fromJson(json, this);
  }

  public Job(Job other) {
    this.type = other.type;
    this.data = other.data.copy();
    this.priority = other.priority;
  }

  public Job(String type, JsonObject data) {
    this.type = type;
    this.data = data;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    JobConverter.toJson(this, json);
    return json;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  @Fluent
  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  public static void setVertx(Vertx v) {
    vertx = v;
    handlerCache = HandlerCache.getInstance(vertx);
  }

  @Fluent
  public Job onComplete(Handler<Job> completeHandler) {
    handlerCache.addCompleteHandler(this.type, completeHandler);
    return this;
  }

  @Fluent
  public Job onFailure(Handler<Throwable> failureHandler) {
    handlerCache.addFailureHandler(this.type, failureHandler);
    return this;
  }

}
