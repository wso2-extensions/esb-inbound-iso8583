# ISO8583 WSO2 EI Inbound Endpoint

The ISO8583 inbound endpoint supported via the ESB Profile of WSO2 Enterprise Integrator (WSO2 EI) is a listening inbound endpoint that can consume ISO8583 standard messages.
ISO8583 is an international messaging standard for financial transaction card originated messages, and is commonly used in transactions between devices such as point-of-sale(POS) terminals and automated teller machines(ATMs).
Although there are various versions of the ISO8583 standard, the ISO8583 inbound endpoint is developed based on the 1987 version. For more information on the ISO8583 standard, see [ISO8583 Documentation](https://en.wikipedia.org/wiki/ISO_8583).

## Compatibility

| Inbound Endpoint version | Supported jpos library version | Supported WSO2 ESB/EI version |
| ------------- | ---------------|------------- |
| [1.0.1](https://github.com/wso2-extensions/esb-inbound-iso8583/tree/org.wso2.carbon.inbound.iso8583-1.0.1) | 1.9.4 | EI 6.1.1, EI 6.4.0, EI 6.5.0   |


## Getting started
To get started, go to [Configuring ISO8583 Inbound Endpoint Operations](docs/config.md). Once you configure the ISO8583 inbound endpoint, the ESB Profile of WSO2 WSO2 EI can consume ISO8583 messages.


## Building from the source

Follow the steps given below to build the ISO8583 Inbound Endpoint from the source code.

1. Get a clone or download the source from [Github](https://github.com/wso2-extensions/esb-inbound-iso8583).
2. Run the following Maven command from the `esb-inbound-iso8583` directory: `mvn clean install`.
3. The JAR file for the ISO8583 Inbound Endpoint is created in the `esb-inbound-iso8583/target` directory.


## How you can contribute

As an open source project, WSO2 extensions welcome contributions from the community.
Check the [issue tracker](https://github.com/wso2-extensions/esb-inbound-iso8583/issues) for open issues that interest you. We look forward to receiving your contributions.
