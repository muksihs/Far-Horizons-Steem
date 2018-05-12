package steem.models;

import java.math.BigDecimal;

public class VideoInfo {
	private String permLink;
	private String author;
	private String permlink;
	private String snaphash;
	private String title;
	private BigDecimal duration;
	private long filesize;
	private String spritehash;
	public String getPermLink() {
		return permLink;
	}

	public void setPermLink(String permLink) {
		this.permLink = permLink;
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = author;
	}

	public String getPermlink() {
		return permlink;
	}

	public void setPermlink(String permlink) {
		this.permlink = permlink;
	}

	public String getSnaphash() {
		return snaphash;
	}

	public void setSnaphash(String snaphash) {
		this.snaphash = snaphash;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public BigDecimal getDuration() {
		return duration;
	}

	public void setDuration(BigDecimal duration) {
		this.duration = duration;
	}

	public long getFilesize() {
		return filesize;
	}

	public void setFilesize(long filesize) {
		this.filesize = filesize;
	}

	public String getSpritehash() {
		return spritehash;
	}

	public void setSpritehash(String spritehash) {
		this.spritehash = spritehash;
	}
}
