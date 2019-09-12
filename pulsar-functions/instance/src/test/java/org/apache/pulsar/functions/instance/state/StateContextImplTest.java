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
package org.apache.pulsar.functions.instance.state;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.bookkeeper.api.kv.Table;
import org.apache.bookkeeper.common.concurrent.FutureUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;

/**
 * Unit test {@link StateContextImpl}.
 */
public class StateContextImplTest {

    private Table<ByteBuf, ByteBuf> mockTable;
    private StateContextImpl stateContext;

    @BeforeMethod
    public void setup() {
        this.mockTable = mock(Table.class);
        this.stateContext = new StateContextImpl(mockTable);
    }

    @Test
    public void testIncr() throws Exception {
        when(mockTable.increment(any(ByteBuf.class), anyLong()))
            .thenReturn(FutureUtils.Void());
        stateContext.incrCounter("test-key", 10L).get();
        verify(mockTable, times(1)).increment(
            eq(Unpooled.copiedBuffer("test-key", UTF_8)),
            eq(10L)
        );
    }

    @Test
    public void testPut() throws Exception {
        when(mockTable.put(any(ByteBuf.class), any(ByteBuf.class)))
            .thenReturn(FutureUtils.Void());
        stateContext.put("test-key", ByteBuffer.wrap("test-value".getBytes(UTF_8))).get();
        verify(mockTable, times(1)).put(
            eq(Unpooled.copiedBuffer("test-key", UTF_8)),
            eq(Unpooled.copiedBuffer("test-value", UTF_8))
        );
    }

    @Test
    public void testGetValue() throws Exception {
        ByteBuf returnedValue = Unpooled.copiedBuffer("test-value", UTF_8);
        when(mockTable.get(any(ByteBuf.class)))
            .thenReturn(FutureUtils.value(returnedValue));
        ByteBuffer result = stateContext.get("test-key").get();
        assertEquals("test-value", new String(result.array(), UTF_8));
        verify(mockTable, times(1)).get(
            eq(Unpooled.copiedBuffer("test-key", UTF_8))
        );
    }

    @Test
    public void testGetAmount() throws Exception {
        when(mockTable.getNumber(any(ByteBuf.class)))
            .thenReturn(FutureUtils.value(10L));
        assertEquals((Long)10L, stateContext.getCounter("test-key").get());
        verify(mockTable, times(1)).getNumber(
            eq(Unpooled.copiedBuffer("test-key", UTF_8))
        );
    }

}
