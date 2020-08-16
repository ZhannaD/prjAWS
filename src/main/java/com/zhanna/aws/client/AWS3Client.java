package com.zhanna.aws.client;

import org.springframework.beans.factory.annotation.Autowired;

import com.zhanna.aws.S3ClientConfigurarionProperties;

import lombok.Data;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Data
public class AWS3Client implements AWSClient {

	@Autowired
	private S3AsyncClient s3client;
	@Autowired
	private S3ClientConfigurarionProperties s3config;

	@Override
	public AWS3Client createAWSClient() {
		return this;
	}

}
