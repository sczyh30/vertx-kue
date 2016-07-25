package io.vertx.blueprint.kue;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.service.JobService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;


/**
 * Implementation of {@link io.vertx.blueprint.kue.CallbackKue}.
 *
 * @author Eric Zhao
 */
public class CallbackKueImpl implements CallbackKue {

  private final Kue kue;
  private final JobService jobService;

  public CallbackKueImpl(Vertx vertx, JsonObject config) {
    this.kue = new Kue(vertx, config);
    this.jobService = kue.getJobService();
  }

  @Override
  public Job createJob(String type, JsonObject data) {
    return kue.createJob(type, data);
  }

  @Override
  public CallbackKue saveJob(Job job, Handler<AsyncResult<Job>> handler) {
    job.save().setHandler(handler);
    return this;
  }

  @Override
  public CallbackKue jobProgress(Job job, int complete, int total, Handler<AsyncResult<Job>> handler) {
    job.progress(complete, total).setHandler(handler);
    return this;
  }

  @Override
  public CallbackKue jobDoneFail(Job job, Throwable ex) {
    job.done(ex);
    return this;
  }

  @Override
  public CallbackKue jobDone(Job job) {
    job.done();
    return this;
  }

  @Override
  public <R> CallbackKue on(String eventType, Handler<Message<R>> handler) {
    kue.on(eventType, handler);
    return this;
  }

  @Override
  public CallbackKue process(String type, int n, Handler<Job> handler) {
    kue.process(type, n, handler);
    return this;
  }

  @Override
  public CallbackKue processBlocking(String type, int n, Handler<Job> handler) {
    kue.processBlocking(type, n, handler);
    return this;
  }

  @Override
  public CallbackKue getJob(long id, Handler<AsyncResult<Job>> handler) {
    jobService.getJob(id, handler);
    return this;
  }

  @Override
  public CallbackKue removeJob(long id, Handler<AsyncResult<Void>> handler) {
    jobService.removeJob(id, handler);
    return this;
  }

  @Override
  public CallbackKue existsJob(long id, Handler<AsyncResult<Boolean>> handler) {
    jobService.existsJob(id, handler);
    return this;
  }

  @Override
  public CallbackKue getJobLog(long id, Handler<AsyncResult<JsonArray>> handler) {
    jobService.getJobLog(id, handler);
    return this;
  }

  @Override
  public CallbackKue jobRangeByState(String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    jobService.jobRangeByState(state, from, to, order, handler);
    return this;
  }

  @Override
  public JobService jobRangeByType(String type, String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    jobService.jobRangeByType(type, state, from, to, order, handler);
    return this;
  }

  @Override
  public JobService jobRange(long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    jobService.jobRange(from, to, order, handler);
    return this;
  }

  @Override
  public CallbackKue cardByType(String type, JobState state, Handler<AsyncResult<Long>> handler) {
    jobService.cardByType(type, state, handler);
    return this;
  }

  @Override
  public CallbackKue card(JobState state, Handler<AsyncResult<Long>> handler) {
    jobService.card(state, handler);
    return this;
  }

  @Override
  public CallbackKue completeCount(String type, Handler<AsyncResult<Long>> handler) {
    jobService.completeCount(type, handler);
    return this;
  }

  @Override
  public CallbackKue failedCount(String type, Handler<AsyncResult<Long>> handler) {
    jobService.failedCount(type, handler);
    return this;
  }

  @Override
  public CallbackKue inactiveCount(String type, Handler<AsyncResult<Long>> handler) {
    jobService.inactiveCount(type, handler);
    return this;
  }

  @Override
  public CallbackKue activeCount(String type, Handler<AsyncResult<Long>> handler) {
    jobService.activeCount(type, handler);
    return this;
  }

  @Override
  public CallbackKue delayedCount(String type, Handler<AsyncResult<Long>> handler) {
    jobService.delayedCount(type, handler);
    return this;
  }

  @Override
  public CallbackKue getAllTypes(Handler<AsyncResult<List<String>>> handler) {
    jobService.getAllTypes(handler);
    return this;
  }

  @Override
  public CallbackKue getIdsByState(JobState state, Handler<AsyncResult<List<Long>>> handler) {
    jobService.getIdsByState(state, handler);
    return this;
  }

  @Override
  public CallbackKue getWorkTime(Handler<AsyncResult<Long>> handler) {
    jobService.getWorkTime(handler);
    return this;
  }
}
