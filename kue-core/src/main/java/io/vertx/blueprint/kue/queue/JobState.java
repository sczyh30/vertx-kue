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
  INACTIVE,
  ACTIVE,
  COMPLETE,
  FAILED,
  DELAYED
}
