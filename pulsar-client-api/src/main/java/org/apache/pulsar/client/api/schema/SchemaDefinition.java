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
package org.apache.pulsar.client.api.schema;

import org.apache.pulsar.client.internal.DefaultImplementation;

import java.util.Map;


public interface SchemaDefinition<T> {

    /**
     * Get a new builder instance that can used to configure and build a {@link SchemaDefinition} instance.
     *
     * @return the {@link SchemaDefinition}
     */
    static  <T> SchemaDefinitionBuilder<T> builder() {
        return DefaultImplementation.newSchemaDefinitionBuilder();
    }

    /**
     * get schema whether always allow null or not
     *
     * @return schema always null or not
     */
    public boolean getAlwaysAllowNull();

    /**
     * Get schema class
     *
     * @return schema class
     */
    public Map<String, String> getProperties();

    /**
     * Get json schema definition
     *
     * @return schema class
     */
    public String getJsonDef();

    /**
     * Get pojo schema definition
     *
     * @return pojo schema
     */
    public Class<T> getPojo();

    /**
     * Get supportSchemaVersioning schema definition
     *
     * @return the flag of supportSchemaVersioning
     */
    public boolean getSupportSchemaVersioning();
}
