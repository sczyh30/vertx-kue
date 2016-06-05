package io.vertx.blueprint.kue.util.functional;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Vert.x Blueprint - Job Queue Utils
 * Either monad trait - Left
 * This usually represent an error value
 *
 * @author Eric Zhao
 */
public final class Left<A, B> extends Either<A, B> {

  private final A a;

  public Left(A a) {
    this.a = a;
    this.isLeft = true;
  }

  @Override
  public A left() {
    return this.a;
  }

  @Override
  public B right() {
    throw new NoSuchElementException("Either.left.value on Right");
  }

  @Override
  public Optional<A> toOption() {
    return Optional.ofNullable(a);
  }

  @Override
  public String toString() {
    return "Either.left: " + a.toString();
  }
}
