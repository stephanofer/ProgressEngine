package com.stephanofer.progressengine.api.result;

import com.stephanofer.progressengine.api.operation.OperationId;
import com.stephanofer.progressengine.api.operation.OperationType;
import com.stephanofer.progressengine.api.operation.ReplayStatus;
import com.stephanofer.progressengine.api.transaction.OperationReceipt;

/**
 * Typed result of an award request.
 */
public sealed interface AwardResult permits AwardResult.Success, AwardResult.NoPointsAwarded,
    AwardResult.BalanceLimitExceeded, AwardResult.Cancelled, AwardResult.IdempotencyConflict {

    /** Returns the operation id. */
    OperationId operationId();

    /** Successful award. */
    record Success(OperationReceipt receipt, AwardCalculation calculation, ReplayStatus replayStatus) implements AwardResult {
        /** Creates a success result. */
        public Success {
            if (receipt == null) throw new NullPointerException("receipt cannot be null");
            if (receipt.type() != OperationType.AWARD) throw new IllegalArgumentException("receipt must be AWARD");
            if (calculation == null) throw new NullPointerException("calculation cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }

        @Override
        public OperationId operationId() {
            return this.receipt.operationId();
        }
    }

    /** Award resolved without a movement because FLOOR produced zero points. */
    record NoPointsAwarded(OperationId operationId, AwardCalculation calculation,
                           ReplayStatus replayStatus) implements AwardResult {
        /** Creates a no-points result. */
        public NoPointsAwarded {
            if (operationId == null) {
                throw new NullPointerException("operationId cannot be null");
            }
            if (calculation == null) {
                throw new NullPointerException("calculation cannot be null");
            }
            if (calculation.finalAmount() != 0L) {
                throw new IllegalArgumentException("calculation finalAmount must be zero");
            }
            if (replayStatus == null) {
                throw new NullPointerException("replayStatus cannot be null");
            }
        }
    }

    /** Award rejected because the balance maximum would be exceeded. */
    record BalanceLimitExceeded(OperationId operationId, ReplayStatus replayStatus) implements AwardResult {
        /** Creates a balance-limit result. */
        public BalanceLimitExceeded {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
            if (replayStatus == null) throw new NullPointerException("replayStatus cannot be null");
        }
    }

    /** Award cancelled by the prepare event before persistence. */
    record Cancelled(OperationId operationId) implements AwardResult {
        /** Creates a cancelled result. */
        public Cancelled {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }

    /** Operation id was reused with a different request fingerprint. */
    record IdempotencyConflict(OperationId operationId) implements AwardResult {
        /** Creates an idempotency conflict. */
        public IdempotencyConflict {
            if (operationId == null) throw new NullPointerException("operationId cannot be null");
        }
    }
}
