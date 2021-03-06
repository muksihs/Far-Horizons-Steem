package steem.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import models.FarHorizonsGameData;

@JsonIgnoreProperties(ignoreUnknown=true)
public class CommentMetadata {
	private FarHorizonsGameData farHorizonsGameData;
	private String app;
	private String canonical;
	private String format;
	private String[] image;
	private String[] links;
	private String status;
	private String[] tags;
	private String[] users;
	private VideoData video;
	private String community;
	public String getApp() {
		return app;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public String getCanonical() {
		return canonical;
	}

	public void setCanonical(String canonical) {
		this.canonical = canonical;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String[] getImage() {
		return image;
	}

	public void setImage(String[] image) {
		this.image = image;
	}

	public String[] getLinks() {
		return links;
	}

	public void setLinks(String[] links) {
		this.links = links;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String[] getTags() {
		return tags;
	}

	public void setTags(String[] tags) {
		this.tags = tags;
	}

	public String[] getUsers() {
		return users;
	}

	public void setUsers(String[] users) {
		this.users = users;
	}

	public VideoData getVideo() {
		return video;
	}

	public void setVideo(VideoData video) {
		this.video = video;
	}

	public String getCommunity() {
		return community;
	}

	public void setCommunity(String community) {
		this.community = community;
	}

	public FarHorizonsGameData getFarHorizonsGameData() {
		return farHorizonsGameData;
	}

	public void setFarHorizonsGameData(FarHorizonsGameData farHorizonsGameData) {
		this.farHorizonsGameData = farHorizonsGameData;
	}
}
