package com.echoroom.server.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.echoroom.server.room.RoomModels.JoinResult;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryRoomStateStoreTest {

    private final InMemoryRoomStateStore store = new InMemoryRoomStateStore();

    @Test
    void shouldRejectSixthParticipant() {
        IntStream.rangeClosed(1, 5).forEach(index -> {
            JoinResult joinResult = store.join("r-1", "s-" + index, "u-" + index, "User " + index);
            assertThat(joinResult.accepted()).isTrue();
        });

        JoinResult rejected = store.join("r-1", "s-6", "u-6", "User 6");

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.errorCode()).isEqualTo("ROOM_FULL");
    }

    @Test
    void shouldLeaveBySession() {
        store.join("r-1", "s-1", "u-1", "User 1");

        var leaveResult = store.leaveBySession("s-1");

        assertThat(leaveResult.left()).isTrue();
        assertThat(store.findBySession("s-1")).isEmpty();
        assertThat(store.getSnapshot("r-1")).isEmpty();
    }

    @Test
    void shouldMoveSessionWhenRejoiningAnotherRoom() {
        store.join("r-1", "s-1", "u-1", "User 1");

        JoinResult secondJoin = store.join("r-2", "s-1", "u-1", "User 1");

        assertThat(secondJoin.accepted()).isTrue();
        assertThat(store.getSnapshot("r-1")).isEmpty();
        assertThat(store.getSnapshot("r-2")).isPresent();
        assertThat(store.getSnapshot("r-2").orElseThrow().participants()).hasSize(1);
    }

    @Test
    void shouldGuardCapacityUnderConcurrentJoin() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> tasks = IntStream.rangeClosed(1, 10)
                .mapToObj(index -> (Callable<Boolean>) () -> store.join(
                    "r-concurrent",
                    "s-" + index,
                    "u-" + index,
                    "User " + index
                ).accepted())
                .toList();

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            long acceptedCount = futures.stream().filter(future -> {
                try {
                    return future.get();
                } catch (InterruptedException | ExecutionException error) {
                    return false;
                }
            }).count();

            assertThat(acceptedCount).isEqualTo(5);
            assertThat(store.getSnapshot("r-concurrent")).isPresent();
            assertThat(store.getSnapshot("r-concurrent").orElseThrow().participants()).hasSize(5);
        } finally {
            executor.shutdownNow();
        }
    }
}
