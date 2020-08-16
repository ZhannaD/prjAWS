package com.zhanna.aws.dto;

import java.util.List;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class UploadResponseDTO {
	HttpStatus status;
	String[] keys;

	public UploadResponseDTO() {
	}

	public UploadResponseDTO(HttpStatus status, List<String> keys) {
		this.status = status;
		this.keys = keys == null ? new String[] {} : keys.toArray(new String[] {});

	}
}