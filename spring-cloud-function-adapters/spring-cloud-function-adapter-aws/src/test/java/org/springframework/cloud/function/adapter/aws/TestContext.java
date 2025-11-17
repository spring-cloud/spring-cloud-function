/*
 * Copyright 2019-present the original author or authors.
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

package org.springframework.cloud.function.adapter.aws;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * @author Oleg Zhurakousky
 *
 */
public class TestContext implements Context {

	@Override
	public String getAwsRequestId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getLogGroupName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getLogStreamName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getFunctionName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getFunctionVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getInvokedFunctionArn() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CognitoIdentity getIdentity() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ClientContext getClientContext() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getRemainingTimeInMillis() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getMemoryLimitInMB() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public LambdaLogger getLogger() {
		// TODO Auto-generated method stub
		return null;
	}

}
