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
package org.apache.pulsar.client.impl;

import org.apache.pulsar.common.api.proto.PulsarApi;
import org.apache.pulsar.common.compression.CompressionCodec;
import org.apache.pulsar.common.compression.CompressionCodecProvider;

import java.io.IOException;
import java.util.List;

/**
 * Batch message container framework.
 */
public abstract class AbstractBatchMessageContainer implements BatchMessageContainerBase {

    protected PulsarApi.CompressionType compressionType;
    protected CompressionCodec compressor;
    protected String topicName;
    protected String producerName;
    protected ProducerImpl producer;

    protected int maxNumMessagesInBatch;
    protected int numMessagesInBatch = 0;
    protected long currentBatchSizeBytes = 0;

    protected static final int INITIAL_BATCH_BUFFER_SIZE = 1024;
    protected static final int MAX_MESSAGE_BATCH_SIZE_BYTES = 128 * 1024;

    // This will be the largest size for a batch sent from this particular producer. This is used as a baseline to
    // allocate a new buffer that can hold the entire batch without needing costly reallocations
    protected int maxBatchSize = INITIAL_BATCH_BUFFER_SIZE;

    @Override
    public boolean haveEnoughSpace(MessageImpl<?> msg) {
        int messageSize = msg.getDataBuffer().readableBytes();
        return ((messageSize + currentBatchSizeBytes) <= MAX_MESSAGE_BATCH_SIZE_BYTES
                && numMessagesInBatch < maxNumMessagesInBatch);
    }

    @Override
    public int getNumMessagesInBatch() {
        return numMessagesInBatch;
    }

    @Override
    public long getCurrentBatchSize() {
        return currentBatchSizeBytes;
    }

    @Override
    public List<ProducerImpl.OpSendMsg> createOpSendMsgs() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ProducerImpl.OpSendMsg createOpSendMsg() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setProducer(ProducerImpl<?> producer) {
        this.producer = producer;
        this.topicName = producer.getTopic();
        this.producerName = producer.getProducerName();
        this.compressionType = CompressionCodecProvider
                .convertToWireProtocol(producer.getConfiguration().getCompressionType());
        this.compressor = CompressionCodecProvider.getCompressionCodec(compressionType);
        this.maxNumMessagesInBatch = producer.getConfiguration().getBatchingMaxMessages();
    }
}
