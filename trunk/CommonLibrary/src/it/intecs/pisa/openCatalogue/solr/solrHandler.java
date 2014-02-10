/* Copyright (c) 2013 Intecs - www.intecs.it. All rights reserved.
 * This code is licensed under the GPL 3.0 license, available at the root
 * application directory.
 */
package it.intecs.pisa.openCatalogue.solr;

import it.intecs.pisa.log.Log;
import it.intecs.pisa.openCatalogue.saxon.SaxonDocument;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import javax.xml.xpath.XPathConstants;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.w3c.dom.Document;

/**
 *
 * @author simone
 */
public class solrHandler {

    String solrHost;

    public solrHandler(String solrEndPoint) {
        this.solrHost = solrEndPoint;
    }

    public SaxonDocument search(HashMap<String, String> request) throws UnsupportedEncodingException, IOException, SaxonApiException, Exception {
        HttpClient client = new HttpClient();
        HttpMethod method;
        String urlStr = prepareUrl(request);
        Log.debug("The following search is goint to be executed:" + urlStr);
        // Create a method instance.
        method = new GetMethod(urlStr);

        // Provide custom retry handler is necessary
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));

        // Execute the method.
        int statusCode = client.executeMethod(method);
        SaxonDocument solrResponse = new SaxonDocument(method.getResponseBodyAsString());
        //Log.debug(solrResponse.getXMLDocumentString());

        if (statusCode != HttpStatus.SC_OK) {
            Log.error("Method failed: " + method.getStatusLine());
            String errorMessage = (String) solrResponse.evaluatePath("//lst[@name='error']/str[@name='msg']/text()", XPathConstants.STRING);
            throw new Exception(errorMessage);
        }

        return solrResponse;
    }

    public int postDocument(String body) throws IOException, SaxonApiException, Exception {
        HttpClient client = new HttpClient();
        HttpMethod method;
        String urlStr = solrHost + "/update?commit=true";
        Log.debug("Ingesting a new document to: " + urlStr);
        Log.debug(body);
        method = new PostMethod(urlStr);
        RequestEntity entity = new StringRequestEntity(body);
        ((PostMethod) method).setRequestEntity(entity);
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
        method.setRequestHeader("Content-Type", "text/xml");
        method.setRequestHeader("charset", "utf-8");

        // Execute the method.
        int statusCode = client.executeMethod(method);
        SaxonDocument solrResponse = new SaxonDocument(method.getResponseBodyAsStream());

        if (statusCode != HttpStatus.SC_OK) {
            Log.error("Method failed: " + method.getStatusLine());
            Log.error(solrResponse.getXMLDocumentString());
        }
        return statusCode;
    }

    /*
     * Esempio query data:
     *
     * 1) beginPosition:[2008-03-04T07:47:30.000Z TO 2009-09-04T07:47:30.000Z]
     * http://localhost/solr/collection1/select?q=*%3A*&fq=beginPosition%3A%5B2008-03-04T07%3A47%3A30.000Z+TO+2009-09-04T07%3A47%3A30.000Z%5D&wt=xml&indent=true
     * 2) beginPosition:[2007-04-04T07:47:30.000Z TO 2008-04-05T07:47:30.000Z]
     * http://localhost/solr/collection1/select?q=*%3A*&fq=beginPosition%3A%5B2007-04-04T07%3A47%3A30.000Z+TO+2008-04-05T07%3A47%3A30.000Z%5D&wt=xml&indent=true
     *
     * 3) endPosition:[2006-04-04T07:47:30.000Z TO 2008-04-05T07:47:30.000Z]
     * http://localhost/solr/collection1/select?q=*%3A*&fq=endPosition%3A%5B2006-04-04T07%3A47%3A30.000Z+TO+2008-04-05T07%3A47%3A30.000Z%5D&wt=xml&indent=true
     *
     * esempi query intersect
     *
     * 1) posList:"intersects(43 69 64 89)
     * http://localhost/solr/collection1/select?q=*%3A*&fq=posList%3A%22intersects(43+69+64+89)%22&wt=xml&indent=true
     *
     * 2) posList :[-90,-180 TO 15,45]
     * http://localhost/solr/collection1/select?q=*%3A*&fq=posList+%3A%5B-90%2C-180+TO+15%2C45%5D&wt=xml&indent=true
     *
     * 3) posList:"Intersects(POLYGON((78 7, 74 17, 52 98, 78 7)))"
     * http://localhost/solr/collection1/select?q=*%3A*&fq=posList%3A%22Intersects(POLYGON((78+7%2C+74+17%2C+52+98%2C+78+7)))%22&wt=xml&indent=true
     *
     *
     * 
     */
    private String prepareUrl2(HashMap<String, String> request) throws UnsupportedEncodingException, Exception {
        String[] params = request.keySet().toArray(new String[0]);
        String fq = "";
        String q = this.solrHost + "/select?q=*%3A*&wt=xml&indent=true";

        if (request.containsKey("q") && (request.get("q").equals("*.*") == false)) {
            String newQ = request.get("q");
            if (null == newQ || newQ.isEmpty())
                newQ = "*.*";
            q = this.solrHost + "/select?q=" + URLDecoder.decode(newQ, "ISO-8859-1") + "&wt=xml&indent=true";
        }

        String lat = null;
        String lon = null;
        String radius = null;

        for (String name : params) {
            String value = request.get(name);

            if (value != null && value.equals("") == false) {
                if (name.equals("count")) {
                    q += "&rows=" + value;
                } else if (name.equals("startPage")) {
                } else if (name.equals("startIndex")) {
                    q += "&start=" + (Integer.parseInt(value) - 1);
                } else if (name.equals("uid")) {
                } else if (name.equals("bbox")) {
                    String[] values = value.split(",");
                    if (values.length != 4) {
                        throw new Exception();
                    }
                    value = "[" + values[1] + "," + values[0] + " " + values[3] + "," + values[2] + "]";
                    Log.debug("BBOX " + value);
                    fq += " AND posList:" + URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("geom")) {
                    fq += " AND posList :\"Intersects(" + (URLDecoder.decode(value, "ISO-8859-1")) + ")\"";
                } else if (name.equals("id")) {
                    fq += " AND id:\"" + URLDecoder.decode(value, "ISO-8859-1") + "\"";
                } else if (name.equals("lat")) {
                    lat = URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("lon")) {
                    lon = URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("radius")) {
                    radius = URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("startdate")) {
                    value = value.endsWith("Z") ? value : value + "Z";
                    fq += " AND beginPosition:[" + URLDecoder.decode(value, "ISO-8859-1") + " TO *]";
                } else if (name.equals("stopdate")) {
                    value = value.endsWith("Z") ? value : value + "Z";
                    fq += " AND endPosition:[* TO " + URLDecoder.decode(value, "ISO-8859-1") + "]";
                } else if (name.equals("tp")) {
                }
                //Table 3 - OpenSearch Parameters for Collection Search
                else if (name.equals("pt")) {
                    fq += parse("productType", value);
                } else if (name.equals("psn")) {
                    fq += parse("platformShortName", value);
                } else if (name.equals("psi")) {
                    fq += parse("platformSerialIdentifier", value);
                } else if (name.equals("inst")) {
                    fq += parse("instrument", value);
                } else if (name.equals("st")) {
                    fq += parse("sensorType", value);
                } else if (name.equals("ct")) {
                    fq += parse("compositeType", value);
                } else if (name.equals("pl")) {
                    fq += parse("processingLevel", value);
                } else if (name.equals("ot")) {
                    fq += parse("orbitType", value);
                } else if (name.equals("res")) {
                    fq += parse("resolution", value);
                } else if (name.equals("sr")) {
                    fq += parse("spectralRange", value);
                } else if (name.equals("wl")) {
                    fq += parse("wavelengths", value);
                } else if (name.equals("ul")) {
                    fq += parse("useLimitation", value);
                } else if (name.equals("hsc")) {
                    fq += parse("hasSecurityConstraints", value);
                } else if (name.equals("orgname")) {
                    fq += parse("organisationName", value);
                } else if (name.equals("diss")) {
                    fq += parse("dissemination", value);
                }                
                //Table 4 - OpenSearch Parameters for Product Search
                else if (name.equals("pid")) {
                    fq += parse("parentIdentifier", value);
                } else if (name.equals("ps")) {//productionStatus
                    fq += parse("productionStatus", value);
                } else if (name.equals("at")) {
                    fq += parse("acquisitionType", value);
                } else if (name.equals("on")) {
                    fq += parse("orbitNumber", value);
                } else if (name.equals("od")) {
                    fq += parse("orbitDirection", value);
                } else if (name.equals("tr")) {
                    fq += parse("track", value);
                } else if (name.equals("fr")) {
                    fq += parse("frame", value);
                } else if (name.equals("si")) {
                    fq += parse("swathIdentifier", value);
                } else if (name.equals("cc")) {
                    fq += parse("cloudCover", value);
                } else if (name.equals("sc")) {
                    fq += parse("snowCover", value);
                } else if (name.equals("pqd")) {
                    fq += parse("productQualityDegradation", value);
                } else if (name.equals("pqdt")) {
                    fq += parse("productQualityDegradationTag", value);
                } else if (name.equals("pn")) {
                    fq += parse("processorName", value);
                } else if (name.equals("pcen")) {
                    fq += parse("processingCenter", value);
                } else if (name.equals("pd")) {
                    fq += parse("processingDate", value);
                } else if (name.equals("sm")) {
                    fq += parse("sensorMode", value);
                } else if (name.equals("ac")) {
                    fq += parse("archivingCenter", value);
                } else if (name.equals("procm")) {
                    fq += parse("processingMode", value);
                }
                //Table 5 - OpenSearch Parameters for Acquistion Parameters Search                
                // todo availabilityTime
                else if (name.equals("as")) {
                    fq += parse("acquisitionStation", value);
                } else if (name.equals("ast")) {
                    fq += parse("acquisitionSubType", value);
                } else if (name.equals("stfan")) {
                    fq += parse("startTimeFromAscendingNode", value);
                } else if (name.equals("ctfan")) {
                    fq += parse("completionTimeFromAscendingNode", value);
                } else if (name.equals("iaa")) {
                    fq += parse("illuminationAzimuthAngle", value);
                } else if (name.equals("iza")) {
                    fq += parse("illuminationZenithAngle", value);
                } else if (name.equals("iea")) {
                    fq += parse("illuminationElevationAngle", value);
                } else if (name.equals("pm")) {
                    fq += parse("polarisationMode", value);
                } else if (name.equals("pc")) {
                    fq += parse("polarisationChannels", value);
                } else if (name.equals("ald")) {
                    fq += parse("antennaLookDirection", value);
                } else if (name.equals("minia")) {
                    fq += parse("minimumIncidenceAngle", value);
                } else if (name.equals("maxia")) {
                    fq += parse("maximumIncidenceAngle", value);
                } else if (name.equals("df")) {
                    fq += parse("dopplerFrequency", value);
                } else if (name.equals("iav")) {
                    fq += parse("incidenceAngleVariation", value);
                } else {
                }
            }
        }

        if ((lat != null) && (lon != null) && (radius != null)) {
            fq += " AND posList :\"Intersects(Circle(" + lon + "," + lat + " d=" + radius + "))\"";
        }

        String url = q;
        if (fq.length() > 1) {
            url += "&fq=" + URLEncoder.encode(fq.substring(5), "ISO-8859-1");
        }
        return url;
    }
    
        private String prepareUrl(HashMap<String, String> request) throws UnsupportedEncodingException, Exception {
        String[] params = request.keySet().toArray(new String[0]);
        String fq = "";
        String q = this.solrHost + "/select?q=*%3A*&wt=xml&indent=true";

        if (request.containsKey("q") && (request.get("q").equals("*.*") == false)) {
            String newQ = request.get("q");
            if (null == newQ || newQ.isEmpty())
                newQ = "*.*";
            q = this.solrHost + "/select?q=" + URLDecoder.decode(newQ, "ISO-8859-1") + "&wt=xml&indent=true";
        }

        String lat = null;
        String lon = null;
        String radius = null;

        for (String name : params) {
            String value = request.get(name);

            if (value != null && value.equals("") == false) {
                if (name.equals("count")) {
                    q += "&rows=" + value;
                } else if (name.equals("startPage")) {
                } else if (name.equals("startIndex")) {
                    q += "&start=" + (Integer.parseInt(value) - 1);
                } else if (name.equals("uid")) {
                } else if (name.equals("bbox")) {
                    String[] values = value.split(",");
                    if (values.length != 4) {
                        throw new Exception();
                    }
                    value = "[" + values[1] + "," + values[0] + " " + values[3] + "," + values[2] + "]";
                    Log.debug("BBOX " + value);
                    fq += " AND posList:" + URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("geom")) {
                    fq += " AND posList :\"Intersects(" + (URLDecoder.decode(value, "ISO-8859-1")) + ")\"";
                } else if (name.equals("id")) {
                    fq += " AND id:\"" + URLDecoder.decode(value, "ISO-8859-1") + "\"";
                } else if (name.equals("lat")) {
                    lat = URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("lon")) {
                    lon = URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("radius")) {
                    radius = URLDecoder.decode(value, "ISO-8859-1");
                } else if (name.equals("startdate")) {
                    value = value.endsWith("Z") ? value : value + "Z";
                    fq += " AND beginPosition:[" + URLDecoder.decode(value, "ISO-8859-1") + " TO *]";
                } else if (name.equals("stopdate")) {
                    value = value.endsWith("Z") ? value : value + "Z";
                    fq += " AND endPosition:[* TO " + URLDecoder.decode(value, "ISO-8859-1") + "]";
                } else if (name.equals("q") || name.equals("recordSchema")) {
                }
                //Table 3 - OpenSearch Parameters for Collection Search
                else {
                    fq += parse(name, value);
                }
            }
        }

        if ((lat != null) && (lon != null) && (radius != null)) {
            fq += " AND posList :\"Intersects(Circle(" + lon + "," + lat + " d=" + radius + "))\"";
        }

        String url = q;
        if (fq.length() > 1) {
            url += "&fq=" + URLEncoder.encode(fq.substring(5), "ISO-8859-1");
        }
        return url;
    }
    
     public SaxonDocument getStats() throws UnsupportedEncodingException, IOException, SaxonApiException, Exception {
        HttpClient client = new HttpClient();
        HttpMethod method;
        String urlStr = this.solrHost + "/select?q=*%3A*&fq=parentIdentifier%3DSENTINEL2_L1C_N2A&wt=xml&indent=true&stats=true&stats.field=beginPosition&stats.field=endPosition&stats.field=orbitNumber&stats.field=acquisitionStation&rows=0&indent=true";

        Log.debug("The following search is goint to be executed:" + urlStr);
        // Create a method instance.
        method = new GetMethod(urlStr);

        // Provide custom retry handler is necessary
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));

        // Execute the method.
        int statusCode = client.executeMethod(method);
        SaxonDocument solrResponse = new SaxonDocument(method.getResponseBodyAsString());
        Log.debug(solrResponse.getXMLDocumentString());

        if (statusCode != HttpStatus.SC_OK) {
            Log.error("Method failed: " + method.getStatusLine());
            String errorMessage = (String) solrResponse.evaluatePath("//lst[@name='error']/str[@name='msg']/text()", XPathConstants.STRING);
            Log.error(solrResponse.getXMLDocumentString());
            throw new Exception(errorMessage);
        }
        
        return solrResponse;
    }

    public SaxonDocument getStatsForCollection(String collectionId) throws UnsupportedEncodingException, IOException, SaxonApiException, Exception {
        HttpClient client = new HttpClient();
        HttpMethod method;
        String fq = !collectionId.isEmpty()?"fq=parentIdentifier%3D"+collectionId+"&":"";
            String urlStr = this.solrHost + "/select?q=*%3A*&"+fq+"wt=xml&stats=true&"
                + "stats.field=beginPosition&"
                + "stats.field=endPosition&"
                + "stats.field=orbitNumber&"
                + "stats.field=acquisitionStation&"
                + "facet.field=productType&"
                + "facet.field=platformShortName&"
                + "facet.field=platformSerialIdentifier&"
                + "facet.field=instrument&"
                + "facet.field=sensorType&"
                + "facet.field=compositeType&"
                + "facet.field=processingLevel&"
                + "facet.field=orbitType&"
                + "stats.field=resolution&"
                + "facet.field=spectralRange&"
                + "stats.field=wavelengths&"
                + "facet.field=useLimitation&"
                + "facet.field=hasSecurityConstraints&"
                + "facet.field=organisationName&"
                + "facet.field=dissemination&"
                + "facet.field=parentIdentifier&"
                + "facet.field=productionStatus&"
                + "facet.field=acquisitionType&"
                + "stats.field=orbitNumber&"
                + "facet.field=orbitDirection&"
                + "stats.field=track&"
                + "stats.field=frame&"
                + "facet.field=swathIdentifier&"
                + "stats.field=cloudCover&"
                + "stats.field=snowCover&"
                + "facet.field=productQualityDegradation&"
                + "facet.field=productQualityDegradationTag&"
                + "facet.field=processorName&"
                + "facet.field=processingCenter&"
                + "stats.field=processingDate&"
                + "facet.field=sensorMode&"
                + "facet.field=archivingCenter&"
                + "facet.field=processingMode&"
                + "facet.field=acquisitionStation&"
                + "facet.field=acquisitionSubType&"
                + "stats.field=startTimeFromAscendingNode&"
                + "stats.field=completionTimeFromAscendingNode&"
                + "stats.field=illuminationAzimuthAngle&"
                + "stats.field=illuminationZenithAngle&"
                + "stats.field=illuminationElevationAngle&"
                + "facet.field=polarisationMode&"
                + "facet.field=polarisationChannels&"
                + "facet.field=antennaLookDirection&"
                + "stats.field=minimumIncidenceAngle&"
                + "stats.field=maximumIncidenceAngle&"
                + "stats.field=dopplerFrequency&"
                + "stats.field=incidenceAngleVariation&"
                + "rows=0&indent=true&facet=on&facet.mincount=1";

        Log.debug("The following search is goint to be executed:" + urlStr);
        // Create a method instance.
        method = new GetMethod(urlStr);

        // Provide custom retry handler is necessary
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));

        // Execute the method.
        int statusCode = client.executeMethod(method);
        SaxonDocument solrResponse = new SaxonDocument(method.getResponseBodyAsString());
        Log.debug(solrResponse.getXMLDocumentString());

        if (statusCode != HttpStatus.SC_OK) {
            Log.error("Method failed: " + method.getStatusLine());
            String errorMessage = (String) solrResponse.evaluatePath("//lst[@name='error']/str[@name='msg']/text()", XPathConstants.STRING);
            Log.error(solrResponse.getXMLDocumentString());
            throw new Exception(errorMessage);
        }
        
        return solrResponse;
    }
    
    public String parse(String tag, String value) {
        /*
         * n1 equal to field = n1, {n1,n2} equals to field=n1 OR field = n2
         * [n1,n2] equal to n1 <= field <= n2, [n1,n2[ equals to n1 <= field <
         * n2 ]n1,n2[ equals to n1 < field < n2 ]n1,n2] equal to n1 < field <=
         * n2. [n1 equals to n1<= field ]n1 equals to n1 < field n2] equals to
         * field <= n2 n2[ equals to field < n2.
         */
        String queryElement = "";
        // in the current implementation we consider only the >= and <=
        if (value.startsWith("[") && value.endsWith("]")) {
            //[n1,n2] equal to n1 <= field <= n2
            queryElement = value.replace(","," TO ");
        } else if (value.startsWith("[")) {
            //[n1 equals to n1<= field
            queryElement = value +" TO *]";
        } else if (value.endsWith("]")) {
            //n2] equals to field <= n2
            queryElement = "[* TO "+ value;
        }else if (value.startsWith("{")) {
            queryElement = value.replace(","," OR ").replace("{", "(").replace("}",")");
        } else {
            queryElement = value;
        }


        return " AND " + tag + ":" + queryElement;


    }
}
