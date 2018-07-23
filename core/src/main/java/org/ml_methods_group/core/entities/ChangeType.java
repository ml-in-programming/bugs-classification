package org.ml_methods_group.core.entities;

public enum ChangeType {
    DELETE, INSERT, MOVE, UPDATE;

    private static final ChangeType[] buffer = values();

    public static ChangeType valueOf(int value) {
        return value == -1 ? null : buffer[value];
    }
}
