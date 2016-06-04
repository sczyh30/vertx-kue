package io.vertx.blueprint.kue.queue;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Job Metrics Class
 *
 * @author Eric Zhao
 */
@DataObject(generateConverter = true)
public class JobMetrics {

  private long createdAt;
  private long promoteAt;
  private long updatedAt;
  private long failedAt;
  private long startedAt;
  private double duration;

  public JobMetrics() {
  }

  public JobMetrics(JsonObject json) {
    JobMetricsConverter.fromJson(json, this);
  }

  public JobMetrics(JobMetrics other) {
    this.createdAt = other.createdAt;
    this.promoteAt = other.promoteAt;
    this.updatedAt = other.updatedAt;
    this.failedAt = other.failedAt;
    this.startedAt = other.startedAt;
    this.duration = other.duration;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    JobMetricsConverter.toJson(this, json);
    return json;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public JobMetrics setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public long getPromoteAt() {
    return promoteAt;
  }

  public JobMetrics setPromoteAt(long promoteAt) {
    this.promoteAt = promoteAt;
    return this;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public JobMetrics setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  public JobMetrics updateNow() {
    this.updatedAt = System.currentTimeMillis();
    return this;
  }

  public long getFailedAt() {
    return failedAt;
  }

  public JobMetrics setFailedAt(long failedAt) {
    this.failedAt = failedAt;
    return this;
  }

  public long getStartedAt() {
    return startedAt;
  }

  public JobMetrics setStartedAt(long startedAt) {
    this.startedAt = startedAt;
    return this;
  }

  public double getDuration() {
    return duration;
  }

  public JobMetrics setDuration(double duration) {
    this.duration = duration;
    return this;
  }
}
