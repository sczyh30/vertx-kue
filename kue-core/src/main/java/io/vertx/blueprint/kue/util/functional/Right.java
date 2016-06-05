package io.vertx.blueprint.kue.util.functional;


import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Vert.x Blueprint - Job Queue Utils
 * Either monad trait - Right
 * This usually represent a correct value
 *
 * @author Eric Zhao
 */
public final class Right<A, B> extends Either<A, B> {

  private final B b;

  public Right(B b) {
    this.b = b;
    this.isRight = true;
  }

  @Override
  public A left() {
    throw new NoSuchElementException("Either.right.value on Left");
  }

  @Override
  public B right() {
    return this.b;
  }

  @Override
  public Optional<B> toOption() {
    return Optional.ofNullable(b);
  }

  @Override
  public String toString() {
    return "Either.right: " + b.toString();
  }
}
