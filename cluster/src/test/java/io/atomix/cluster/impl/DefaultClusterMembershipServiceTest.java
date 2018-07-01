/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.cluster.impl;

import io.atomix.cluster.BootstrapMembershipProvider;
import io.atomix.cluster.ClusterMembershipEvent;
import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ManagedClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.Member.State;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.TestBootstrapService;
import io.atomix.cluster.messaging.impl.TestBroadcastServiceFactory;
import io.atomix.cluster.messaging.impl.TestMessagingServiceFactory;
import io.atomix.utils.net.Address;
import org.junit.Test;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Default cluster service test.
 */
public class DefaultClusterMembershipServiceTest {

  private Member buildMember(int memberId) {
    return Member.builder(String.valueOf(memberId))
        .withAddress("localhost", memberId)
        .build();
  }

  private Collection<Address> buildBootstrapLocations(int nodes) {
    return IntStream.range(1, nodes + 1)
        .mapToObj(id -> Address.from("localhost", id))
        .collect(Collectors.toList());
  }

  @Test
  public void testClusterService() throws Exception {
    TestMessagingServiceFactory messagingServiceFactory = new TestMessagingServiceFactory();
    TestBroadcastServiceFactory broadcastServiceFactory = new TestBroadcastServiceFactory();

    Collection<Address> bootstrapLocations = buildBootstrapLocations(3);

    Member localMember1 = buildMember(1);
    ManagedClusterMembershipService clusterService1 = new DefaultClusterMembershipService(
        localMember1,
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(localMember1.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join()),
        new BootstrapMembershipProvider(bootstrapLocations));

    Member localMember2 = buildMember(2);
    ManagedClusterMembershipService clusterService2 = new DefaultClusterMembershipService(
        localMember2,
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(localMember2.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join()),
        new BootstrapMembershipProvider(bootstrapLocations));

    Member localMember3 = buildMember(3);
    ManagedClusterMembershipService clusterService3 = new DefaultClusterMembershipService(
        localMember3,
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(localMember3.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join()),
        new BootstrapMembershipProvider(bootstrapLocations));

    assertNull(clusterService1.getMember(MemberId.from("1")));
    assertNull(clusterService1.getMember(MemberId.from("2")));
    assertNull(clusterService1.getMember(MemberId.from("3")));

    CompletableFuture.allOf(new CompletableFuture[]{clusterService1.start(), clusterService2.start(),
        clusterService3.start()}).join();

    Thread.sleep(5000);

    assertEquals(3, clusterService1.getMembers().size());
    assertEquals(3, clusterService2.getMembers().size());
    assertEquals(3, clusterService3.getMembers().size());

    assertEquals(MemberId.Type.IDENTIFIED, clusterService1.getLocalMember().id().type());
    assertEquals(MemberId.Type.IDENTIFIED, clusterService1.getMember(MemberId.from("1")).id().type());
    assertEquals(MemberId.Type.IDENTIFIED, clusterService1.getMember(MemberId.from("2")).id().type());
    assertEquals(MemberId.Type.IDENTIFIED, clusterService1.getMember(MemberId.from("3")).id().type());

    assertEquals(State.ACTIVE, clusterService1.getLocalMember().getState());
    assertEquals(State.ACTIVE, clusterService1.getMember(MemberId.from("1")).getState());
    assertEquals(State.ACTIVE, clusterService1.getMember(MemberId.from("2")).getState());
    assertEquals(State.ACTIVE, clusterService1.getMember(MemberId.from("3")).getState());

    Member anonymousMember = buildMember(4);

    ManagedClusterMembershipService ephemeralClusterService = new DefaultClusterMembershipService(
        anonymousMember,
        new TestBootstrapService(
            messagingServiceFactory.newMessagingService(anonymousMember.address()).start().join(),
            broadcastServiceFactory.newBroadcastService().start().join()),
        new BootstrapMembershipProvider(bootstrapLocations));

    assertEquals(State.INACTIVE, ephemeralClusterService.getLocalMember().getState());

    assertNull(ephemeralClusterService.getMember(MemberId.from("1")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("2")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("3")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("4")));
    assertNull(ephemeralClusterService.getMember(MemberId.from("5")));

    ephemeralClusterService.start().join();

    Thread.sleep(1000);

    assertEquals(4, clusterService1.getMembers().size());
    assertEquals(4, clusterService2.getMembers().size());
    assertEquals(4, clusterService3.getMembers().size());
    assertEquals(4, ephemeralClusterService.getMembers().size());

    clusterService1.stop().join();

    Thread.sleep(15000);

    assertEquals(3, clusterService2.getMembers().size());

    assertNull(clusterService2.getMember(MemberId.from("1")));
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("2")).getState());
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("3")).getState());
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("4")).getState());

    ephemeralClusterService.stop().join();

    Thread.sleep(15000);

    assertEquals(2, clusterService2.getMembers().size());
    assertNull(clusterService2.getMember(MemberId.from("1")));
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("2")).getState());
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("3")).getState());
    assertNull(clusterService2.getMember(MemberId.from("4")));

    Thread.sleep(2500);

    assertEquals(2, clusterService2.getMembers().size());

    assertNull(clusterService2.getMember(MemberId.from("1")));
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("2")).getState());
    assertEquals(State.ACTIVE, clusterService2.getMember(MemberId.from("3")).getState());
    assertNull(clusterService2.getMember(MemberId.from("4")));

    TestClusterMembershipEventListener eventListener = new TestClusterMembershipEventListener();
    clusterService2.addListener(eventListener);

    clusterService3.getLocalMember().metadata().put("foo", "bar");

    ClusterMembershipEvent event = eventListener.nextEvent();
    assertEquals(ClusterMembershipEvent.Type.MEMBER_UPDATED, event.type());
    assertEquals("bar", event.subject().metadata().get("foo"));

    CompletableFuture.allOf(new CompletableFuture[]{clusterService1.stop(), clusterService2.stop(),
        clusterService3.stop()}).join();
  }

  private class TestClusterMembershipEventListener implements ClusterMembershipEventListener {
    private BlockingQueue<ClusterMembershipEvent> queue = new ArrayBlockingQueue<ClusterMembershipEvent>(10);

    @Override
    public void onEvent(ClusterMembershipEvent event) {
      queue.add(event);
    }

    ClusterMembershipEvent nextEvent() {
      try {
        return queue.poll(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        return null;
      }
    }
  }
}
