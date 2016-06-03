package io.vertx.blueprint.kue.util;

/**
 * Vert.x Blueprint - Job Queue
 * Redis helper class
 *
 * @author Eric Zhao
 */
public final class RedisHelper {

  private static final String VERTX_KUE_REDIS_PREFIX = "vertx_kue";

  private RedisHelper() {
  }

  public static String getRedisKey(String key) {
    return VERTX_KUE_REDIS_PREFIX + ":" + key;
  }
}
