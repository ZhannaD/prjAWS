package com.zhanna.aws.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder

public class Project {
	private Long id;
	private String name;
	// private Customer customer;
	private String sourceLocation;
}
