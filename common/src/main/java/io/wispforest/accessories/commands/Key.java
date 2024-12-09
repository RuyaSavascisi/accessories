package io.wispforest.accessories.commands;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Key {
    private final List<String> path;

    public Key(String key) {
        this(key.split("/"));
    }

    public Key(String... keyParts) {
        this(List.of(keyParts));
    }

    public Key(List<String> keyParts) {
        this.path = ImmutableList.copyOf(keyParts);
    }

    public Key child(String keyPart) {
        var parts = new ArrayList<>(path);

        parts.add(keyPart);

        return new Key(parts);
    }

    @Nullable
    public Key parent() {
        if (path.size() - 1 <= 0) return null;

        var parts = new ArrayList<>(path);

        parts.removeLast();

        return new Key(parts);
    }

    public String topPath() {
        return this.path.getLast();
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Key otherKey) && this.path.equals(otherKey.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.path);
    }
}
