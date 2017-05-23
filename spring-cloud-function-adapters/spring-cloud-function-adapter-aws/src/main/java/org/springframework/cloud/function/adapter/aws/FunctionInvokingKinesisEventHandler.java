/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.adapter.aws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Date;

import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord;

/**
 * @author Mark Fisher
 */
public class FunctionInvokingKinesisEventHandler extends SpringBootRequestHandler<KinesisEvent, String> {

	public FunctionInvokingKinesisEventHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	@Override
	protected String convertEvent(KinesisEvent event) {
		StringBuilder result = new StringBuilder();
		for (KinesisEventRecord record : event.getRecords()) {
			String id = record.getEventID();
			String name = record.getEventName();
			String source = record.getEventSource();
			Date timestamp = record.getKinesis().getApproximateArrivalTimestamp();
			String partitionKey = record.getKinesis().getPartitionKey();
			String sequenceNumber = record.getKinesis().getSequenceNumber();
			ByteBuffer data = record.getKinesis().getData();
			String dataString = new String(data.array(), Charset.forName("UTF-8"));
			result.append(String.format("id=%s,name=%s,source=%s,timestamp=%s,partitionKey=%s,sequenceNumber=%s,data=%s",
					id, name, source, timestamp, partitionKey, sequenceNumber, dataString));
		}
		return result.toString();
	}
}
