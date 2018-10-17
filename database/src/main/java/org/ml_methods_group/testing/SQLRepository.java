package org.ml_methods_group.testing;

import org.ml_methods_group.testing.database.Condition;
import org.ml_methods_group.testing.database.ConditionSupplier;
import org.ml_methods_group.testing.database.Proxy;
import org.ml_methods_group.testing.database.Repository;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class SQLRepository<T> implements Repository<T> {
    private final Class<T> template;
    private final List<Column> columns;
    private final Map<Column, Field> fields;
    private final Table table;

    SQLRepository(String name, Class<T> template, SQLDatabase database) {
        this.template = template;
        this.columns = new ArrayList<>();
        this.fields = new HashMap<>();

        final List<Field> dataFields = Arrays.stream(template.getDeclaredFields())
                .sorted(Comparator.comparing(SQLRepository::getCanonicalName))
                .peek(field -> field.setAccessible(true))
                .collect(Collectors.toCollection(ArrayList::new));
        for (Field field : dataFields) {
            final Column column = generateColumn(field);
            columns.add(column);
            fields.put(column, field);
        }
        columns.add(Column.ID);
        this.table = database.getTable(name, columns);
        table.create();
    }

    SQLRepository(Class<T> template, SQLDatabase database) {
        this(template.getSimpleName(), template, database);
    }

    @Override
    public void insert(T value) {
        try {
            final List<Object> data = new ArrayList<>();
            for (Column column : columns) {
                final Field field = fields.get(column);
                data.add(field == null ? null : field.get(value));
            }
            table.insert(data);
        } catch (IllegalAccessException e) {
            throw new DatabaseException(e);
        }
    }

    @Override
    public Iterator<T> get(Condition... conditions) {
        final Iterator<Table.ResultWrapper> iterator = table.select(conditions);
        return new Iterator<T>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                return parse(iterator.next());
            }
        };
    }

    @Override
    public Iterator<Proxy<T>> getProxy(Condition... conditions) {
        final Iterator<Table.ResultWrapper> iterator = table.select(conditions);
        return new Iterator<Proxy<T>>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Proxy<T> next() {
                return createProxy(iterator.next());
            }
        };
    }

    @Override
    public Optional<T> find(Condition... conditions) {
        return table.find(conditions).map(this::parse);
    }

    @Override
    public void clear() {
        table.clear();
    }

    @Override
    public ConditionSupplier conditionSupplier() {
        return SQLConditionSupplier.instance();
    }

    @Override
    public Iterator<T> iterator() {
        return get();
    }

    private T parse(Table.ResultWrapper wrapper) {
        try {
            final T instance = template.newInstance();
            for (Column column : columns) {
                final Field field = fields.get(column);
                if (field != null) {
                    field.set(instance, wrapper.getValue(column, field.getType()));
                }
            }
            return instance;
        } catch (Exception e) {
            throw new DatabaseException(e);
        }
    }

    private Proxy<T> createProxy(Table.ResultWrapper wrapper) {
        final int id = wrapper.getIntValue(Column.ID);
        return new SQLProxy(id);
    }

    private static Column generateColumn(Field field) {
        final DataType dataType = DataType.getDefaultTypeFor(field.getType());
        final String columnName = field.getName();
        return new Column(columnName, dataType);
    }

    private static String getCanonicalName(Field field) {
        return field.getDeclaringClass().getCanonicalName() + "." + field.getName();
    }

    private class SQLProxy implements Proxy<T> {
        private final int id;

        private SQLProxy(int id) {
            this.id = id;
        }

        @Override
        public T get() {
            return table.find(SQLConditionSupplier.instance().is("ID", id))
                    .map(SQLRepository.this::parse)
                    .orElseThrow(NoSuchElementException::new);
        }
    }
}
