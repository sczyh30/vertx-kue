package io.vertx.blueprint.kue.service.impl;

import io.vertx.blueprint.kue.service.KueService;

import io.vertx.redis.RedisClient;

/**
 * Vert.x Blueprint - Job Queue
 * Kue Service Impl
 *
 * @author Eric Zhao
 */
public class KueServiceImpl implements KueService {

  private RedisClient redis;
}
