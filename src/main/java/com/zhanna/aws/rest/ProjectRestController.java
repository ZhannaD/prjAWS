package com.zhanna.aws.rest;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.zhanna.aws.common.Constants;
import com.zhanna.aws.dto.UploadResponseDTO;
import com.zhanna.aws.model.Customer;
import com.zhanna.aws.model.Project;
import com.zhanna.aws.service.ProjectService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/projects")
@Slf4j
public class ProjectRestController {

	private final ProjectService projectService;

	public ProjectRestController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public String getTest() {

		return "Test Spring Boot Application";
	}

	@PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ResponseEntity<UploadResponseDTO>> uploadHandler(@RequestHeader HttpHeaders headers, @RequestPart("id") String id,
			@RequestPart("name") String name, @RequestPart("projectName") String projectName, /* @RequestPart("customer") Mono<Customer> customer, */
			@RequestPart("file") Flux<FilePart> parts) {

		String folderName = id + Constants.SEPARATE_NAME + name + Constants.SEPARATE_NAME + projectName;

		/* later from DB */
		Long custId = Long.parseLong(id);
		Timestamp tm = new Timestamp(System.currentTimeMillis());
		Project project = Project.builder().id(tm.getTime()).name(projectName).sourceLocation(folderName).build();
		Customer customer = Customer.builder().id(custId).name(name).build();// .projects(projects)
		List<Project> projects = customer.getProjects();
		projects.add(project);
		customer.setProjects(projects);

		return parts.ofType(FilePart.class).flatMap((part) -> projectService.saveFile(headers, part, folderName)).collect(Collectors.toList())
				.map((keys) -> ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponseDTO(HttpStatus.CREATED, keys)));

	}

}