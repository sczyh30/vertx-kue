package io.vertx.blueprint.kue.queue;

import io.vertx.codegen.annotations.VertxGen;

/**
 * Vert.x Blueprint - Job Queue
 * Job Priority
 *
 * @author Eric Zhao
 */
@VertxGen
public enum Priority {
  LOW,
  NORMAL,
  MEDIUM,
  HIGH,
  CRITICAL
}
