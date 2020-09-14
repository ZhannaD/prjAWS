package com.zhanna.aws.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class Project {
	private Long id;
	private String name;
	private String sourceLocation;
}
