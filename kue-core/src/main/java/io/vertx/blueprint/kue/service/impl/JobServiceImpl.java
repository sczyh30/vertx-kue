package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.queue.Job;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Vert.x Blueprint - Job Queue
 * Job Service Implementation
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
        List<Long> list = (List<Long>) r.result().getList().stream()
          .map(e -> RedisHelper.numStripFIFO((String) e))
          .collect(Collectors.toList());
        long max = list.get(list.size() - 1);
        List<Job> jobList = new ArrayList<>();
        list.forEach(e -> {
          this.getJob(e, jr -> {
            if (jr.succeeded()) {
              if (jr.result() != null) {
                jobList.add(jr.result());
              }
              if (e >= max) {
                jobList.sort((a1, a2) -> {
                  if (order.equals("asc"))
                    return Long.compare(a1.getId(), a2.getId());
                  else
                    return Long.compare(a2.getId(), a1.getId());
                });
                handler.handle(Future.succeededFuture(jobList));
              }
            } else {
              handler.handle(Future.failedFuture(jr.cause()));
            }
          });
        });
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
        if (r.succeeded())
          handler.handle(Future.succeededFuture());
        else
          handler.handle(Future.failedFuture(r.cause()));
      });

    // TODO: search functionality
    return this;
  }
}
