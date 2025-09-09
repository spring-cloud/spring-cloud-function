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

package com.example.azure.di.azureblobtriggerdemo;

import java.util.function.Function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.BlobInput;
import com.microsoft.azure.functions.annotation.BlobOutput;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.StorageAccount;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Azure Functions with Azure Storage Blob.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-blob-trigger?tabs=java
 *
 * The Blob storage binding is part of an extension bundle, which is specified in your host.json project file.
 */

@Component
public class MyBlobFunction {

    @Autowired
    private Function<byte[], byte[]> uppercase;

    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are
     * provided as input to this function. The location of the blob is provided in the path parameter. Example -
     * test-trigger/{name} below
     */
    @FunctionName("BlobTrigger")
    @StorageAccount("AzureWebJobsStorage")
    public void blobTest(
            @BlobTrigger(name = "triggerBlob", path = "test-trigger/{name}", dataType = "binary") byte[] triggerBlob,
            @BindingName("name") String fileName,
            @BlobInput(name = "inputBlob", path = "test-input/{name}", dataType = "binary") byte[] inputBlob,
            @BlobOutput(name = "outputBlob", path = "test-output/{name}", dataType = "binary") OutputBinding<byte[]> outputBlob,
            final ExecutionContext context) {

        context.getLogger().info("Java Blob trigger function blobTest processed a blob.\n Name: "
                + fileName + "\n Size: " + triggerBlob.length + " Bytes");

        outputBlob.setValue(uppercase.apply(inputBlob));
    }
}
