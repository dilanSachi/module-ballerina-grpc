# Proposal: Protocol Buffers Any Type Support for gRPC

_Owners_: @shafreenAnfar @daneshk @BuddhiWathsala @MadhukaHarith92 @dilanSachi  
_Reviewers_: @sameerajayasoma @shafreenAnfar @daneshk @MadhukaHarith92 @dilanSachi  
_Created_: 2022/04/07   
_Updated_: 2022/04/07  
_Issues_: [#2794](https://github.com/ballerina-platform/ballerina-standard-library/issues/2794)  

## Summary
Generating a stub using a Command-Line Interface (CLI), or a generation plugin is the very first step of a gRPC development. There are several CLIs and plugins have built for this use case, and protoc is one of the leading implementations at the moment. Generating a stub for a single protobuf file is the primary functionality of such tools, and generating a stub for a directory containing multiple proto files is comparatively a complex task. In this proposal, we are planning to handle such complex use cases in Ballerina gRPC CLI.

## Goals
- Ballerina CLI should have the capability to generate stubs for a given directory of proto files without missing any files.

## Motivation
When we implement a complex gRPC scenario using the `google.protobuf.Any` type, the message scope of such a use case is indefinite. Simply, the current Ballerina gRPC CLI takes only two instances of message definitions during the stub generation. Those two instances are,
- The messages that the gRPC service uses
- The messages defined explicitly in the input protobuf file
  However, when we are using a `google.protobuf.Any` type, the related message could be available in neither of the aforementioned two instances. There might be a proto file that is not imported into any protobuf file but is might use during the actual execution. Handling such a complex use case is the motivation of this proposal.

## Description
### Problem 01 - Support for Nested Directories
#### Problem Definition
As mentioned before, when we use the `google.protobuf.Any` type, the stub generation of CLI is getting complex. If there exists a message definition, which does not import to any service or does not declare in an input file, the relevant message definitions are not generated by the Ballerina CLI. For example, let’s take the following example.

```sh
proto
├── foo
│   └── bar
│       └── service.proto
├── messages1.proto
└── messages2.proto
```
Here, we are going to generate stubs for the `service.proto`. The `service.proto` file imports the `message1.proto`, and uses the message defined inside it. However, the `service.proto` does not import `message2.proto`. In the current Ballerina CLI, it only generates stubs related to `service.proto` and `message1.proto`. The stub definition for `message2.proto` is missing because it is not directly used in `service.proto`. However, other CLIs such as protoc and Java protoc Gradle plugin, generate `message2.proto`.

The behaviour of Java protoc CLI is obvious since it generates classes for all the proto files inside the pre-allocated proto directory. The behaviour of Go protoc CLI is as follows.

```sh
protoc --go_out=go2 --go-grpc_out=go2 proto/**.proto --proto_path=proto
```
In Golang, we can input proto files as a wildcard, and then it generates all the necessary stubs [1].

#### Proposed Solution
We have decided to follow the Go approach where we allow users to use wild cards as the proto command input.
```sh
bal grpc –input proto/**.proto –output ballerina-out
```

### Problem 02 - Centralized Proto Descriptor
#### Problem Definition
In our current Ballerina gRPC library, the generated stub contains a large single descriptor (root descriptor + descriptor map), which is related to the service and the messages that the service uses. But, when we address problem 01 and create several stubs for each proto file in a directory, maintaining such a huge descriptor will be a difficult task. Therefore, we should decentralize our descriptor (especially the descriptor map) like other languages. For example, Java has the descriptor details in the message class itself and Golang has it in the generated struct.
E.g., 
```go
…
var file_messages2_proto_rawDesc = []byte{
	0x0a, 0x0f, 0x6d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x73, 0x32, 0x2e, 0x70, 0x72, 0x6f, 0x74,
	0x6f, 0x22, 0x1c, 0x0a, 0x08, 0x4d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x32, 0x12, 0x10, 0x0a,
	0x03, 0x6d, 0x73, 0x67, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09, 0x52, 0x03, 0x6d, 0x73, 0x67, 0x42,
	0x17, 0x5a, 0x15, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x2e, 0x63, 0x6f, 0x6d, 0x2f, 0x6d,
	0x65, 0x73, 0x73, 0x61, 0x67, 0x65, 0x73, 0x32, 0x62, 0x06, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x33,
}
…
```
#### Proposed Solution
The proposed solution is to introduce a message-level annotation for the generated records. The descriptor value is related to a particular Proto file. For example, for each proto file (i.e. stub) there will be one descriptor variable. Then, all the recorded in the stub file use the same descriptor value.

```proto
message Person {
  string name = 1;
  int32 id = 2;
  string email = 3;
}
```
```ballerina
const string descriptorName = "0A2736385F73696D706C655F...";

@protobuf:Descriptor { value: descriptorName }
public type Person record {|
    string name = “”;
    int id = 0;
    string officeEmail = “”;
|};
```
Note: Here the descriptor value should be unique. It can be generated as follows:
```sh
descriptorName := <PROTO_FILE_NAME_IN_UPPER_CASE_WITH_UNDERSCORE>_DESC
```

Also, adding the annotations would not entirely solve the problem of correctly deserializing `Any` messages, which descriptors are not available at the root level. As the next step to entirely solve this problem, we should move the deserialization of `Any` type to `'any:unpack` API in the protobuf module.

### Problem 03 - Packaging Support
#### Problem Definition
The Ballerina CLI cannot create packages according to the protobuf definition. In protobuf, there is a way to specify Java and Go packages. However, for Ballerina, we do not have such an option.

Go Packages
```proto
option go_package = "example.com/messages1";
```

Java Packages
```proto
option java_package = "com.example.tutorial.protos";
```
#### Proposed Solution 
The details related to this can be found in (*Proposal: gRPC Packaging Support*)[https://github.com/ballerina-platform/ballerina-standard-library/issues/2948]

## Implementation
The tasks related to each problem are as follows:
- Problem 01
    - The changes need to be done only at the CLI level.
- Problem 02
    - We have to change both CLI and the runtime.
    - This needs to make sure of backward compatibility.
- Problem 03
    - Implementation needs to be done with the CLI and some changes to the runtime.

## Testing
For each problem, we should make sure of the backward compatibility.

## References
[1] https://github.com/golang/protobuf/issues/39
[2] https://developers.google.com/protocol-buffers/docs/proto#customoptions
