package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.service.JobService;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;


/**
 * A callback-based {@link io.vertx.blueprint.kue.Kue} interface for Vert.x Codegen to support polyglot languages.
 *
 * @author Eric Zhao
 */
@VertxGen
public interface CallbackKue extends JobService {

  static CallbackKue createKue(Vertx vertx, JsonObject config) {
    return new CallbackKueImpl(vertx, config);
  }

  Job createJob(String type, JsonObject data);

  @Fluent
  <R> CallbackKue on(String eventType, Handler<Message<R>> handler);

  @Fluent
  CallbackKue saveJob(Job job, Handler<AsyncResult<Job>> handler);

  @Fluent
  CallbackKue jobProgress(Job job, int complete, int total, Handler<AsyncResult<Job>> handler);

  @Fluent
  CallbackKue jobDone(Job job);

  @Fluent
  CallbackKue jobDoneFail(Job job, Throwable ex);

  @Fluent
  CallbackKue process(String type, int n, Handler<Job> handler);

  @Fluent
  CallbackKue processBlocking(String type, int n, Handler<Job> handler);
}
