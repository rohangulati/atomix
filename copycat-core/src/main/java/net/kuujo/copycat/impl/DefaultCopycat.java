/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.impl;

import net.kuujo.copycat.Copycat;
import net.kuujo.copycat.CopycatConfig;
import net.kuujo.copycat.CopycatContext;
import net.kuujo.copycat.StateMachine;
import net.kuujo.copycat.cluster.ClusterConfig;
import net.kuujo.copycat.endpoint.Endpoint;
import net.kuujo.copycat.event.*;
import net.kuujo.copycat.log.Log;

import java.util.concurrent.CompletableFuture;

/**
 * Primary copycat API.<p>
 *
 * The <code>CopyCat</code> class provides a fluent API for
 * combining the {@link DefaultCopycatContext} with an {@link Endpoint}.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class DefaultCopycat implements Copycat {
  private final Endpoint endpoint;
  private final CopycatContext context;

  public DefaultCopycat(Endpoint endpoint, StateMachine stateMachine, Log log, ClusterConfig<?> cluster, CopycatConfig config) {
    this.context = new DefaultCopycatContext(stateMachine, log, cluster, config);
    this.endpoint = endpoint;
    endpoint.init(context);
  }

  public DefaultCopycat(Endpoint endpoint, CopycatContext context) {
    this.endpoint = endpoint;
    this.context = context;
    endpoint.init(context);
  }

  @Override
  public EventsContext on() {
    return context.on();
  }

  @Override
  public <T extends Event> EventContext<T> on(Class<T> event) {
    return context.on().<T>event(event);
  }

  @Override
  public EventHandlersRegistry events() {
    return context.events();
  }

  @Override
  public <T extends Event> EventHandlerRegistry<T> event(Class<T> event) {
    return context.event(event);
  }

  @Override
  public CompletableFuture<Void> start() {
    return context.start().thenRun(()->{});
  }

  @Override
  public CompletableFuture<Void> stop() {
    return endpoint.stop();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

}
