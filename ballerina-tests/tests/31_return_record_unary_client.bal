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
import ballerina/test;

@test:Config {enable: false}
isolated function testUnaryRecordValueReturn() returns grpc:Error? {
    HelloWorld31Client ep = check new ("http://localhost:9121");
    SampleMsg31 reqMsg = {name: "WSO2", id: 8};
    SampleMsg31 response = check ep->sayHello(reqMsg);
    test:assertEquals(response.name, "Ballerina Lang");
    test:assertEquals(response.id, 7);
}

@test:Config {enable: false}
isolated function testUnaryErrorReturn() returns grpc:Error? {
    HelloWorld31Client ep = check new ("http://localhost:9121");
    SampleMsg31 reqMsg = {id: 8};
    var response = ep->sayHello(reqMsg);
    test:assertTrue(response is grpc:Error);
    test:assertEquals((<grpc:Error>response).message(), "Name must not be empty.");
}
