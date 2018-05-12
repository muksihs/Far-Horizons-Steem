package steem.models;

public class VideoData {
	private VideoContent content;
	private VideoInfo info;
	private String _id;

	public VideoContent getContent() {
		return content;
	}

	public void setContent(VideoContent content) {
		this.content = content;
	}

	public VideoInfo getInfo() {
		return info;
	}

	public void setInfo(VideoInfo info) {
		this.info = info;
	}

	public String get_id() {
		return _id;
	}

	public void set_id(String _id) {
		this._id = _id;
	}
}
