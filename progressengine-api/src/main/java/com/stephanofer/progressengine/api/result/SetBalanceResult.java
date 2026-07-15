package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;

/**
 * Typed result of a set-balance request.
 */
public sealed interface SetBalanceResult permits SetBalanceResult.Success, SetBalanceResult.BalanceLimitExceeded,
    SetBalanceResult.IdempotencyConflict {
    /** Returns the operation id. */
    OperationId operationId();

    /** Successful set-balance operation. */
    record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements SetBalanceResult {
        /** Creates a success result. */
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (receipt.type() != OperationType.SET_BALANCE) throw new IllegalArgumentException("receipt must be SET_BALANCE");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    /** Set rejected because the configured maximum would be exceeded. */
    record BalanceLimitExceeded(OperationId operationId, ReplayStatus replayStatus) implements SetBalanceResult {
        /** Creates a balance-limit result. */
        public BalanceLimitExceeded {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }

    /** Operation id was reused with a different request fingerprint. */
    record IdempotencyConflict(OperationId operationId) implements SetBalanceResult {
        /** Creates an idempotency conflict. */
        public IdempotencyConflict {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }
}
