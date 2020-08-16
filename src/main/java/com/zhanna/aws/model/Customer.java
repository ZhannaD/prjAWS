package com.zhanna.aws.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder

public class Customer {

	private Long id;
	private String name;
	// private String projectName;
	@Builder.Default
	private List<Project> projects = new ArrayList<>();
}
