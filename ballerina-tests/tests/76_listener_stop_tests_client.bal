// Copyright (c) 2022 WSO2 LLC. (http://www.wso2.com) All Rights Reserved.
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
import ballerina/io;
//import ballerina/lang.runtime;

@test:Config {enable: true}
function testListenerGracefulStopTest() returns error? {
    grpc:Listener 'listener = check new (9176);
    grpc:Service stopService =
    @grpc:Descriptor {value: LISTENER_STOP_TESTS_DESC}
    service object {
        remote function basicRpc(string req) returns string? {
            io:println("received req - " + req);
            return "Hey " + req;
        }
    };
    check 'listener.attach(stopService, "StopService");
    check 'listener.start();

    StopServiceClient 'client = check new ("http://localhost:9176");
    string result = check 'client->basicRpc("gRPC");
    test:assertEquals(result, "Hey gRPC");

    check 'listener.immediateStop();
    //runtime:sleep(5);
    result = check 'client->basicRpc("Ballerina");
    test:assertEquals(result, "Hey Ballerina");
    test:assertFail("Failing");
}
