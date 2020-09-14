package com.zhanna.aws.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.FilePart;

import reactor.core.publisher.Mono;

public interface ProjectService {
	Mono<String> saveFile(HttpHeaders headers, FilePart part, String folderName);
}
