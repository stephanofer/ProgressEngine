package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;

/**
 * Typed result of a debit request.
 */
public sealed interface DebitResult permits DebitResult.Success, DebitResult.InsufficientFunds,
    DebitResult.IdempotencyConflict {
    /** Returns the operation id. */
    OperationId operationId();

    /** Successful debit. */
    record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements DebitResult {
        /** Creates a success result. */
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (receipt.type() != OperationType.DEBIT) throw new IllegalArgumentException("receipt must be DEBIT");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    /** Debit rejected because the locked balance was insufficient. */
    record InsufficientFunds(OperationId operationId, ReplayStatus replayStatus) implements DebitResult {
        /** Creates an insufficient-funds result. */
        public InsufficientFunds {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }

    /** Operation id was reused with a different request fingerprint. */
    record IdempotencyConflict(OperationId operationId) implements DebitResult {
        /** Creates an idempotency conflict. */
        public IdempotencyConflict {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }
}
