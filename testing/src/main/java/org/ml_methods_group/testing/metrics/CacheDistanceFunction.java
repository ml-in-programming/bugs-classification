package org.ml_methods_group.testing.metrics;

import org.ml_methods_group.common.DistanceFunction;
import org.ml_methods_group.testing.database.ConditionSupplier;
import org.ml_methods_group.testing.database.Database;
import org.ml_methods_group.testing.database.Repository;

import java.util.Optional;
import java.util.function.ToIntFunction;

public class CacheDistanceFunction<T> implements DistanceFunction<T> {
    private final Repository<CachedDistance> repository;
    private final ConditionSupplier supplier;
    private final ToIntFunction<T> idExtractor;
    private final DistanceFunction<T> oracle;

    public CacheDistanceFunction(Database database, String identifier,
                                 ToIntFunction<T> idExtractor,
                                 DistanceFunction<T> oracle) {
        this.repository = database.getRepository(identifier, CachedDistance.class);
        this.supplier = repository.conditionSupplier();
        this.idExtractor = idExtractor;
        this.oracle = oracle;
    }

    @Override
    public double distance(T first, T second) {
        final int firstId = idExtractor.applyAsInt(first);
        final int secondId = idExtractor.applyAsInt(second);
        final Optional<Double> cache = loadCached(firstId, secondId);
        if (cache.isPresent()) {
            return cache.get();
        }
        final double result = oracle.distance(first, second);
        storeCached(firstId, secondId, result);
        return result;
    }

    private Optional<Double> loadCached(int firstId, int secondId) {
        return repository.find(supplier.is("firstid", firstId), supplier.is("secondid", secondId))
                .map(CachedDistance::getDistance);
    }

    private void storeCached(int firstId, int secondId, double value) {
        repository.insert(new CachedDistance(firstId, secondId, value));
    }

    public static class CachedDistance {
        private final int firstId;
        private final int secondId;
        private final double distance;

        public CachedDistance() {
            this(0, 0, 0);
        }

        public CachedDistance(int firstId, int secondId, double distance) {
            this.firstId = firstId;
            this.secondId = secondId;
            this.distance = distance;
        }

        public int getFirstId() {
            return firstId;
        }

        public int getSecondId() {
            return secondId;
        }

        public double getDistance() {
            return distance;
        }
    }
}
