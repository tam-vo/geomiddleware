/*******************************************************************************
 *   Gisgraphy Project 
 * 
 *   This library is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU Lesser General Public
 *   License as published by the Free Software Foundation; either
 *   version 2.1 of the License, or (at your option) any later version.
 * 
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *   Lesser General Public License for more details.
 * 
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the Free Software
 *   Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307, USA
 * 
 *  Copyright 2008  Gisgraphy project 
 *  David Masclet <davidmasclet@gisgraphy.com>
 *  
 *  
 *******************************************************************************/
package com.gisgraphy.domain.geoloc.service.fulltextsearch;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.util.Assert;

import com.gisgraphy.domain.geoloc.entity.GisFeature;
import com.gisgraphy.domain.geoloc.service.ServiceException;
import com.gisgraphy.domain.repository.GisFeatureDao;
import com.gisgraphy.domain.valueobject.Constants;
import com.gisgraphy.domain.valueobject.FulltextResultsDto;
import com.gisgraphy.service.IStatsUsageService;
import com.gisgraphy.stats.StatsUsageType;

/**
 * Default (threadsafe) implementation of {@link IFullTextSearchEngine}
 * 
 * @author <a href="mailto:david.masclet@gisgraphy.com">David Masclet</a>
 */
public class FullTextSearchEngine implements IFullTextSearchEngine {

	protected static final Logger logger = LoggerFactory
			.getLogger(FullTextSearchEngine.class);

	private HttpClient httpClient;

	private IsolrClient solrClient;

	@Autowired
	@Qualifier("gisFeatureDao")
	private GisFeatureDao gisFeatureDao;

	@Resource
	IStatsUsageService statsUsageService;

	/**
	 * Default constructor needed by cglib and spring
	 */
	@SuppressWarnings("unused")
	private FullTextSearchEngine() {
		super();
	}

	public FullTextSearchEngine(GisFeatureDao gisFeatureDao,
			IStatsUsageService statsUsageService) {
		this.gisFeatureDao = gisFeatureDao;
		this.statsUsageService = statsUsageService;
	}

	public FullTextSearchEngine(IsolrClient solrClient,
			GisFeatureDao gisFeatureDao, IStatsUsageService statsUsageService) {
		this(gisFeatureDao, statsUsageService);
		this.solrClient = solrClient;
	}

