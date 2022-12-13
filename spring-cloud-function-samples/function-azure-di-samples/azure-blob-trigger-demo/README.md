

The Blob storage binding is part of an [extension bundle](https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-register#extension-bundles), which is specified in your [host.json](https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-blob?tabs=in-process%2Cextensionv5%2Cextensionv3&pivots=programming-language-java#install-bundle) project file. 


The Blob storage trigger starts a function when a new or updated blob is detected. The blob contents are provided as [input](https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-blob-input?tabs=in-process%2Cextensionv5&pivots=programming-language-java) to the function.