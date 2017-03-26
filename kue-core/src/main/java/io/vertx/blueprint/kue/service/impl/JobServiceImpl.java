package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.queue.Job;
import io.vertx.blueprint.kue.queue.JobState;
import io.vertx.blueprint.kue.service.JobService;
import io.vertx.blueprint.kue.util.RedisHelper;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Redis backend implementation of {@link JobService}.
 *
 * @author Eric Zhao
 */
public final class JobServiceImpl implements JobService {

  private final Vertx vertx;
  private final JsonObject config;
  private final RedisClient client;

  public JobServiceImpl(Vertx vertx) {
    this(vertx, new JsonObject());
  }

  public JobServiceImpl(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.client = RedisClient.create(vertx, RedisHelper.options(config));
    Job.setVertx(vertx, RedisHelper.client(vertx, config)); // init static vertx instance inner job
  }

  @Override
  public JobService getJob(long id, Handler<AsyncResult<Job>> handler) {
    String zid = RedisHelper.createFIFO(id);
    client.hgetall(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        try {
          if (!r.result().containsKey("id")) {
            handler.handle(Future.succeededFuture());
          } else {
            Job job = new Job(r.result());
            job.setId(id);
            job.setZid(zid);
            handler.handle(Future.succeededFuture(job));
          }
        } catch (Exception e) {
          e.printStackTrace();
          this.removeBadJob(id, "", null);
          handler.handle(Future.failedFuture(e));
        }
      } else {
        this.removeBadJob(id, "", null);
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }

  @Override
  public JobService removeJob(long id, Handler<AsyncResult<Void>> handler) {
    this.getJob(id, r -> {
      if (r.succeeded()) {
        if (r.result() != null) {
          r.result().remove()
            .setHandler(handler);
        } else {
          handler.handle(Future.succeededFuture());
        }
      } else {
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }

  @Override
  public JobService existsJob(long id, Handler<AsyncResult<Boolean>> handler) {
    client.exists(RedisHelper.getKey("job:" + id), r -> {
      if (r.succeeded()) {
        if (r.result() == 0)
          handler.handle(Future.succeededFuture(false));
        else
          handler.handle(Future.succeededFuture(true));
      } else {
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }

  @Override
  public JobService getJobLog(long id, Handler<AsyncResult<JsonArray>> handler) {
    client.lrange(RedisHelper.getKey("job:" + id + ":log"), 0, -1, handler);
    return this;
  }

  @Override
  public JobService jobRangeByState(String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    return rangeGeneral("jobs:" + state.toUpperCase(), from, to, order, handler);
  }

  @Override
  public JobService jobRangeByType(String type, String state, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    return rangeGeneral("jobs:" + type + ":" + state.toUpperCase(), from, to, order, handler);
  }

  @Override
  public JobService jobRange(long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    return rangeGeneral("jobs", from, to, order, handler);
  }

  /**
   * Range job by from, to and order
   *
   * @param key     range type(key)
   * @param from    from
   * @param to      to
   * @param order   range order(asc, desc)
   * @param handler result handler
   */
  private JobService rangeGeneral(String key, long from, long to, String order, Handler<AsyncResult<List<Job>>> handler) {
    if (to < from) {
      handler.handle(Future.failedFuture("to can not be greater than from"));
      return this;
    }
    client.zrange(RedisHelper.getKey(key), from, to, r -> {
      if (r.succeeded()) {
        if (r.result().size() == 0) { // maybe empty
          handler.handle(Future.succeededFuture(new ArrayList<>()));
        } else {
          List<Long> list = (List<Long>) r.result().getList().stream()
            .map(e -> RedisHelper.numStripFIFO((String) e))
            .collect(Collectors.toList());
          list.sort((a1, a2) -> {
            if (order.equals("asc"))
              return Long.compare(a1, a2);
            else
              return Long.compare(a2, a1);
          });
          long max = Math.max(list.get(0), list.get(list.size() - 1));
          List<Job> jobList = new ArrayList<>();
          list.forEach(e -> {
            this.getJob(e, jr -> {
              if (jr.succeeded()) {
                if (jr.result() != null) {
                  jobList.add(jr.result());
                }
                if (e >= max) {
                  handler.handle(Future.succeededFuture(jobList));
                }
              } else {
                handler.handle(Future.failedFuture(jr.cause()));
              }
            });
          });
        }
      } else {
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }

  /**
   * Remove bad job by id (absolutely)
   *
   * @param id      job id
   * @param handler result handler
   */
  private JobService removeBadJob(long id, String jobType, Handler<AsyncResult<Void>> handler) {
    String zid = RedisHelper.createFIFO(id);
    client.transaction().multi(null)
      .del(RedisHelper.getKey("job:" + id + ":log"), null)
      .del(RedisHelper.getKey("job:" + id), null)
      .zrem(RedisHelper.getKey("jobs:INACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:ACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:COMPLETE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:FAILED"), zid, null)
      .zrem(RedisHelper.getKey("jobs:DELAYED"), zid, null)
      .zrem(RedisHelper.getKey("jobs"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":INACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":ACTIVE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":COMPLETE"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":FAILED"), zid, null)
      .zrem(RedisHelper.getKey("jobs:" + jobType + ":DELAYED"), zid, null)
      .exec(r -> {
        if (handler != null) {
          if (r.succeeded())
            handler.handle(Future.succeededFuture());
          else
            handler.handle(Future.failedFuture(r.cause()));
        }
      });

    // TODO: add search functionality

    return this;
  }

  @Override
  public JobService cardByType(String type, JobState state, Handler<AsyncResult<Long>> handler) {
    client.zcard(RedisHelper.getKey("jobs:" + type + ":" + state.name()), handler);
    return this;
  }

  @Override
  public JobService card(JobState state, Handler<AsyncResult<Long>> handler) {
    client.zcard(RedisHelper.getKey("jobs:" + state.name()), handler);
    return this;
  }

  @Override
  public JobService completeCount(String type, Handler<AsyncResult<Long>> handler) {
    if (type == null)
      return this.card(JobState.COMPLETE, handler);
    else
      return this.cardByType(type, JobState.COMPLETE, handler);
  }

  @Override
  public JobService failedCount(String type, Handler<AsyncResult<Long>> handler) {
    if (type == null)
      return this.card(JobState.FAILED, handler);
    else
      return this.cardByType(type, JobState.FAILED, handler);
  }

  @Override
  public JobService inactiveCount(String type, Handler<AsyncResult<Long>> handler) {
    if (type == null)
      return this.card(JobState.INACTIVE, handler);
    else
      return this.cardByType(type, JobState.INACTIVE, handler);
  }

  @Override
  public JobService activeCount(String type, Handler<AsyncResult<Long>> handler) {
    if (type == null)
      return this.card(JobState.ACTIVE, handler);
    else
      return this.cardByType(type, JobState.ACTIVE, handler);
  }

  @Override
  public JobService delayedCount(String type, Handler<AsyncResult<Long>> handler) {
    if (type == null)
      return this.card(JobState.DELAYED, handler);
    else
      return this.cardByType(type, JobState.DELAYED, handler);
  }

  @Override
  public JobService getAllTypes(Handler<AsyncResult<List<String>>> handler) {
    client.smembers(RedisHelper.getKey("job:types"), r -> {
      if (r.succeeded()) {
        handler.handle(Future.succeededFuture(r.result().getList()));
      } else {
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }

  @Override
  public JobService getIdsByState(JobState state, Handler<AsyncResult<List<Long>>> handler) {
    client.zrange(RedisHelper.getStateKey(state), 0, -1, r -> {
      if (r.succeeded()) {
        List<Long> list = r.result().stream()
          .map(e -> RedisHelper.numStripFIFO((String) e))
          .collect(Collectors.toList());
        handler.handle(Future.succeededFuture(list));
      } else {
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }

  @Override
  public JobService getWorkTime(Handler<AsyncResult<Long>> handler) {
    client.get(RedisHelper.getKey("stats:work-time"), r -> {
      if (r.succeeded()) {
        handler.handle(Future.succeededFuture(Long.parseLong(r.result() == null ? "0" : r.result())));
      } else {
        handler.handle(Future.failedFuture(r.cause()));
      }
    });
    return this;
  }
}
