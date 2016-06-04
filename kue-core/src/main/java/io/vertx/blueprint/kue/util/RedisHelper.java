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

  public static String createFIFO(long id) {
    String idLen = "" + ("" + id).length();
    int len = 2 - idLen.length();
    while (len-- > 0)
      idLen = "0" + idLen;
    return idLen + "|" + id;
  }

  public static String stripFIFO(String zid) {
    return zid.substring(zid.indexOf('|') + 1);
  }
}
