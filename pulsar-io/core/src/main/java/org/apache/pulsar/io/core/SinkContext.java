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
package org.apache.pulsar.io.core;

import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public interface SinkContext {

    /**
     * The id of the instance that invokes this sink.
     *
     * @return the instance id
     */
    int getInstanceId();

    /**
     * Get the number of instances that invoke this sink.
     *
     * @return the number of instances that invoke this sink.
     */
    int getNumInstances();

    /**
     * Record a user defined metric
     * @param metricName The name of the metric
     * @param value The value of the metric
     */
    void recordMetric(String metricName, double value);

    /**
     * Get a list of all input topics
     * @return a list of all input topics
     */
    Collection<String> getInputTopics();

    /**
     * The tenant this sink belongs to
     * @return the tenant this sink belongs to
     */
    String getTenant();

    /**
     * The namespace this sink belongs to
     * @return the namespace this sink belongs to
     */
    String getNamespace();

    /**
     * The name of the sink that we are executing
     * @return The Sink name
     */
    String getSinkName();

    /**
     * The logger object that can be used to log in a sink
     * @return the logger object
     */
    Logger getLogger();

    /**
     * Get the secret associated with this key
     * @param secretName The name of the secret
     * @return The secret if anything was found or null
     */
    String getSecret(String secretName);

    /**
     * Increment the builtin distributed counter referred by key.
     *
     * @param key    The name of the key
     * @param amount The amount to be incremented
     */
    void incrCounter(String key, long amount);


    /**
     * Increment the builtin distributed counter referred by key
     * but dont wait for the completion of the increment operation
     *
     * @param key    The name of the key
     * @param amount The amount to be incremented
     */
    CompletableFuture<Void> incrCounterAsync(String key, long amount);

    /**
     * Retrieve the counter value for the key.
     *
     * @param key name of the key
     * @return the amount of the counter value for this key
     */
    long getCounter(String key);

    /**
     * Retrieve the counter value for the key, but don't wait
     * for the operation to be completed
     *
     * @param key name of the key
     * @return the amount of the counter value for this key
     */
    CompletableFuture<Long> getCounterAsync(String key);

    /**
     * Update the state value for the key.
     *
     * @param key   name of the key
     * @param value state value of the key
     */
    void putState(String key, ByteBuffer value);

    /**
     * Update the state value for the key, but don't wait for the operation to be completed
     *
     * @param key   name of the key
     * @param value state value of the key
     */
    CompletableFuture<Void> putStateAsync(String key, ByteBuffer value);

    /**
     * Retrieve the state value for the key.
     *
     * @param key name of the key
     * @return the state value for the key.
     */
    ByteBuffer getState(String key);

    /**
     * Retrieve the state value for the key, but don't wait for the operation to be completed
     *
     * @param key name of the key
     * @return the state value for the key.
     */
    CompletableFuture<ByteBuffer> getStateAsync(String key);
}