	/**
	 * @param multiThreadedHttpConnectionManager
	 *            The
	 * @link {@link MultiThreadedHttpConnectionManager} that the fulltext search
	 *       engine will use
	 * @throws FullTextSearchException
	 *             If an error occured
	 */
	@Autowired
	public FullTextSearchEngine(
			@Qualifier("multiThreadedHttpConnectionManager") MultiThreadedHttpConnectionManager multiThreadedHttpConnectionManager)
			throws FullTextSearchException {
		Assert.notNull(multiThreadedHttpConnectionManager,
				"multiThreadedHttpConnectionManager can not be null");
		this.httpClient = new HttpClient(multiThreadedHttpConnectionManager);
		if (this.httpClient == null) {
			throw new FullTextSearchException(
					"Can not instanciate http client with multiThreadedHttpConnectionManager : "
							+ multiThreadedHttpConnectionManager);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.gisgraphy.domain.geoloc.service.fulltextsearch.IFullTextSearchEngine
	 * #executeAndSerialize
	 * (com.gisgraphy.domain.geoloc.service.fulltextsearch.FulltextQuery,
	 * java.io.OutputStream)
	 */
	public void executeAndSerialize(FulltextQuery query,
			OutputStream outputStream) throws FullTextSearchException {
		statsUsageService.increaseUsage(StatsUsageType.FULLTEXT);
		Assert.notNull(query, "Can not execute a null query");
		Assert.notNull(outputStream,
				"Can not serialize into a null outputStream");
		try {
			logger.info(query.toString());

			ModifiableSolrParams params = query.parameterize();
			CommonsHttpSolrServer server = new CommonsHttpSolrServer(solrClient
					.getURL(), this.httpClient,
					new OutputstreamResponseWrapper(outputStream, params
							.get(Constants.OUTPUT_FORMAT_PARAMETER)));
			server.query(params);
		} catch (SolrServerException e) {
			logger.error("Can not execute query " + query.toQueryString()
					+ "for URL : " + solrClient.getURL() + " : "
					+ e.getCause().getMessage());
			throw new FullTextSearchException(e.getCause().getMessage());
		} catch (MalformedURLException e1) {
			logger.error("The URL " + solrClient.getURL() + " is incorrect");
			throw new FullTextSearchException(e1);
		} catch (RuntimeException e2) {
			logger
					.error("An error has occurred during fulltext search of query "
							+ query + " : " + e2.getCause().getMessage());
			throw new FullTextSearchException(e2.getCause().getMessage());
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.gisgraphy.domain.geoloc.service.fulltextsearch.IFullTextSearchEngine
	 * #executeQueryToString
	 * (com.gisgraphy.domain.geoloc.service.fulltextsearch.FulltextQuery)
	 */
	public String executeQueryToString(FulltextQuery query) throws FullTextSearchException {
		try {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			executeAndSerialize(query, outputStream);
			return outputStream.toString(Constants.CHARSET);
		} catch (UnsupportedEncodingException e) {
			throw new FullTextSearchException("Encoding error during search : "
					+ e.getCause().getMessage());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.gisgraphy.domain.geoloc.service.fulltextsearch.IFullTextSearchEngine
	 * #executeQuery
	 * (com.gisgraphy.domain.geoloc.service.fulltextsearch.FulltextQuery)
	 */
	public List<? extends GisFeature> executeQueryToDatabaseObjects(
			FulltextQuery query) throws ServiceException {
		statsUsageService.increaseUsage(StatsUsageType.FULLTEXT);
		Assert.notNull(query, "Can not execute a null query");
		ModifiableSolrParams params = query.parameterize();
		List<GisFeature> gisFeatureList = new ArrayList<GisFeature>();
		QueryResponse results = null;
		try {
			results = solrClient.getServer().query(params);

			logger.info(query + " took " + (results.getQTime())
					+ " ms and returns " + results.getResults().getNumFound()
					+ " results");

			List<Long> ids = new ArrayList<Long>();
			for (SolrDocument doc : results.getResults()) {
				ids.add((Long) doc.getFieldValue(FullTextFields.FEATUREID
						.getValue()));
			}

			gisFeatureList = gisFeatureDao.listByFeatureIds(ids);
			gisFeatureDao.listByName("asd");
		} catch (Exception e) {
			e.printStackTrace();
			// logger
			// .error("An error has occurred during fulltext search to database object for query "
			// + query + " : " + e.getCause().getMessage());
			// throw new FullTextSearchException(e);
		}

		return gisFeatureList;
	}

	public List<? extends GisFeature> executeQueryToDatabaseObjectsTam(
			FulltextQuery query) throws ServiceException {
		statsUsageService.increaseUsage(StatsUsageType.FULLTEXT);
		Assert.notNull(query, "Can not execute a null query");
		List<GisFeature> gisFeatureList = new ArrayList<GisFeature>();
		try {
			gisFeatureList = gisFeatureDao.listByName(query
					.getQuery());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return gisFeatureList;
	}

	public FulltextResultsDto executeQuery(FulltextQuery query)
			throws ServiceException {
		statsUsageService.increaseUsage(StatsUsageType.FULLTEXT);
		Assert.notNull(query, "Can not execute a null query");
		ModifiableSolrParams params = query.parameterize();
		// System.out.println(params);
		QueryResponse response = null;
		try {
			response = solrClient.getServer().query(params);
		} catch (SolrServerException e) {
			throw new FullTextSearchException(e.getMessage(), e);
		} catch (RuntimeException e) {
			throw new FullTextSearchException(e.getMessage(), e);
		}
		if (response != null) {
			long numberOfResults = response.getResults() != null ? response
					.getResults().getNumFound() : 0;
			logger.info(query + " took " + response.getQTime()
					+ " ms and returns " + numberOfResults + " results");
			return new FulltextResultsDto(response);
		} else {
			return new FulltextResultsDto();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.gisgraphy.domain.geoloc.service.fulltextsearch.IFullTextSearchEngine
	 * #isAlive()
	 */
	public boolean isAlive() {
		return solrClient == null ? false : solrClient.isServerAlive();
	}

	/**
	 * @param solrClient
	 *            the solrClient to set
	 */
	@Required
	public void setSolrClient(IsolrClient solrClient) {
		this.solrClient = solrClient;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.gisgraphy.domain.geoloc.service.fulltextsearch.IFullTextSearchEngine
	 * #getURL()
	 */
	public String getURL() {
		return solrClient.getURL();
	}

}
