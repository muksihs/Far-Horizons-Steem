package com.muksihs.farhorizons;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NasaApod {
	private static final String FALLBACK_IMG = "https://steemitimages.com/DQmXpFhwGPkDvL1R2jcS3ULg6MkFdUHQm6D1xKMJnw7djt2/image.png";
	private static final TimeZone EST5EDT = TimeZone.getTimeZone("EST5EDT");
	private static ObjectMapper _json=null;
	private static ObjectMapper json() {
		if (_json==null) {
			initJackson();
		}
		return _json;
	}
	public static String getApodImageUrl(File gameDir) throws IOException {
		File imgStoreDir = new File(gameDir, "steem-data");
		imgStoreDir.mkdirs();
		File imgStore = new File(imgStoreDir, "nasa-images.json");
		if (!imgStore.exists()) {
			FileUtils.touch(imgStore);
		}
		NasaApodImageMap map;
		try {
			map = json().readValue(imgStore, NasaApodImageMap.class);
		} catch (Exception e) {
			map=null;
		}
		if (map==null) {
			map = new NasaApodImageMap();
		}
		GregorianCalendar gc = new GregorianCalendar(EST5EDT);
		gc.add(GregorianCalendar.DAY_OF_YEAR, -new Random().nextInt(5*365));
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		sdf.setTimeZone(EST5EDT);
		String apiUrl = "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&date=";
		String ymd = sdf.format(gc.getTime());
		apiUrl+=ymd;
		if (map.getMap().containsKey(ymd)) {
			return map.getMap().get(ymd).getUrl();
		}
		NasaApodApiResponse resp;
		try {
			resp = json().readValue(new URL(apiUrl), NasaApodApiResponse.class);
		} catch (Exception e) {
			resp = null;
		}
		if (resp!=null) {
			if (resp.getDate()!=null) {
				if (resp.getUrl()!=null) {
					if (resp.getUrl().startsWith("https:")) {
						if (resp.getUrl().endsWith(".jpg")) {
							NasaApodImage nasaImg = new NasaApodImage();
							nasaImg.setDate(resp.getDate().trim());
							nasaImg.setUrl(resp.getUrl().trim());
							map.getMap().put(nasaImg.getDate().trim(), nasaImg);
							try {
								json().writeValue(imgStore, map);
							} catch (Exception e) {
							}
							return nasaImg.getUrl();
						}
					}
				}
			}
		}
		if (map.getMap().isEmpty()) {
			return FALLBACK_IMG;
		}
		List<NasaApodImage> images = new ArrayList<>(map.getMap().values());
		Collections.shuffle(images);
		return images.get(0).getUrl();
	}
	private static void initJackson() {
		_json = new ObjectMapper();
		_json.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		_json.setDateFormat(dateFormat);
	}
	public static class NasaApodImage {
		private String date;
		private String url;
		public String getUrl() {
			return url;
		}
		public void setUrl(String url) {
			this.url = url;
		}
		public String getDate() {
			return date;
		}
		public void setDate(String date) {
			this.date = date;
		}
	}
	public static class NasaApodImageMap {
		private Map<String, NasaApodImage> map = new HashMap<>();

		public Map<String, NasaApodImage> getMap() {
			return map;
		}

		public void setMap(Map<String, NasaApodImage> map) {
			this.map = map;
		}
	}
	public static class NasaApodApiResponse {
		private String date;
		private String explanation;
		private String hdurl;
		private String media_type;
		@JsonProperty("service_version")
		private String serviceVersion;
		private String title;
		private String url;
		public String getDate() {
			return date;
		}
		public void setDate(String date) {
			this.date = date;
		}
		public String getExplanation() {
			return explanation;
		}
		public void setExplanation(String explanation) {
			this.explanation = explanation;
		}
		public String getHdurl() {
			return hdurl;
		}
		public void setHdurl(String hdurl) {
			this.hdurl = hdurl;
		}
		public String getMedia_type() {
			return media_type;
		}
		public void setMedia_type(String media_type) {
			this.media_type = media_type;
		}
		public String getServiceVersion() {
			return serviceVersion;
		}
		public void setServiceVersion(String serviceVersion) {
			this.serviceVersion = serviceVersion;
		}
		public String getTitle() {
			return title;
		}
		public void setTitle(String title) {
			this.title = title;
		}
		public String getUrl() {
			return url;
		}
		public void setUrl(String url) {
			this.url = url;
		}
	}
	
	public static void main(String[] args) throws IOException {
		System.out.println(getApodImageUrl(new File("/home/muksihs/Far-Horizons/game-1517015828")));
	}
}
