package com.stephanofer.progressengine.persistence;

public sealed interface OperationReservation permits OperationReservation.Reserved, OperationReservation.Existing {
    record Reserved(StoredOperation operation) implements OperationReservation {
        public Reserved {
            if (operation == null) throw new NullPointerException("operation cannot be null");
        }
    }

    record Existing(StoredOperation operation) implements OperationReservation {
        public Existing {
            if (operation == null) throw new NullPointerException("operation cannot be null");
        }
    }
}
