/*******************************************************************************
 * Copyright (C) 2020 Eclipse Foundation
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipsefoundation.git.eca.service.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.eclipsefoundation.git.eca.api.ProjectsAPI;
import org.eclipsefoundation.git.eca.model.Project;
import org.eclipsefoundation.git.eca.service.ProjectsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.quarkus.runtime.Startup;

/**
 * Projects service implementation that handles pagination of data manually, as
 * well as makes use of a loading cache to have data be always available with as
 * little latency to the user as possible.
 * 
 * @author Martin Lowe
 */
@Startup
@ApplicationScoped
public class PagintationProjectsService implements ProjectsService {
	private static final Logger LOGGER = LoggerFactory.getLogger(PagintationProjectsService.class);

	@Inject
	@RestClient
	ProjectsAPI projects;
	// this class has a separate cache as this data is long to load and should be
	// always available.
	LoadingCache<String, List<Project>> internalCache;

	/**
	 * Initializes the internal loader cache and pre-populates the data with the one
	 * available key. If more than one key is used, eviction of previous results
	 * will happen and create degraded performance.
	 */
	@PostConstruct
	public void init() {
		// set up the internal cache
		this.internalCache = CacheBuilder.newBuilder().maximumSize(1).refreshAfterWrite(3600, TimeUnit.SECONDS)
				.build(new CacheLoader<String, List<Project>>() {
					@Override
					public List<Project> load(String key) throws Exception {
						return getProjectsInternal();
					}
				});

		// pre-cache the projects to reduce load time for other users
		LOGGER.debug("Starting pre-cache of projects");
		if (getProjects() == null) {
			LOGGER.warn(
					"Unable to populate pre-cache for Eclipse projects. Calls may experience degraded performance.");
		}
		LOGGER.debug("Completed pre-cache of projects assets");
	}

	@Override
	public List<Project> getProjects() {
		try {
			return internalCache.get("projects");
		} catch (ExecutionException e) {
			throw new RuntimeException("Could not load Eclipse projects", e);
		}
	}

	/**
	 * Logic for retrieving projects from API. Will loop until there are no more
	 * projects to be found
	 * 
	 * @return list of projects for the
	 */
	private List<Project> getProjectsInternal() {
		int page = 0;
		int pageSize = 100;
		List<Project> out = new LinkedList<>();
		List<Project> in;
		do {
			page++;
			in = projects.getProject(page, pageSize);
			out.addAll(in);
		} while (in != null && !in.isEmpty());
		return out;

	}

}
