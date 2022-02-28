/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.function.context.catalog.observability;

import io.micrometer.api.instrument.observation.Observation;
import io.micrometer.api.lang.Nullable;

import org.springframework.cloud.function.context.catalog.SimpleFunctionRegistry;

/**
 * Context.
 *
 * @author Marcin Grzejszczak
 * @since 4.0.0
 */
public class FunctionContext extends Observation.Context {

	private final SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction;

	private Object input;

	private Object modifiedInput;

	private Object output;

	private Object modifiedOutput;

	public FunctionContext(SimpleFunctionRegistry.FunctionInvocationWrapper targetFunction) {
		this.targetFunction = targetFunction;
	}

	public FunctionContext withInput(Object input) {
		this.input = input;
		this.modifiedInput = input;
		return this;
	}

	public FunctionContext withOutput(Object output) {
		this.output = output;
		this.modifiedOutput = output;
		return this;
	}

	@Nullable
	public Object getInput() {
		return input;
	}

	public SimpleFunctionRegistry.FunctionInvocationWrapper getTargetFunction() {
		return targetFunction;
	}

	@Nullable
	public Object getModifiedInput() {
		return modifiedInput;
	}

	public void setModifiedInput(Object modifiedInput) {
		this.modifiedInput = modifiedInput;
	}

	@Nullable
	public Object getOutput() {
		return output;
	}

	public void setOutput(Object output) {
		this.output = output;
	}

	@Nullable
	public Object getModifiedOutput() {
		return modifiedOutput;
	}

	public void setModifiedOutput(Object modifiedOutput) {
		this.modifiedOutput = modifiedOutput;
	}
}
