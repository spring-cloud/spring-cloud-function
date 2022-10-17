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

package example;

import com.microsoft.azure.functions.BrokerAuthenticationMode;
import com.microsoft.azure.functions.BrokerProtocol;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.KafkaOutput;
import com.microsoft.azure.functions.annotation.KafkaTrigger;

import org.springframework.cloud.function.adapter.azure.FunctionInvoker;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class UppercaseHandler extends FunctionInvoker<Message<String>, String> {

    
    @FunctionName("KafkaTrigger")
    public void execute(
        @KafkaTrigger(
            name = "KafkaTrigger", 
            topic = "%TriggerKafkaTopic%", 
            brokerList = "%BrokerList%", 
            consumerGroup = "$Default", 
            username = "%ConfluentCloudUsername%", 
            password = "ConfluentCloudPassword", 
            authenticationMode = BrokerAuthenticationMode.PLAIN, 
            protocol = BrokerProtocol.PLAINTEXT,
            // protocol = BrokerProtocol.SASLSSL,
            // sslCaLocation = "confluent_cloud_cacert.pem", // Enable this line for windows.
            dataType = "string") String kafkaEventData,
        @KafkaOutput(
            name = "kafkaOutput",
            topic = "output",  
            brokerList="%BrokerList%",
            username = "%ConfluentCloudUsername%", 
            password = "ConfluentCloudPassword",
            authenticationMode = BrokerAuthenticationMode.PLAIN,
            // sslCaLocation = "confluent_cloud_cacert.pem", // Enable this line for windows.  
            protocol = BrokerProtocol.PLAINTEXT
            //protocol = BrokerProtocol.SASLSSL
        )  OutputBinding<String> output,
        final ExecutionContext context) {

        context.getLogger().info(kafkaEventData);

        Message<String> message = MessageBuilder.withPayload(kafkaEventData).build();

        String response = handleRequest(message, context);

        output.setValue(response);
    }
}
