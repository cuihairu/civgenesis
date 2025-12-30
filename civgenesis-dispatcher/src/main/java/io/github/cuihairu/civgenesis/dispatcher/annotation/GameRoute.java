package io.github.cuihairu.civgenesis.dispatcher.annotation;

import io.github.cuihairu.civgenesis.dispatcher.route.ShardBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GameRoute {
    int id();

    boolean open() default false;

    ShardBy shardBy() default ShardBy.PLAYER;
}

