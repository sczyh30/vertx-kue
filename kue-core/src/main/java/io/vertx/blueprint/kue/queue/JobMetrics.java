package io.vertx.blueprint.kue.queue;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

import java.time.Instant;

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

  }

  public JobMetrics(JobMetrics other) {
    this.createdAt = other.createdAt;
    this.promoteAt = other.promoteAt;
    this.updatedAt = other.updatedAt;
    this.failedAt = other.failedAt;
    this.startedAt = other.startedAt;
    this.duration = other.duration;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public long getPromoteAt() {
    return promoteAt;
  }

  public void setPromoteAt(long promoteAt) {
    this.promoteAt = promoteAt;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
  }

  public long getFailedAt() {
    return failedAt;
  }

  public void setFailedAt(long failedAt) {
    this.failedAt = failedAt;
  }

  public long getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(long startedAt) {
    this.startedAt = startedAt;
  }

  public double getDuration() {
    return duration;
  }

  public void setDuration(double duration) {
    this.duration = duration;
  }
}
