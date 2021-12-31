/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.streams.common.cache.compress;

/**
 * 支持key是string，value是int的场景，支持size不大于10000000.只支持int，long，boolean，string类型 只能一次行load，不能进行更新
 */
public class ByteArrayValueKV extends CacheKV<byte[]> {

    protected final static String CODE = "UTF-8";
    protected ByteStore values;

    public ByteArrayValueKV(int capacity) {
        super(capacity, true);
        values = new ByteStore(-1);
    }

    public ByteArrayValueKV(int capacity, int elementSize) {
        super(capacity, true);
        if (elementSize > 0) {
            values = new ByteStore(elementSize);
        } else {
            values = new ByteStore(-1);
        }

    }

    /**
     * 直接存取byte数组
     *
     * @param key
     * @return
     */
    @Override
    public synchronized byte[] get(String key) {
        ByteArray value = super.getInner(key);
        if (value == null) {
            return null;
        }
        KVAddress mapAddress = new KVAddress(value);
        ByteArray byteArray = values.getValue(mapAddress);
        return byteArray.getByteArray();
    }

    /**
     * 如果是定长的字节，则判断已经有的value字节数和当前字节数相同，否则不允许插入
     *
     * @param key
     * @param value
     */
    @Override
    public synchronized void put(String key, byte[] value) {
        if (key == null || value == null) {
            return;
        }
        byte[] oriValue = get(key);
        if (oriValue != null) {
            if (oriValue.length != value.length) {
                throw new RuntimeException("The lengths of the two values are inconsistent。 the key is " + key + ", the ori value size is " + oriValue.length + ", the put value size is " + value.length);
            }
        }
        KVAddress address = null;

        int index = values.getConflictIndex();
        int offset = values.getConflictOffset();
        address = values.add2Store(value);
        byte[] bytes = address.createBytes();
        boolean success = super.putInner(key, new ByteArray(bytes), true);
        if (!success) {//不支持更新，如果存在已经有的key，则不插入，并回退刚插入的数据
            values.setConflictOffset(offset);
            values.setConflictIndex(index);
            throw new RuntimeException("can not update value, the key has exist");
        }

    }

    @Override
    public int calMemory() {
        return super.calMemory() + (this.conflicts.getConflictIndex() + 1) * this.conflicts.getBlockSize();
    }

    @Override
    public boolean contains(String key) {
        byte[] bytes = get(key);
        return bytes != null;
    }

}
