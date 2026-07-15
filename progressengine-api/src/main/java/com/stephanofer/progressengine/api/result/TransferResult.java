package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;

/**
 * Typed result of a transfer request.
 */
public sealed interface TransferResult permits TransferResult.Success, TransferResult.InsufficientFunds,
    TransferResult.BalanceLimitExceeded, TransferResult.SelfTransferRejected, TransferResult.IdempotencyConflict {
    /** Returns the operation id. */
    OperationId operationId();

    /** Successful transfer. */
    record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements TransferResult {
        /** Creates a success result. */
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (receipt.type() != OperationType.TRANSFER) throw new IllegalArgumentException("receipt must be TRANSFER");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    /** Transfer rejected because the sender balance was insufficient. */
    record InsufficientFunds(OperationId operationId, ReplayStatus replayStatus) implements TransferResult {
        /** Creates an insufficient-funds result. */
        public InsufficientFunds {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }

    /** Transfer rejected because the receiver maximum would be exceeded. */
    record BalanceLimitExceeded(OperationId operationId, ReplayStatus replayStatus) implements TransferResult {
        /** Creates a balance-limit result. */
        public BalanceLimitExceeded {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }

    /** Transfer rejected because sender and receiver are the same account. */
    record SelfTransferRejected(OperationId operationId) implements TransferResult {
        /** Creates a self-transfer rejection. */
        public SelfTransferRejected {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }

    /** Operation id was reused with a different request fingerprint. */
    record IdempotencyConflict(OperationId operationId) implements TransferResult {
        /** Creates an idempotency conflict. */
        public IdempotencyConflict {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }
}
