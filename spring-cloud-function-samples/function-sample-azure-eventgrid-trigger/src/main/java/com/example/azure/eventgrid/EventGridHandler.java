/*
 * Copyright 2024-2024 the original author or authors.
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

package com.example.azure.eventgrid;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.stereotype.Component;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.jackson.JsonFormat;

@Component
public class EventGridHandler {

	private static final Logger logger = LoggerFactory.getLogger(EventGridHandler.class);
	private static final String WEBHOOK_REQUEST_ORIGIN = "WebHook-Request-Origin";
	private static final String WEBHOOK_ALLOWED_ORIGIN = "WebHook-Allowed-Origin";
	private static final String CONTENT_TYPE_CLOUDEVENTS = "application/cloudevents+json";

	@Autowired
	private FunctionCatalog functionCatalog;

	private final ObjectMapper objectMapper = new ObjectMapper()
		.registerModule(JsonFormat.getCloudEventJacksonModule());

	/**
	 * Azure Event Grid webhook endpoint that handles:
	 * 1. Webhook validation requests (OPTIONS method)
	 * 2. CloudEvent format events (POST method)
	 * 3. Event Grid format events (POST method) - converts to CloudEvent
	 */
	@FunctionName("eventGridWebhook")
	public HttpResponseMessage eventGridWebhook(
			@HttpTrigger(name = "req",
						methods = { HttpMethod.POST, HttpMethod.OPTIONS },
						authLevel = AuthorizationLevel.ANONYMOUS,
						route = "eventgrid")
			HttpRequestMessage<Optional<String>> request,
			ExecutionContext context) {

		logger.info("EventGrid webhook triggered with method: {}", request.getHttpMethod());

		// Handle webhook validation for Event Grid subscription
		if (HttpMethod.OPTIONS.equals(request.getHttpMethod())) {
			return handleWebhookValidation(request, context);
		}

		// Handle event processing
		if (HttpMethod.POST.equals(request.getHttpMethod())) {
			return handleEventProcessing(request, context);
		}

		return request.createResponseBuilder(HttpStatus.METHOD_NOT_ALLOWED)
			.body("Method not allowed")
			.build();
	}

	/**
	 * Handles Event Grid webhook validation according to CloudEvents webhook spec
	 */
	private HttpResponseMessage handleWebhookValidation(HttpRequestMessage<Optional<String>> request,
			ExecutionContext context) {

		logger.info("Handling webhook validation request");

        String origin = getHeaderIgnoreCase(request.getHeaders(), WEBHOOK_REQUEST_ORIGIN);
        if (origin == null) {
            logger.warn("Webhook validation: missing WebHook-Request-Origin header, responding with wildcard");
            // Be permissive to pass Azure CLI/portal validation flows that omit the header
            return request.createResponseBuilder(HttpStatus.OK)
                .header(WEBHOOK_ALLOWED_ORIGIN, "*")
                .build();
        }

        logger.info("Webhook validation origin: {}", origin);
        return request.createResponseBuilder(HttpStatus.OK)
            .header(WEBHOOK_ALLOWED_ORIGIN, origin)
            .build();
	}

	/**
	 * Handles event processing for both CloudEvent and Event Grid formats
	 */
	private HttpResponseMessage handleEventProcessing(HttpRequestMessage<Optional<String>> request,
			ExecutionContext context) {

		try {
			String requestBody = request.getBody().orElse("");
			logger.info("Processing event with body: {}", requestBody);

            String contentType = Optional.ofNullable(getHeaderIgnoreCase(request.getHeaders(), "Content-Type")).orElse("");

            if (hasCloudEventBinaryHeaders(request.getHeaders())) {
                // Handle CloudEvent binary-mode
                return handleCloudEventBinary(request, requestBody, contentType, context);
            }
            else if (contentType.toLowerCase().contains(CONTENT_TYPE_CLOUDEVENTS)) {
				// Handle CloudEvent format
				return handleCloudEvent(request, requestBody, context);
            } else {
				// Handle Event Grid format - convert to CloudEvent
				return handleEventGridFormat(request, requestBody, context);
			}

		} catch (Exception e) {
			logger.error("Error processing event", e);
			return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Error processing event: " + e.getMessage())
				.build();
		}
	}

	/**
	 * Handles CloudEvent format events
	 */
	private HttpResponseMessage handleCloudEvent(HttpRequestMessage<Optional<String>> request,
			String requestBody, ExecutionContext context) throws IOException {

		logger.info("Processing CloudEvent format");

		CloudEvent cloudEvent = objectMapper.readValue(requestBody, CloudEvent.class);

		// Process the CloudEvent using Spring Cloud Function
		Function<CloudEvent, String> processor = functionCatalog.lookup("processEvent");
		String result = processor.apply(cloudEvent);

		logger.info("CloudEvent processed successfully: {}", result);

		return request.createResponseBuilder(HttpStatus.OK)
			.header("content-type", "application/json")
			.body(Map.of("status", "success", "message", result))
			.build();
	}

	/**
	 * Handles Event Grid format events and converts them to CloudEvent
	 */
	private HttpResponseMessage handleEventGridFormat(HttpRequestMessage<Optional<String>> request,
			String requestBody, ExecutionContext context) throws IOException {

		logger.info("Processing Event Grid format");

		// Parse Event Grid format (array of events)
		List<Map<String, Object>> eventGridEvents = objectMapper.readValue(
			requestBody, new TypeReference<List<Map<String, Object>>>() {});

		StringBuilder results = new StringBuilder();

		for (Map<String, Object> eventGridEvent : eventGridEvents) {
			// Check for subscription validation event
			String eventType = (String) eventGridEvent.get("eventType");
			if ("Microsoft.EventGrid.SubscriptionValidationEvent".equals(eventType)) {
				logger.info("Handling subscription validation event");
				Map<String, Object> data = (Map<String, Object>) eventGridEvent.get("data");
				String validationCode = (String) data.get("validationCode");

				return request.createResponseBuilder(HttpStatus.OK)
					.header("content-type", "application/json")
					.body(Map.of("validationResponse", validationCode))
					.build();
			}

			// Convert Event Grid event to CloudEvent
			CloudEvent cloudEvent = convertEventGridToCloudEvent(eventGridEvent);

			// Process the CloudEvent
			Function<CloudEvent, String> processor = functionCatalog.lookup("processEvent");
			String result = processor.apply(cloudEvent);

			results.append(result).append("; ");
		}

		logger.info("Event Grid events processed successfully");

		return request.createResponseBuilder(HttpStatus.OK)
			.header("content-type", "application/json")
			.body(Map.of("status", "success", "processedEvents", eventGridEvents.size(),
						 "results", results.toString()))
			.build();
	}

	/**
	 * Converts Event Grid format to CloudEvent format
	 */
	private CloudEvent convertEventGridToCloudEvent(Map<String, Object> eventGridEvent) throws IOException {
		CloudEventBuilder builder = CloudEventBuilder.v1()
			.withId((String) eventGridEvent.get("id"))
			.withType((String) eventGridEvent.get("eventType"))
			.withSource(URI.create((String) eventGridEvent.get("topic")));

		if (eventGridEvent.containsKey("subject")) {
			builder.withSubject((String) eventGridEvent.get("subject"));
		}

		if (eventGridEvent.containsKey("eventTime")) {
			builder.withTime(java.time.OffsetDateTime.parse((String) eventGridEvent.get("eventTime")));
		}

		if (eventGridEvent.containsKey("data")) {
			String dataJson = objectMapper.writeValueAsString(eventGridEvent.get("data"));
			builder.withData("application/json", dataJson.getBytes(StandardCharsets.UTF_8));
		}

		// Add Event Grid specific extensions
		if (eventGridEvent.containsKey("dataVersion")) {
			builder.withExtension("dataversion", String.valueOf(eventGridEvent.get("dataVersion")));
		}
		if (eventGridEvent.containsKey("metadataVersion")) {
			builder.withExtension("metadataversion", String.valueOf(eventGridEvent.get("metadataVersion")));
		}

		return builder.build();
	}

	/**
	 * Detects presence of any ce-* headers indicating CloudEvents binary-mode.
	 */
	private static boolean hasCloudEventBinaryHeaders(Map<String, String> headers) {
		if (headers == null || headers.isEmpty()) {
			return false;
		}
		for (String key : headers.keySet()) {
			if (key != null && key.toLowerCase().startsWith("ce-")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Build a CloudEvent from ce-* headers and body (binary-mode).
	 */
	private static CloudEvent convertBinaryHeadersToCloudEvent(Map<String, String> headers, String body,
			String contentType) {
		CloudEventBuilder builder = CloudEventBuilder.v1();

		String id = getHeaderIgnoreCase(headers, "ce-id");
		String type = getHeaderIgnoreCase(headers, "ce-type");
		String source = getHeaderIgnoreCase(headers, "ce-source");
		String subject = getHeaderIgnoreCase(headers, "ce-subject");
		String time = getHeaderIgnoreCase(headers, "ce-time");

		if (id != null) {
			builder.withId(id);
		}
		if (type != null) {
			builder.withType(type);
		}
		if (source != null) {
			builder.withSource(URI.create(source));
		}
		if (subject != null) {
			builder.withSubject(subject);
		}
		if (time != null) {
			builder.withTime(java.time.OffsetDateTime.parse(time));
		}

		if (body != null && !body.isEmpty()) {
			String dataContentType = (contentType == null || contentType.isEmpty()) ? "application/json" : contentType;
			builder.withData(dataContentType, body.getBytes(StandardCharsets.UTF_8));
		}

		// Copy any additional ce-* headers as extensions
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			String key = entry.getKey();
			if (key != null && key.toLowerCase().startsWith("ce-") &&
					!key.equalsIgnoreCase("ce-id") && !key.equalsIgnoreCase("ce-type") &&
					!key.equalsIgnoreCase("ce-source") && !key.equalsIgnoreCase("ce-subject") &&
					!key.equalsIgnoreCase("ce-time") && !key.equalsIgnoreCase("ce-specversion")) {
				String extName = key.substring(3); // drop 'ce-'
				builder.withExtension(extName, entry.getValue());
			}
		}

		return builder.build();
	}

	/**
	 * Retrieve header value ignoring case.
	 */
	private static String getHeaderIgnoreCase(Map<String, String> headers, String name) {
		if (headers == null || name == null) {
			return null;
		}
		String lower = name.toLowerCase();
		for (Map.Entry<String, String> e : headers.entrySet()) {
			if (e.getKey() != null && e.getKey().toLowerCase().equals(lower)) {
				return e.getValue();
			}
		}
		return null;
	}

    /**
     * Handles CloudEvent binary-mode events (ce-* headers + data in body)
     */
    private HttpResponseMessage handleCloudEventBinary(HttpRequestMessage<Optional<String>> request,
            String requestBody, String contentType, ExecutionContext context) throws IOException {

        logger.info("Processing CloudEvent binary-mode");

        CloudEvent cloudEvent = convertBinaryHeadersToCloudEvent(request.getHeaders(), requestBody, contentType);

        // Process the CloudEvent using Spring Cloud Function
        Function<CloudEvent, String> processor = functionCatalog.lookup("processEvent");
        String result = processor.apply(cloudEvent);

        logger.info("CloudEvent (binary) processed successfully: {}", result);

        return request.createResponseBuilder(HttpStatus.OK)
            .header("content-type", "application/json")
            .body(Map.of("status", "success", "message", result))
            .build();
    }
}
