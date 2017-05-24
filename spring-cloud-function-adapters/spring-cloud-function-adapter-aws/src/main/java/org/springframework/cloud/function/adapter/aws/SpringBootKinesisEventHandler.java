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

import java.util.List;

import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord;

/**
 * @author Mark Fisher
 */
public class SpringBootKinesisEventHandler
		extends SpringBootRequestHandler<KinesisEvent, String> {

	public SpringBootKinesisEventHandler() {
		super();
	}

	public SpringBootKinesisEventHandler(Class<?> configurationClass) {
		super(configurationClass);
	}

	@Override
	protected List<KinesisEventRecord> convertEvent(KinesisEvent event) {
		// TODO: maybe convert to List<Message>
		return event.getRecords();
	}
}
