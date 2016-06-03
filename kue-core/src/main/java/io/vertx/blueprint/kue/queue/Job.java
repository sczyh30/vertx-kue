package io.vertx.blueprint.kue.queue;

import io.vertx.blueprint.kue.Kue;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

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
  private static RedisClient redisClient = KueVerticle.getRedis();

  public static void setVertx(Vertx v) {
    vertx = v;
    redisClient = KueVerticle.getRedis();
  }

  private long id = -1;
  private String type;
  private JsonObject data;
  private Priority priority = Priority.NORMAL;
  private int progress = 0;
  private JsonObject result;
  private JobMetrics jobMetrics = new JobMetrics();

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

  public JsonObject getData() {
    return data;
  }

  public Job setData(JsonObject data) {
    this.data = data;
    return this;
  }

  public String getType() {
    return type;
  }

  public Job setType(String type) {
    this.type = type;
    return this;
  }

  public Priority getPriority() {
    return priority;
  }

  public Job setPriority(Priority priority) {
    this.priority = priority;
    return this;
  }

  public JsonObject getResult() {
    return result;
  }

  public Job setResult(JsonObject result) {
    this.result = result;
    return this;
  }

  public int getProgress() {
    return progress;
  }

  public Job setProgress(int progress) {
    this.progress = progress;
    return this;
  }

  public JobMetrics getJobMetrics() {
    return jobMetrics;
  }

  public Job setJobMetrics(JobMetrics jobMetrics) {
    this.jobMetrics = jobMetrics;
    return this;
  }

  @Fluent
  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  @Fluent
  public Job progress(int complete, int total) {
    int n = Math.min(100, complete * 100 / total);
    this.setProgress(n);
    this.jobMetrics.updateNow();
    // TODO: emit this event
    return this;
  }

  @Fluent
  public Job onComplete(Handler<Job> completeHandler) {
    vertx.eventBus().consumer(Kue.getHandlerAddress("complete", this.type));
    // TODO: emit this event
    return this;
  }

  @Fluent
  public Job onFailure(Handler<Throwable> failureHandler) {
    // TODO: emit this event
    return this;
  }

}
