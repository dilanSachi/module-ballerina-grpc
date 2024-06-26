// Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import ballerina/io;
import ballerina/protobuf.types.wrappers;

listener grpc:Listener ep = new (8981);

@grpc:ServiceDescriptor {descriptor: ROOT_DESCRIPTOR_PROGRAM_ANALYZER, descMap: getDescriptorMapProgramAnalyzer()}
service "BalProgramAnalyzer" on ep {

    isolated remote function syntaxTree() returns wrappers:ContextString|error {
        string syntaxTree = check io:fileReadString("./resources/grpc.json");

        // Enable the gRPC Gzip compression
        map<string|string[]> compression = grpc:setCompression(grpc:GZIP);
        return {content: syntaxTree, headers: compression};
    }
}

