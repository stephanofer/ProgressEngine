package com.stephanofer.progressengine.synchronization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.stephanofer.progressengine.api.operation.OperationId;
import org.junit.jupiter.api.Test;

final class TransferNoticeDeduplicatorTest {
    @Test
    void marksOnlyTheFirstNoticeForAnOperation() {
        TransferNoticeDeduplicator deduplicator = new TransferNoticeDeduplicator();
        OperationId operationId = OperationId.generate();

        assertTrue(deduplicator.markFirst(operationId));
        assertFalse(deduplicator.markFirst(operationId));
    }

    @Test
    void releaseAllowsRetryAfterFailedFeedback() {
        TransferNoticeDeduplicator deduplicator = new TransferNoticeDeduplicator();
        OperationId operationId = OperationId.generate();

        assertTrue(deduplicator.markFirst(operationId));
        deduplicator.release(operationId);

        assertTrue(deduplicator.markFirst(operationId));
    }

    @Test
    void closeClearsSeenOperations() {
        TransferNoticeDeduplicator deduplicator = new TransferNoticeDeduplicator();
        OperationId operationId = OperationId.generate();

        assertTrue(deduplicator.markFirst(operationId));
        deduplicator.close();

        assertTrue(deduplicator.markFirst(operationId));
    }
}
