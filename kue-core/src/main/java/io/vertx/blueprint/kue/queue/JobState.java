package io.vertx.blueprint.kue.queue;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Vert.x Blueprint - Job Queue
 * Job state enum class
 *
 * @author Eric Zhao
 */
@VertxGen
public enum JobState {
  INACTIVE("inactive"),
  ACTIVE("active"),
  COMPLETE("complete"),
  FAILED("failed"),
  DELAYED("delayed");

  private String state;

  JobState(String state) {
    this.state = state;
  }

  public String state() {
    return state;
  }

}
