/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pulsar.client.impl.schema.generic;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.pulsar.client.api.SchemaSerializationException;
import org.apache.pulsar.client.api.schema.Field;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.SchemaReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;


public class GenericAvroReader implements SchemaReader<GenericRecord> {

    private final GenericDatumReader<GenericAvroRecord> reader;
    private BinaryEncoder encoder;
    private final ByteArrayOutputStream byteArrayOutputStream;
    private final List<Field> fields;
    private final Schema schema;
    private final byte[] schemaVersion;
    public GenericAvroReader(Schema schema) {
        this(null, schema, null);
    }

    public GenericAvroReader(Schema writerSchema, Schema readerSchema, byte[] schemaVersion) {
        this.schema = readerSchema;
        this.fields = schema.getFields()
                .stream()
                .map(f -> new Field(f.name(), f.pos()))
                .collect(Collectors.toList());
        this.schemaVersion = schemaVersion;
        if (writerSchema == null) {
            this.reader = new GenericDatumReader<>(readerSchema);
        } else {
            this.reader = new GenericDatumReader<>(writerSchema, readerSchema);
        }
        this.byteArrayOutputStream = new ByteArrayOutputStream();
        this.encoder = EncoderFactory.get().binaryEncoder(this.byteArrayOutputStream, encoder);
    }

    @Override
    public GenericAvroRecord read(byte[] bytes) {
        try {
            Decoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
            org.apache.avro.generic.GenericRecord avroRecord =
                    (org.apache.avro.generic.GenericRecord)reader.read(
                    null,
                    decoder);
            return new GenericAvroRecord(schemaVersion, schema, fields, avroRecord);
        } catch (IOException e) {
            throw new SchemaSerializationException(e);
        }
    }
}
