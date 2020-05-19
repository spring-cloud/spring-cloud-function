package com.example;

import java.util.Map;

/**
 * A class that can be mapped to the GCF Pub/Sub Message event type. This is for use in
 * the background functions.
 *
 * <p>See the PubSubMessage definition for reference:
 * https://cloud.google.com/pubsub/docs/reference/rest/v1/PubsubMessage
 *
 * @author Mike Eltsufin
 */
public class PubSubMessage {

	private String data;

	private Map<String, String> attributes;

	private String messageId;

	private String publishTime;

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes;
	}

	public String getMessageId() {
		return messageId;
	}

	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}

	public String getPublishTime() {
		return publishTime;
	}

	public void setPublishTime(String publishTime) {
		this.publishTime = publishTime;
	}

}
