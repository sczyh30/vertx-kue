package io.vertx.blueprint.kue.util;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.RedisClient;
import io.vertx.redis.RedisOptions;

/**
 * Vert.x Blueprint - Job Queue Utils
 * Redis helper class
 *
 * @author Eric Zhao
 */
public final class RedisHelper {

  private static final String VERTX_KUE_REDIS_PREFIX = "vertx_kue";

  private RedisHelper() {
  }

  public static RedisClient client(Vertx vertx, JsonObject config) {
    return RedisClient.create(vertx, options(config));
  }

  public static RedisOptions options(JsonObject config) {
    return new RedisOptions()
      .setHost(config.getString("redis.host", "127.0.0.1"))
      .setPort(config.getInteger("redis.port", 6379));
  }

  public static String getKey(String key) {
    return VERTX_KUE_REDIS_PREFIX + ":" + key;
  }

  /**
   * Create an id for the zset to preserve FIFO order
   *
   * @param id id
   */
  public static String createFIFO(long id) {
    String idLen = "" + ("" + id).length();
    int len = 2 - idLen.length();
    while (len-- > 0)
      idLen = "0" + idLen;
    return idLen + "|" + id;
  }

  /**
   * Parse out original ID from zid
   * @param zid zid
   */
  public static String stripFIFO(String zid) {
    return zid.substring(zid.indexOf('|') + 1);
  }
}
