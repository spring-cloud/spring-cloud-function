/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KafkaEntity {
    @JsonProperty("Offset")
    private int offset;
    @JsonProperty("Partition")
    private int partition;
    @JsonProperty("Timestamp")
    private String timestamp;
    @JsonProperty("Topic")
    private String topic;
    @JsonProperty("Key")
    private String key;
    @JsonProperty("Value")
    private String value;
    @JsonProperty("Headers")
    private KafkaHeaders[] headers;

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getPartition() {
        return partition;
    }

    public void setPartition(int partition) {
        this.partition = partition;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getValue() {
        return value;
    }

    
    public void setValue(String value) {
        this.value = value;
    }

    public KafkaHeaders[] getHeaders() {
        return headers;
    }

    public void setHeaders(KafkaHeaders[] headers) {
        this.headers = headers;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

}
