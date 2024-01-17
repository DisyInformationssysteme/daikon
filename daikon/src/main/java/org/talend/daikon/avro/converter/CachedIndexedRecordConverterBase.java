// Copyright 2005 - 2024 Talend, Inc., All Rights Reserved - www.talend.com
package org.talend.daikon.avro.converter;

import java.lang.reflect.Array;
import java.util.Arrays;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.IndexedRecord;
import org.talend.daikon.avro.AvroUtils;
import org.talend.daikon.avro.container.ContainerReaderByIndex;
import org.talend.daikon.avro.container.ContainerWriterByIndex;

/**
 * This abstract base class provides an implementation of an {@link IndexedRecordConverter} that caches the maximum
 * information possible for reading and writing data.
 * 
 * When wrapping a technology-specific container, subclasses should implement the missing methods to automatically
 * determine the contents of the output {@link IndexedRecord} that this factory generates.
 * 
 * @param <ContainerDataSpecT> A technology-specific class that can describe the types of container that this wraps.
 * @param <FieldDataSpecT> A technology-specific class that can describe the fields.
 * @param <GettableT> The technology-specific container type that can be used to get data from the container.
 * @param <SettableT> The technology-specific container type that can be used to set data into the container.
 */
public abstract class CachedIndexedRecordConverterBase<ContainerDataSpecT, FieldDataSpecT, GettableT, SettableT>
        implements IndexedRecordConverter<GettableT, IndexedRecord>, HasNestedAvroConverter<GettableT, IndexedRecord> {

    /** Every {@link IndexedRecord} generated by this factory should use the same schema. */
    private Schema schema;

    /** The class type of the fields (used to generate the array). */
    private final Class<FieldDataSpecT> fieldDataSpecClass;

    /** A container type associated with the factory. Once set, this is assumed to never change. */
    protected transient ContainerDataSpecT containerDataSpec;

    /** The cached specific data types for the fields of this record. */
    protected transient FieldDataSpecT[] fieldType;

    /** The cached AvroConverter objects for the fields of this record. */
    @SuppressWarnings("rawtypes")
    protected transient AvroConverter[] fieldConverter;

    /** The cached ContainerReaderByIndex objects for the fields of this record. */
    protected transient ContainerReaderByIndex<? super GettableT, ?>[] fieldReader;

    /** The cached ContainerReaderByIndex objects for the fields of this record. */
    protected transient ContainerWriterByIndex<? super SettableT, ?>[] fieldWriter;

    /**
     * Create a new instance of this class. It will self-initialize as necessary. This can be an expensive operation, so
     * instances of this class should be cached where possible.
     * 
     * @param fieldDataSpecClass The class of the technology-specific objects that describe the fields.
     */
    protected CachedIndexedRecordConverterBase(Class<FieldDataSpecT> fieldDataSpecClass) {
        this.fieldDataSpecClass = fieldDataSpecClass;
    }

    /**
     * @return the Schema used for {@link IndexedRecord}s created by this factory, or null if no Schema has been set or
     * inferred from existing data.
     */
    @Override
    public Schema getSchema() {
        return schema;
    }

    /**
     * Sets the known schema for this factory to the given value, which will be used for all subsequent generated
     * {@link IndexedRecord}s, or re-inferred from incoming data if null.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void setSchema(Schema schema) {
        this.schema = schema;
        if (schema != null) {
            // Initialized on the first convertToAvro or convertToDatum call.
            fieldType = (FieldDataSpecT[]) Array.newInstance(fieldDataSpecClass,
                    AvroUtils.unwrapIfNullable(schema).getFields().size());
            // Initialized on the first get(i) call on the indexed record for that field.
            fieldConverter = new AvroConverter[fieldType.length];
        } else {
            fieldType = null;
            fieldConverter = null;
            fieldWriter = null;
            fieldReader = null;
        }
    }

    /**
     * @return a technology-specific class that describes the container that this object wraps. This is usually used to
     * determine the field types.
     */
    public ContainerDataSpecT getContainerDataSpec() {
        return containerDataSpec;
    }

    /**
     * Set the container type for this factory. This automatically initializes the {@link Schema} using the inferrers.
     * 
     * @param containerType A container type can be used to access the field types.
     */
    public void setContainerDataSpec(ContainerDataSpecT containerType) {
        this.containerDataSpec = containerType;
        setSchemaFromContainerDataSpec(containerType);
    }

    /**
     * Given a container type, set the schema of the factory.
     */
    protected abstract void setSchemaFromContainerDataSpec(ContainerDataSpecT containerType);

    /**
     * Given an instance of a data object, set the container type.
     */
    protected abstract void setContainerDataSpecFromInstance(GettableT gettable);

    /**
     * @param i The index of the field.
     * @return The technology-specific data spec for that field.
     */
    public abstract FieldDataSpecT getFieldDataSpec(int i);

    protected abstract ContainerReaderByIndex<? super GettableT, ?> getFieldReader(FieldDataSpecT fDataSpec);

    protected abstract ContainerWriterByIndex<? super SettableT, ?> getFieldWriter(FieldDataSpecT fDataSpec);

    protected abstract void setToNull(SettableT value, int fieldIndex);

    @SuppressWarnings("rawtypes")
    protected abstract AvroConverter getConverter(FieldDataSpecT fDataSpec, Schema fSchema, Class<?> fDatumClass);

    protected abstract SettableT createOrGetInstance();

    @Override
    public Iterable<AvroConverter<?, ?>> getNestedAvroConverters() {
        for (Schema.Field f : AvroUtils.unwrapIfNullable(getSchema()).getFields()) {
            int i = f.pos();
            if (fieldType[i] == null) {
                fieldType[i] = getFieldDataSpec(i);
            }
            if (fieldConverter[i] == null) {
                fieldConverter[i] = getConverter(fieldType[i], f.schema(), null);
            }
        }
        return Arrays.asList((AvroConverter<?, ?>[]) fieldConverter);
    }

    @SuppressWarnings("unchecked")
    @Override
    public IndexedRecord convertToAvro(GettableT gettable) {
        if (containerDataSpec == null) {
            setContainerDataSpecFromInstance(gettable);
        }

        IndexedRecordAdapterWithCache record = new IndexedRecordAdapterWithCache(gettable);

        // Create all of the readers for the record immediately.
        if (fieldReader == null) {
            fieldReader = new ContainerReaderByIndex[fieldType.length];
            for (Field f : AvroUtils.unwrapIfNullable(getSchema()).getFields()) {
                int i = f.pos();
                fieldType[i] = getFieldDataSpec(i);
                fieldReader[i] = getFieldReader(fieldType[i]);
            }
        }

        return record;
    }

    @SuppressWarnings("unchecked")
    @Override
    public GettableT convertToDatum(IndexedRecord record) {

        if (fieldWriter == null) {
            fieldWriter = new ContainerWriterByIndex[fieldType.length];
            for (Field f : AvroUtils.unwrapIfNullable(getSchema()).getFields()) {
                int i = f.pos();
                fieldType[i] = getFieldDataSpec(i);
                fieldWriter[i] = getFieldWriter(fieldType[i]);
            }
        }

        SettableT value = createOrGetInstance();

        for (Field f : AvroUtils.unwrapIfNullable(getSchema()).getFields()) {
            int fieldIndex = f.pos();
            if (fieldType[fieldIndex] == null) {
                fieldType[fieldIndex] = getFieldDataSpec(fieldIndex);
            }
            Object fieldValue = record.get(fieldIndex);

            if (fieldValue == null) {
                setToNull(value, fieldIndex);
                continue;
            }

            if (fieldConverter[fieldIndex] == null) {
                fieldConverter[fieldIndex] = getConverter(fieldType[fieldIndex], f.schema(), fieldValue.getClass());
            }

            @SuppressWarnings("rawtypes")
            ContainerWriterByIndex writer = fieldWriter[fieldIndex];
            writer.writeValue(value, f.pos(), fieldConverter[fieldIndex].convertToDatum(fieldValue));
        }
        return (GettableT) value;
    }

    /**
     * An Adapter that maps the given {@link GettableT} to have the appearance of an Avro {@link IndexedRecord}. This
     * relies heavily on the cached and unchanging information stored in the factory.
     */
    private class IndexedRecordAdapterWithCache extends ComparableIndexedRecordBase {

        /** The wrapped data. */
        public GettableT gettable;

        public IndexedRecordAdapterWithCache(GettableT gettable) {
            this.gettable = gettable;
        }

        @Override
        public Schema getSchema() {
            return schema;
        }

        @Override
        public void put(int i, Object v) {
            // This should never happen.
            throw new UnmodifiableAdapterException();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Object get(int i) {
            Object value = fieldReader[i].readValue(gettable, i);
            if (value == null) {
                return null;
            }

            if (fieldConverter[i] == null) {
                fieldConverter[i] = getConverter(fieldType[i], AvroUtils.unwrapIfNullable(schema).getFields().get(i).schema(),
                        value.getClass());
            }

            if (fieldConverter[i] != null) {
                value = fieldConverter[i].convertToAvro(value);
            }

            return value;
        }

        @Override
        public String toString() {
            return gettable.toString();
        }
    }
}
