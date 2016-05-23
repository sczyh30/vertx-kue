package io.vertx.blueprint.kue.queue;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Vert.x Blueprint - Job Queue
 * Job Class
 *
 * @author Eric Zhao
 */
public class Job {

  private String type;
  private JsonObject data;
  private Priority priority = Priority.NORMAL;

  public Job(String type, JsonObject data) {
    this.type = type;
    this.data = data;
  }

  public Job priority(Priority level) {
    if (level != null)
      this.priority = level;
    return this;
  }

  public Job save() {
    // TODO...
    return this;
  }

}
