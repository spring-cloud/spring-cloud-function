FROM springcloudstream/azure-functions-java17:1.0.0

COPY ./target/azure-functions /src/java-function-app

RUN  mkdir -p /home/site/wwwroot &&  \
     cd /src/java-function-app && \
     cd $(ls -d */|head -n 1) && \
     cp -a . /home/site/wwwroot

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true
