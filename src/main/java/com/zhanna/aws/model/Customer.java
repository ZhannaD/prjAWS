package com.zhanna.aws.model;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class Customer {

	private Long id;
	private String name;
	@Builder.Default
	private List<Project> projects = new ArrayList<>();
}
