# What is gplus-to-rss ? #

  * gplus-to-rss aims to expose Google+ public posts of any user as a RSS feed.
  * This project is the exact code of http://gplus-to-rss.appspot.com service.
  * This project is a simple java web application. You can deploy it on any java web server (ex: Tomcat, Google App Engine, ...).


# How to install it ? #
  * firstable, you have to create a Google API Key :
    * go to http://code.google.com/apis/console (you need to be authentified with your google account)
    * create a new project
    * access 'Services' tab
    * activate 'Google+ API' service
    * access 'API Access' tab
    * copy your new Google API Key in 'API key' field
  * download the latest gplus-to-rss version in [Downloads](https://code.google.com/p/gplus-to-rss/downloads/list) section
  * unzip it to a new directory (let's call it $gplus-to-rss-webapp)
  * edit $gplus-to-rss-webapp/WEB-INF/web.xml and replace 'YOUR GOOGLE API KEY' with the one you have just created and copied
  * deploy it to your favorite java web server
  * start your java server, access root page, and follow instructions
  * enjoy ;)


# Change Log #

### version 1.1.1 ###

  * process reshare annotation text
  * try to recover google+ ID when an email is entered in input
  * add "unwrapAttachments" option
  * home page update (labels, new paragraph "About this service", javascript moved to page bottom)
  * add a favicon.ico file
  * bug fix : a post with objectType null produces NullPointerException (Now, it is processed as an article)
  * bug fix : video with no attached image produces NullPointerException (Now, display name is used for the video link)
  * enhance multi "google+ api key" support
  * error messages update

### version 1.1.0 ###

  * improvement of article attachment display : a thumbnail image is displayed on the left of the article summary, when a thumnail is available (like in G+)
  * album display : a new feature of G+ API, which allow to display G+ albums as an album of thumbnails with the link to the real album
  * RSS feed title and description is now the full name of the G+ user/page
  * google+ api java client version upgrade from 1.6 to 1.12
  * jackson json lib update from 1.9 to 2.0
  * addition of robots.txt file, which avoids google bot to reference /rss/
  * bug fixes

### version 1.0.1 ###

  * bug fix : G+ API field 'updated' may be null, and the code was not protected against that.