/*
 * Copyright 2021-present the original author or authors.
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

package com.example.azure.di.timetriggerdemo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FixedDelayRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class UppercaseHandler {

    public static String EXECUTION_CONTEXT = "executionContext";

    private static AtomicInteger count = new AtomicInteger();

    @Autowired
    private Consumer<Message<String>> uppercase;

    @FunctionName("uppercase")
    @FixedDelayRetry(maxRetryCount = 4, delayInterval = "00:00:10")
    public void execute(@TimerTrigger(name = "keepAliveTrigger", schedule = "0 */1 * * * *") String timerInfo,
            ExecutionContext context) {

        Message<String> message = MessageBuilder
                .withPayload(timerInfo)
                .setHeader(EXECUTION_CONTEXT, context)
                .build();

        this.uppercase.accept(message);
    }

    @FunctionName("uppercaseExpRetry")
    @ExponentialBackoffRetry(maxRetryCount = 4, maximumInterval = "00:15:00", minimumInterval = "00:00:03")
    public void executeExpRetry(@TimerTrigger(name = "keepAliveTrigger", schedule = "*/10 * * * * *") String timerInfo,
            ExecutionContext context) {

        if (count.incrementAndGet() < 3) {
            context.getLogger().info("EMULATE ERROR# " + count.get());
            throw new IllegalStateException("Emulated ERROR# " + count.get());
        }

        context.getLogger().info("ERRORLESS EXECUTION");

        Message<String> message = MessageBuilder
                .withPayload(timerInfo)
                .setHeader(EXECUTION_CONTEXT, context)
                .build();

        this.uppercase.accept(message);
    }
}
