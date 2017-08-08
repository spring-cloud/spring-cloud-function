/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.stream.binder.servlet;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.reactivestreams.Processor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.core.MessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.UnicastProcessor;

/**
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping("/${spring.cloud.stream.binder.servlet.prefix:stream}")
public class MessageController implements RouteRegistrar {

	public static final String ROUTE_KEY = "stream_routekey";

	private final ConcurrentMap<String, Bridge<Message<?>>> queues = new ConcurrentHashMap<>();

	private final ConcurrentMap<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

	private final Map<String, MessageChannel> inputs = new HashMap<>();

	private final Map<String, String> outputs = new HashMap<>();

	private final EnabledBindings bindings;

	private final MessagingTemplate template = new MessagingTemplate();

	private String prefix;

	public long timeoutSeconds = 10;

	private long receiveTimeoutMillis;

	private Set<String> routes = new LinkedHashSet<>();

	public MessageController(String prefix, EnabledBindings bindings) {
		if (!prefix.startsWith("/")) {
			prefix = "/" + prefix;
		}
		if (!prefix.endsWith("/")) {
			prefix = prefix + "/";
		}
		this.prefix = prefix;
		this.bindings = bindings;
		this.template.setReceiveTimeout(this.receiveTimeoutMillis);
	}

	public void setReceiveTimeoutSeconds(long receiveTimeoutMillis) {
		this.receiveTimeoutMillis = receiveTimeoutMillis;
		this.template.setReceiveTimeout(receiveTimeoutMillis);
	}

	public void setBufferTimeoutSeconds(long timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	@GetMapping(path = "/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public ResponseEntity<SseEmitter> sse(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestHeader HttpHeaders headers) throws IOException {
		Route route = output(path);
		String channel = route.getChannel();
		if (!bindings.getOutputs().contains(channel)) {
			return org.springframework.http.ResponseEntity.notFound().build();
		}
		Message<Collection<Object>> message = poll(route.getChannel(), route.getKey(),
				true);
		SseEmitter body = emit(route, message);
		return ResponseEntity.ok()
				.headers(HeaderUtils.fromMessage(message.getHeaders(), headers))
				.body(body);
	}

	@GetMapping("/**")
	public ResponseEntity<Object> supplier(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestHeader HttpHeaders headers,
			@RequestParam(required = false) boolean purge) {
		Route route = output(path);
		String channel = route.getChannel();
		if (bindings.getOutputs().contains(channel)) {
			Message<Collection<Object>> polled = poll(channel, route.getKey(), !purge);
			if (routes.contains(route.getKey()) || !polled.getPayload().isEmpty()
					|| route.getKey() == null) {
				return convert(polled, headers);
			}
		}
		route = input(path);
		channel = route.getChannel();
		if (!bindings.getInputs().contains(channel)) {
			return ResponseEntity.notFound().build();
		}
		String body = route.getKey();
		body = body.contains("/") ? body.substring(body.lastIndexOf("/") + 1) : body;
		path = path.replaceAll("/" + body, "");
		return string(path, body, headers);
	}

	@PostMapping(path = "/**", consumes = MediaType.TEXT_PLAIN_VALUE)
	public ResponseEntity<Object> string(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestBody String body, @RequestHeader HttpHeaders headers) {
		return function(path, body, headers);
	}

	@PostMapping(path = "/**", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Object> json(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestBody String body, @RequestHeader HttpHeaders headers) {
		return function(path, extract(body), headers);
	}

	private Object extract(String body) {
		body = body.trim();
		Object result = body;
		if (body.startsWith("[")) {
			result = JsonUtils.split(body);
		}
		return result;
	}

	@PostMapping("/**")
	public ResponseEntity<Object> function(
			@RequestAttribute("org.springframework.web.servlet.HandlerMapping.pathWithinHandlerMapping") String path,
			@RequestBody Object body, @RequestHeader HttpHeaders headers) {
		Route route = input(path);
		String channel = route.getChannel();
		if (!inputs.containsKey(channel)) {
			return ResponseEntity.notFound().build();
		}
		Collection<Object> collection;
		boolean single = false;
		if (body instanceof String) {
			body = extract((String) body);
		}
		if (body instanceof Collection) {
			@SuppressWarnings("unchecked")
			Collection<Object> list = (Collection<Object>) body;
			collection = list;
		}
		else {
			if (ObjectUtils.isArray(body)) {
				collection = Arrays.asList(ObjectUtils.toObjectArray(body));
			}
			else {
				single = true;
				collection = Arrays.asList(body);
			}
		}
		Map<String, Object> messageHeaders = new HashMap<>(HeaderUtils.fromHttp(headers));
		if (route.getKey() != null) {
			messageHeaders.put(ROUTE_KEY, route.getKey());
		}
		MessageChannel input = inputs.get(channel);
		Map<String, Object> outputHeaders = null;
		List<Object> results = new ArrayList<>();
		HttpStatus status = HttpStatus.ACCEPTED;
		// This is a total guess. We have no way to guarantee that the user will
		// implement a Processor so that inputs always get an output, so either
		// nothing might come back or there might be multiple outputs and we only get
		// one of them.
		if (this.outputs.containsKey(channel)) {
			for (Object payload : collection) {
				Message<?> result = template.sendAndReceive(input, MessageBuilder
						.withPayload(payload).copyHeadersIfAbsent(messageHeaders)
						.setHeader(MessageHeaders.REPLY_CHANNEL, outputs.get(channel))
						.build());
				if (result != null) {
					if (outputHeaders == null) {
						outputHeaders = new LinkedHashMap<>(result.getHeaders());
					}
					results.add(result.getPayload());
				}
			}
			status = HttpStatus.OK;
			if (results.isEmpty()) {
				// If nothing came back, just assume it was intentional, and say that
				// we accepted the inputs.
				status = HttpStatus.ACCEPTED;
				results.addAll(collection);
			}
		}
		else {
			for (Object payload : collection) {
				template.send(input, MessageBuilder.withPayload(payload)
						.copyHeadersIfAbsent(messageHeaders).build());
			}
			outputHeaders = messageHeaders;
			results.addAll(collection);
		}
		if (outputHeaders == null) {
			outputHeaders = new LinkedHashMap<>();
		}
		outputHeaders.put(ROUTE_KEY, route.getKey());
		if (single && results.size() == 1) {
			body = results.get(0);
		}
		else {
			body = results;
		}
		if (headers.getContentType() != null
				&& headers.getContentType().includes(MediaType.APPLICATION_JSON)
				&& body.toString().contains("\"")) {
			body = body.toString();
		}
		return convert(status, MessageBuilder.withPayload(body)
				.copyHeadersIfAbsent(outputHeaders).build(), headers);
	}

	private ResponseEntity<Object> convert(Message<?> message, HttpHeaders request) {
		return convert(HttpStatus.OK, message, request);
	}

	private ResponseEntity<Object> convert(HttpStatus status, Message<?> message,
			HttpHeaders request) {
		return ResponseEntity.status(status)
				.headers(HeaderUtils.fromMessage(message.getHeaders(), request))
				.body(message.getPayload());
	}

	private SseEmitter emit(Route route, Message<Collection<Object>> message)
			throws IOException {
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
		String path = route.getPath();
		if (!emitters.containsKey(path)) {
			emitters.putIfAbsent(path, new HashSet<>());
		}
		emitters.get(path).add(emitter);
		emitter.onCompletion(() -> emitters.get(path).remove(emitter));
		emitter.onTimeout(() -> emitters.get(path).remove(emitter));
		for (Object body : message.getPayload()) {
			emitter.send(body);
		}
		return emitter;
	}

	public void reset() {
		queues.clear();
	}

	private Message<Collection<Object>> poll(String channel, String key,
			boolean requeue) {
		List<Object> list = new ArrayList<>();
		List<Message<?>> messages = new ArrayList<>();
		Bridge<Message<?>> queue = queues.get(new Route(key, channel).getPath());
		if (queue != null) {
			queue.receive().subscribe(message -> {
				messages.add(message);
				list.add(message.getPayload());
			});
			if (!requeue) {
				queue.reset();
			}
		}
		MessageBuilder<Collection<Object>> builder = MessageBuilder.withPayload(list);
		if (!messages.isEmpty()) {
			builder.copyHeadersIfAbsent(messages.get(0).getHeaders());
		}
		return builder.build();
	}

	public void subscribe(String name, SubscribableChannel outboundBindTarget) {
		this.outputs.put(bindings.getInput(name), name);
		outboundBindTarget.subscribe(message -> this.append(name, message));
	}

	private void append(String name, Message<?> message) {
		String key = (String) message.getHeaders().get(ROUTE_KEY);
		if (message.getHeaders().getReplyChannel() instanceof MessageChannel) {
			MessageChannel replyChannel = (MessageChannel) message.getHeaders()
					.getReplyChannel();
			replyChannel.send(message);
			return;
		}
		Route route = new Route(key, name);
		String path = route.getPath();
		if (!queues.containsKey(path)) {
			Bridge<Message<?>> flux = new Bridge<>();
			queues.putIfAbsent(path, flux);
		}
		queues.get(path).send(message);
		if (emitters.containsKey(path)) {
			Set<SseEmitter> list = new HashSet<>(emitters.get(path));
			for (SseEmitter emitter : list) {
				try {
					emitter.send(message.getPayload());
				}
				catch (IOException e) {
					emitters.get(path).remove(emitter);
				}
			}
		}
	}

	public void bind(String name, String group, MessageChannel inputTarget) {
		this.inputs.put(name, inputTarget);
	}

	public Route output(String path) {
		return new Route(prefix, path,
				bindings.getOutputs().size() == 1
						? bindings.getOutputs().iterator().next()
						: "output");
	}

	public Route input(String path) {
		return new Route(prefix, path,
				bindings.getInputs().size() == 1 ? bindings.getInputs().iterator().next()
						: "input");
	}

	private class Route {
		private String key;
		private String channel;
		private String path;

		private Route(String prefix, String path, String defaultChannel) {
			String channel;
			String route = null;
			// Strip the prefix first
			if (path.length() > prefix.length()) {
				path = path.substring(prefix.length());
			}
			else {
				path = "";
			}
			// Then extract the first segment of the path, and call it a "channel"
			String[] paths = path.split("/");
			if (paths.length > 1) {
				channel = paths[0];
				route = path.substring(channel.length() + 1, path.length());
			}
			else {
				channel = path;
			}
			// If it's not actually a channel we know about, use the default, and call the
			// whole path a "route"
			if (!bindings.getInputs().contains(channel)
					& !bindings.getOutputs().contains(channel)) {
				channel = defaultChannel;
				route = path.length() > 0 ? path : null;
			}
			this.channel = channel;
			this.key = route;
			this.path = key != null ? key + "/" + channel : channel;
		}

		public Route(String key, String channel) {
			this.key = key;
			this.channel = channel;
			this.path = key != null ? key + "/" + channel : channel;
		}

		public String getPath() {
			return path;
		}

		public String getKey() {
			return key;
		}

		public String getChannel() {
			return channel;
		}
	}

	private class Bridge<T> {

		private Processor<T, T> emitter;
		private Flux<T> sink;

		public Bridge() {
			reset();
		}

		public void reset() {
			this.emitter = UnicastProcessor.<T>create().serialize();
			this.sink = Flux.from(emitter).replay().autoConnect()
					.take(Duration.ofSeconds(timeoutSeconds));
		}

		public void send(T item) {
			emitter.onNext(item);
		}

		public Flux<T> receive() {
			return sink;
		}
	}

	@Override
	public void registerRoutes(Set<String> routes) {
		this.routes.addAll(routes);
	}

	@Override
	public void unregisterRoutes(Set<String> routes) {
		this.routes.removeAll(routes);
		for (String path : routes) {
			queues.remove(output(prefix + path).getPath());
		}
	}

}
