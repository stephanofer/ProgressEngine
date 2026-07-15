package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;

/**
 * Typed result of a credit request.
 */
public sealed interface CreditResult permits CreditResult.Success, CreditResult.BalanceLimitExceeded,
    CreditResult.IdempotencyConflict {
    /** Returns the operation id. */
    OperationId operationId();

    /** Successful credit. */
    record Success(OperationReceipt receipt, ReplayStatus replayStatus) implements CreditResult {
        /** Creates a success result. */
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (receipt.type() != OperationType.CREDIT) throw new IllegalArgumentException("receipt must be CREDIT");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    /** Credit rejected because the balance maximum would be exceeded. */
    record BalanceLimitExceeded(OperationId operationId, ReplayStatus replayStatus) implements CreditResult {
        /** Creates a balance-limit result. */
        public BalanceLimitExceeded {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }

    /** Operation id was reused with a different request fingerprint. */
    record IdempotencyConflict(OperationId operationId) implements CreditResult {
        /** Creates an idempotency conflict. */
        public IdempotencyConflict {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }
}
