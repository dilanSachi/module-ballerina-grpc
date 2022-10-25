// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/grpc;
import ballerina/protobuf.types.wrappers;
import ballerina/test;

@test:Config {enable: false}
isolated function testStringValueReturnWithBasicAuth() returns grpc:Error? {
    HelloWorld28Client helloWorldEp = check new ("http://localhost:9118");
    grpc:CredentialsConfig config = {
        username: "admin",
        password: "123"
    };

    grpc:ClientBasicAuthHandler handler = new (config);
    map<string|string[]> requestHeaders = {};
    requestHeaders = check handler.enrich(requestHeaders);

    wrappers:ContextString requestMessage = {
        content: "WSO2",
        headers: requestHeaders
    };
    string response = check helloWorldEp->testStringValueReturn(requestMessage);
    test:assertEquals(response, "Hello WSO2");
}

@test:Config {enable: false}
isolated function testStringValueReturnWithInvalidBasicAuth() returns grpc:Error? {
    HelloWorld28Client helloWorldEp = check new ("http://localhost:9118");
    grpc:CredentialsConfig config = {
        username: "admin",
        password: "1234"
    };

    grpc:ClientBasicAuthHandler handler = new (config);
    map<string|string[]> requestHeaders = {};
    requestHeaders = check handler.enrich(requestHeaders);

    wrappers:ContextString requestMessage = {
        content: "WSO2",
        headers: requestHeaders
    };
    string|grpc:Error response = helloWorldEp->testStringValueReturn(requestMessage);
    test:assertTrue(response is grpc:Error);
    test:assertEquals((<grpc:Error>response).message(), "Failed to authenticate username 'admin' from file user store.");
}

@test:Config {enable: false}
isolated function testStringValueReturnWithBasicAuthWithEmpty() returns grpc:Error? {
    map<string|string[]> requestHeaders = {};
    grpc:CredentialsConfig config = {
        username: "",
        password: "1234"
    };

    grpc:ClientBasicAuthHandler handler = new (config);
    map<string|string[]>|grpc:ClientAuthError result = handler.enrich(requestHeaders);
    test:assertTrue(result is grpc:Error);
    test:assertEquals((<grpc:Error>result).message(), "Failed to enrich request with Basic Auth token. Username or password cannot be empty.");
}

@test:Config {enable: false}
isolated function testStringValueReturnWithBasicAuthWithInvalidHeader() returns grpc:Error? {
    HelloWorld28Client helloWorldEp = check new ("http://localhost:9118");
    map<string|string[]> requestHeaders = {
        "authorization": "Bearer "
    };

    wrappers:ContextString requestMessage = {
        content: "WSO2",
        headers: requestHeaders
    };
    string|grpc:Error response = helloWorldEp->testStringValueReturn(requestMessage);
    test:assertTrue(response is grpc:Error);
    test:assertEquals((<grpc:Error>response).message(), "Empty authentication header.");
}
