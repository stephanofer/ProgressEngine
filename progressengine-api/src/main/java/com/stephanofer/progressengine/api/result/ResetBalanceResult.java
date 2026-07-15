package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;

/**
 * Typed result of a reset-balance request.
 */
public sealed interface ResetBalanceResult permits ResetBalanceResult.Success, ResetBalanceResult.IdempotencyConflict {
    /** Returns the operation id. */
    OperationId operationId();

    /** Successful reset-balance operation. */
    record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements ResetBalanceResult {
        /** Creates a success result. */
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (receipt.type() != OperationType.RESET_BALANCE) throw new IllegalArgumentException("receipt must be RESET_BALANCE");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    /** Operation id was reused with a different request fingerprint. */
    record IdempotencyConflict(OperationId operationId) implements ResetBalanceResult {
        /** Creates an idempotency conflict. */
        public IdempotencyConflict {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }
}
