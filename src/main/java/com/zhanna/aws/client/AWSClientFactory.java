package com.zhanna.aws.client;

import org.springframework.stereotype.Component;

@Component
public class AWSClientFactory {

	AWSClient client = null;

	public AWSClient getAWSClient(ClientType type) {

		switch (type) {
		case S3CLIENT:
			client = new AWS3Client();

		default:
			break;
		}

		return client;
	}
}
