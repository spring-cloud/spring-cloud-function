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

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.kinesis.deagg.RecordDeaggregator;
import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

/**
 * @author Mark Fisher
 * @author Halvdan Hoem Grelland
 */
public class SpringBootKinesisEventHandler<E, O>
		extends SpringBootRequestHandler<KinesisEvent, O> {

	@Autowired
	private ObjectMapper mapper;

	@Autowired
	private FunctionInspector inspector;

	public SpringBootKinesisEventHandler() {
		super();
	}

	public SpringBootKinesisEventHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<O> handleRequest(KinesisEvent event, Context context) {
		return (List<O>) super.handleRequest(event, context);
	}

	@Override
	protected Object convertEvent(KinesisEvent event) {
		List<E> payloads = deserializePayloads(event.getRecords());

		if (functionAcceptsMessage()) {
			return wrapInMessages(payloads);
		} else {
			return payloads;
		}
	}

	private List<Message<E>> wrapInMessages(List<E> payloads) {
		return payloads.stream()
				.map(GenericMessage::new)
				.collect(Collectors.toList());
	}

	private List<E> deserializePayloads(List<KinesisEvent.KinesisEventRecord> records) {
		return RecordDeaggregator.deaggregate(records).stream()
				.map(this::deserializeUserRecord)
				.collect(toList());
	}

	@SuppressWarnings("unchecked")
	private E deserializeUserRecord(UserRecord userRecord) {
		try {
			byte[] jsonBytes = userRecord.getData().array();
			return (E) mapper.readValue(jsonBytes, getInputType());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot convert event", e);
		}
	}

	private boolean functionAcceptsMessage() {
		return inspector.isMessage(function());
	}
}
