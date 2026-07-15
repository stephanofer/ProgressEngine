package com.stephanofer.progressengine.transaction;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;
import com.stephanofer.progressengine.persistence.OperationStatus;

public sealed interface DecodedOperationResult permits DecodedOperationResult.Success, DecodedOperationResult.Rejected {
    OperationId operationId();

    ReplayStatus replayStatus();

    record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements DecodedOperationResult {
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    record Rejected(OperationId operationId, OperationStatus status, ReplayStatus replayStatus) implements DecodedOperationResult {
        public Rejected {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (status == null) throw new NullPointerException("status cannot be null");
            if (status == OperationStatus.PENDING || status == OperationStatus.SUCCESS) {
                throw new IllegalArgumentException("status must be a durable rejection");
            }
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }
}
