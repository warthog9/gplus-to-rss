/*
 * Copyright 2002-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gplus.to.rss;

import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.CommonGoogleClientRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.Activity;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments.Image;
import com.google.api.services.plus.model.Activity.PlusObject.Attachments.Thumbnails;
import com.google.api.services.plus.model.ActivityFeed;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.io.SyndFeedOutput;

/**
 * Servlet which exposes Google+ public posts of a user as a RSS feed
 * 
 * @author Fabien Baligand
 */
public class GplusToRssServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final String GOOGLE_API_KEY_INIT_PARAM_NAME = "googleApiKey";
	private static final String GOOGLE_PLUS_PUBLIC_COLLECTION = "public";

	private static final Logger LOGGER = LoggerFactory.getLogger(GplusToRssServlet.class);

	/** Google+ API Client */
	private List<Plus> plusClients;

	/**
	 * Get All Public G+ posts of requested user 
	 * and publish them as a RSS feed
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		
		// Check the URI
		if (request.getPathInfo() == null || request.getPathInfo().trim().length() <= 1 || !request.getPathInfo().startsWith("/")) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No google+ user id. Url must be /rss/[googlePlusUserId]");
			return;
		}

		// Get the googlePlusUserId
		String googlePlusUserId = request.getPathInfo().substring("/".length()).trim();
		
		// Process the case when googlePlusUserId entered is a complete Google+ profile URL
		if (googlePlusUserId.contains("/")) {
			googlePlusUserId = googlePlusUserId.replaceAll("/about|/posts|/photos|/videos|/plusones", "");
			googlePlusUserId = googlePlusUserId.substring(googlePlusUserId.lastIndexOf('/') + 1);
			response.sendRedirect("/rss/" + googlePlusUserId);
			return;
		}
		// Process the case when googlePlusUserId entered is a Google User Name
		else if (!googlePlusUserId.matches("[0-9]+")) {
			try {
				URL url = new URL("https://profiles.google.com/" + googlePlusUserId);
				HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
				urlConnection.setAllowUserInteraction(false);
				urlConnection.setInstanceFollowRedirects(false);
				urlConnection.connect();
				int responseCode = urlConnection.getResponseCode();
				if (responseCode == HttpServletResponse.SC_MOVED_PERMANENTLY) {
					String locationHeader = urlConnection.getHeaderField("Location");
					googlePlusUserId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
					response.sendRedirect("/rss/" + googlePlusUserId);
					return;
				}
				else {
					LOGGER.error("Impossible to get userId from userName " + googlePlusUserId);
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "You must enter a valid google+ user id");
					return;
				}
			}
			catch (IOException e) {
				LOGGER.error("Impossible to get userId from userName " + googlePlusUserId, e);
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "You must enter a valid google+ user id");
				return;
			}
		}
		
		// Get the public G+ posts 
		ActivityFeed activityFeed = null;
		Iterator<Plus> plusClientIterator = plusClients.iterator();
		String errorMessage = null;
		int errorCode = 0;
		
		while (activityFeed == null && plusClientIterator.hasNext()) {
			try {
				Plus plusClient = plusClientIterator.next();
				activityFeed = plusClient.activities().list(googlePlusUserId, GOOGLE_PLUS_PUBLIC_COLLECTION).execute();
			} catch (GoogleJsonResponseException e) {
				if (e.getDetails() != null && (e.getDetails().getCode() == 400 || e.getDetails().getCode() == 404)) {
					LOGGER.warn("UserId is not valid or not found : " + googlePlusUserId, e);
					errorCode = HttpServletResponse.SC_BAD_REQUEST;
				}
				else {
					LOGGER.error("Error when trying to get public G+ posts of user " + googlePlusUserId, e);
					errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
				}
				if (e.getDetails() != null) {
					if (e.getDetails().getErrors() != null && !e.getDetails().getErrors().isEmpty()) {
						errorMessage = e.getDetails().getErrors().get(0).getMessage();
						if (e.getDetails().getErrors().get(0).getReason() != null) {
							errorMessage += " (" + e.getDetails().getErrors().get(0).getReason() + ")";
						}
					}
					else {
						errorMessage = e.getDetails().getMessage();
					}
				}
				else {
					errorMessage = e.getMessage();
				}
			} catch (IOException e) {
				LOGGER.error("Error when trying to get public G+ posts of user " + googlePlusUserId, e);
				errorMessage = e.getMessage();
				errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			}
		}

		// If it doesn't work with each google api key, we definitely abandon and raise an error to the client 
		if (activityFeed == null) {
			response.sendError(errorCode, "A technical error occured when trying to get public G+ posts of user " + googlePlusUserId + " : " + errorMessage);
			return;
		}

		// Begin the RSS feed
		SyndFeed feed = new SyndFeedImpl();
		feed.setFeedType("rss_2.0");
		feed.setLink("https://plus.google.com/" + googlePlusUserId + "/posts");
		if (activityFeed.getUpdated() != null) {
			feed.setPublishedDate(new Date(activityFeed.getUpdated().getValue()));
		}
		
		List<SyndEntry> entries = new ArrayList<SyndEntry>();

		// Processing G+ posts
		for (Activity post : activityFeed.getItems()) {
			SyndEntry entry = new SyndEntryImpl();
			boolean hasContent = post.getObject().getContent() != null && !post.getObject().getContent().trim().isEmpty();
			boolean hasAttachments = post.getObject().getAttachments() != null && !post.getObject().getAttachments().isEmpty();

			// Define feed title (if it is usefull)
			if (feed.getTitle() == null && post.getActor() != null && post.getActor().getDisplayName() != null && googlePlusUserId.equals(post.getActor().getId())) {
				feed.setTitle(filterContent(post.getActor().getDisplayName()) + " - Google+ Posts");
			}
			
			// Define feed published date
			if (feed.getPublishedDate() == null) {
				if (post.getUpdated() != null) {
					feed.setPublishedDate(new Date(post.getUpdated().getValue()));
				}
				if (post.getPublished() != null) {
					feed.setPublishedDate(new Date(post.getPublished().getValue()));
				}
			}
			
			// Define entry title
			if (hasContent) {
				entry.setTitle(filterContent(post.getTitle()));
			}
			else if (hasAttachments && post.getObject().getAttachments().get(0).getDisplayName() != null) {
				entry.setTitle(filterContent(post.getObject().getAttachments().get(0).getDisplayName()));
			}
			else {
				entry.setTitle("No Title");
			}

			// Define link and date
			entry.setLink(post.getUrl());
			entry.setPublishedDate(new Date(post.getPublished().getValue()));

			// Define entry content
			SyndContent description = new SyndContentImpl();
			description.setType("text/html");
			StringBuilder contentBuilder = new StringBuilder();
			if (hasContent) {
				contentBuilder.append(filterContent(post.getObject().getContent()))
				              .append("<br/>");
				if (hasAttachments) {
					contentBuilder.append("<br/>");
				}
			}
			
			if (hasAttachments) {
				
				for (int i=0 ; i < post.getObject().getAttachments().size() ; ++i) {
					Attachments attachment = post.getObject().getAttachments().get(i);
					
					// Article Attachment
					if (attachment.getObjectType().equals("article")) {

						// Thumbnail ?
						Image thumbnail = attachment.getImage();
						
						// Thumbnail Insertion
						if (thumbnail != null) {
							contentBuilder.append("<table cellspacing='10px'><tr valign='top'><td><img style='max-width: 150px; max-height: 150px;' ");
							if (thumbnail.getWidth() != null && thumbnail.getWidth() > 150 && (thumbnail.getHeight() == null || thumbnail.getWidth() > thumbnail.getHeight())) {
								contentBuilder.append("width='150px' ");
							}
							else if (thumbnail.getHeight() != null && thumbnail.getHeight() > 150 && (thumbnail.getWidth() == null || thumbnail.getHeight() > thumbnail.getWidth())) {
								contentBuilder.append("height='150px' ");
							}
							contentBuilder.append("src='" + thumbnail.getUrl() + "' />")
							              .append("</td><td>");
						}
						
						// Content
						String title = attachment.getDisplayName() != null ? filterContent(attachment.getDisplayName()) : "Article";
						contentBuilder.append("<a href='" + attachment.getUrl() + "'>")
						              .append(title)
						              .append("</a>")
						              .append("<br/>");
						if (attachment.getContent() != null && !attachment.getContent().trim().isEmpty()) {
							contentBuilder.append(filterContent(attachment.getContent()))
				              			  .append("<br/>");
						}

						if (thumbnail != null) {
							contentBuilder.append("</td></tr></table>");
							++i;
						}
					}
					// Photo Attachment
					else if (attachment.getObjectType().equals("photo")) {
						if (attachment.getFullImage() != null) {
							contentBuilder.append("<img src='" + attachment.getFullImage().getUrl() + "'/>");
						}
						else {
							contentBuilder.append("<img src='" + attachment.getImage().getUrl() + "'/>");
						}
						contentBuilder.append("<br/>");
					}
					// Video Attachment
					else if (attachment.getObjectType().equals("video")) {
						contentBuilder.append("<a href='" + attachment.getUrl() + "'>")
						              .append("<img border='0' src='" + attachment.getImage().getUrl() + "'/>")
						              .append("</a>")
						              .append("<br/>");
					}
					// Album Attachment
					else if (attachment.getObjectType().equals("album")) {
						String albumTitle = (attachment.getDisplayName() != null && !attachment.getDisplayName().isEmpty()) ? attachment.getDisplayName() : "Album";
						contentBuilder.append("<a href='" + attachment.getUrl() + "'>")
			              			  .append(albumTitle)
			              			  .append("</a>")
			              			  .append("<br/>");
						if (attachment.getThumbnails() != null) {
							boolean isFirstPhoto = true;
							for (Thumbnails thumbnail : attachment.getThumbnails()) {
								contentBuilder.append("<a href='" + thumbnail.getUrl() + "'>")
					              			  .append("<img border='0' src='" + thumbnail.getImage().getUrl() + "'/>")
					              			  .append("</a>")
					              			  .append("   ");
								if (isFirstPhoto) {
									contentBuilder.append("<br/>");
									isFirstPhoto = false;
								}
							}
							contentBuilder.append("<br/>");
						}
					}
				}
			}
			description.setValue(contentBuilder.toString());
			entry.setDescription(description);

			entries.add(entry);
		}
		
		// Define default feed title, description and published date
		if (feed.getTitle() == null) {
			feed.setTitle(googlePlusUserId + " - Google+ Posts");
		}
		feed.setDescription(feed.getTitle());
		if (feed.getPublishedDate() == null) {
			feed.setPublishedDate(new Date());
		}

		// Generate and finally publish the RSS feed
		feed.setEntries(entries);
        SyndFeedOutput output = new SyndFeedOutput();
		try {
			response.setContentType("application/rss+xml");
			response.setCharacterEncoding("UTF-8");
			Writer responseWriter = response.getWriter();
			output.output(feed, responseWriter);
			responseWriter.close();
		} catch (Exception e) {
			LOGGER.error("Error when posting RSS feed of user " + googlePlusUserId, e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Google+ content cannot be published as RSS feed.");
			return;
		}
	}

	/**
	 * Initialize the service
	 */
	@Override
	public void init() throws ServletException {
		
		String googleApiKeyParam = this.getServletConfig().getInitParameter(GOOGLE_API_KEY_INIT_PARAM_NAME);
		String[] googleApiKeys = googleApiKeyParam.split(",");
		
		plusClients = new ArrayList<Plus>();
		for (String googleApiKey : googleApiKeys) {
			// Initialisation of Google+ Client
			Plus plusClient = new Plus.Builder (new NetHttpTransport(), new JacksonFactory(), null)
								  .setApplicationName("gplus-to-rss")
								  .setGoogleClientRequestInitializer(new CommonGoogleClientRequestInitializer(googleApiKey))
								  .build();
			plusClients.add(plusClient);
		}

	}
	
	/**
	 * Filter every character which is not a valid XML character
	 * @param content content to be filtered
	 * @return filtered content
	 */
	private String filterContent(String content) {
		
		if (content == null) {
			return null;
		}
		
		StringBuilder filteredContent = new StringBuilder();

		for (int i=0 ; i<content.length() ; ++i) {
			char c = content.charAt(i);
			if (c >= 32 || c == 9 || c == 10 || c == 13) {
				filteredContent.append(c);
			}
		}
		
		return filteredContent.toString();
	}
}
