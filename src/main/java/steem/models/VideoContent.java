package steem.models;

public class VideoContent {
	private String description;
	private String[] tags;
	private String videohash;
	private String video480hash;
	private String magnet;
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String[] getTags() {
		return tags;
	}

	public void setTags(String[] tags) {
		this.tags = tags;
	}

	public String getVideohash() {
		return videohash;
	}

	public void setVideohash(String videohash) {
		this.videohash = videohash;
	}

	public String getVideo480hash() {
		return video480hash;
	}

	public void setVideo480hash(String video480hash) {
		this.video480hash = video480hash;
	}

	public String getMagnet() {
		return magnet;
	}

	public void setMagnet(String magnet) {
		this.magnet = magnet;
	}
}
