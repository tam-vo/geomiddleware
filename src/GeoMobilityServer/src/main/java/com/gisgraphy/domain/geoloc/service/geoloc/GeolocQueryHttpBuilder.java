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
package com.gisgraphy.domain.geoloc.service.geoloc;

import javax.servlet.http.HttpServletRequest;

import com.gisgraphy.domain.geoloc.entity.GisFeature;
import com.gisgraphy.domain.valueobject.GisgraphyConfig;
import com.gisgraphy.domain.valueobject.GisgraphyServiceType;
import com.gisgraphy.domain.valueobject.Output;
import com.gisgraphy.domain.valueobject.OutputFormatHelper;
import com.gisgraphy.domain.valueobject.Pagination;
import com.gisgraphy.helper.GeolocHelper;
import com.gisgraphy.serializer.OutputFormat;
import com.gisgraphy.servlet.GeolocServlet;
import com.gisgraphy.servlet.GisgraphyServlet;
import com.vividsolutions.jts.geom.Point;

/**
 * A GeolocQuery Query builder. it build geolocQuery from HTTP Request
 * 
 * @see Pagination
 * @see Output
 * @author <a href="mailto:david.masclet@gisgraphy.com">David Masclet</a>
 */
public class GeolocQueryHttpBuilder {

	private static GeolocQueryHttpBuilder instance = new GeolocQueryHttpBuilder();

	public static GeolocQueryHttpBuilder getInstance() {
		return instance;
	}

	/**
	 * @param req an HttpServletRequest to construct a {@link GeolocQuery}
	 * @throws GeolocSearchException 
	 * @throws StreetSearchException 
	 */
	public GeolocQuery buildFromHttpRequest(HttpServletRequest req) throws GeolocSearchException, StreetSearchException {

		// point
		Float latitude;
		Float longitude;
		// lat
		try {
			latitude = GeolocHelper.parseInternationalDouble(req.getParameter(GeolocServlet.LAT_PARAMETER));
		} catch (Exception e) {
			throw new GeolocSearchException("latitude is not correct or empty");
		}
		if (latitude == null) {
			throw new GeolocSearchException("latitude is not correct or empty");
		}
		// long
		try {
			longitude = GeolocHelper.parseInternationalDouble(req.getParameter(GeolocServlet.LONG_PARAMETER));
		} catch (Exception e) {
			throw new GeolocSearchException("longitude is not correct or empty");
		}
		if (longitude == null) {
			throw new GeolocSearchException("longitude is not correct or empty");
		}

		// point

		Point point;
		try {
			point = GeolocHelper.createPoint(longitude, latitude);
		} catch (RuntimeException e1) {
			throw new GeolocSearchException(e1.getMessage());
		}
		if (point == null) {
			throw new GeolocSearchException("can not determine Point");
		}
		// radius
		double radius;
		try {
			radius = GeolocHelper.parseInternationalDouble(req.getParameter(GeolocServlet.RADIUS_PARAMETER));
		} catch (Exception e) {
			radius = GeolocQuery.DEFAULT_RADIUS;
		}

		GeolocQuery geolocQuery = constructMinimalQuery(point, radius);

		// pagination
		Pagination pagination = null;
		int from;
		int to;
		try {
			from = Integer.valueOf(req.getParameter(GisgraphyServlet.FROM_PARAMETER)).intValue();
		} catch (Exception e) {
			from = Pagination.DEFAULT_FROM;
		}

		try {
			to = Integer.valueOf(req.getParameter(GisgraphyServlet.TO_PARAMETER)).intValue();
		} catch (NumberFormatException e) {
			to = -1;
		}

		pagination = Pagination.paginateWithMaxResults(geolocQuery.getMaxLimitResult()).from(from).to(to)
				.limitNumberOfResults(geolocQuery.getMaxLimitResult());
		// output format
		OutputFormat format = OutputFormat.getFromString(req.getParameter(GisgraphyServlet.FORMAT_PARAMETER));
		format = OutputFormatHelper.getDefaultForServiceIfNotSupported(format, GisgraphyServiceType.GEOLOC);
		Output output = Output.withFormat(format);

		// indent
		if ("true".equalsIgnoreCase(req.getParameter(GisgraphyServlet.INDENT_PARAMETER))
				|| "on".equalsIgnoreCase(req.getParameter(GisgraphyServlet.INDENT_PARAMETER))) {
			output.withIndentation();
		}

		// placetype
		Class<? extends GisFeature> clazz = GeolocHelper.getClassEntityFromString(req
						.getParameter(GeolocServlet.PLACETYPE_PARAMETER));

		// distance field
		if ("false".equalsIgnoreCase(req.getParameter(GeolocServlet.DISTANCE_PARAMETER))
				|| "off".equalsIgnoreCase(req.getParameter(GeolocServlet.DISTANCE_PARAMETER))) {
			geolocQuery.withDistanceField(false);
		}

		if (req.getParameter(GeolocServlet.CALLBACK_PARAMETER) != null) {
			geolocQuery.withCallback(req.getParameter(GeolocServlet.CALLBACK_PARAMETER));
		}

		geolocQuery.withPagination(pagination);
		if (clazz == null) {
			geolocQuery.withPlaceType(GisgraphyConfig.defaultGeolocSearchPlaceTypeClass);
		} else {
			geolocQuery.withPlaceType(clazz);
			geolocQuery.withOutput(output);
		}
		return geolocQuery;
	}

	/**
	 * Create a basic GeolocQuery. this method must be overide if we need to
	 * create inheritance object
	 * 
	 * @param point
	 *            the JTS point to create the query
	 * @param radius
	 *            the radius to search around
	 * @return
	 */
	protected GeolocQuery constructMinimalQuery(Point point, double radius) {
		GeolocQuery geolocQuery = new GeolocQuery(point, radius);
		return geolocQuery;
	}
}
