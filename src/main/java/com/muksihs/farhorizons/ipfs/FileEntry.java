package com.muksihs.farhorizons.ipfs;

import java.io.File;

public class FileEntry {
	private final String ipfsFilename;
	private final File localFile;
	public String getIpfsFilename() {
		return ipfsFilename;
	}
	public File getLocalFile() {
		return localFile;
	}
	public FileEntry(String ipfsFilename, File localFile) {
		if (ipfsFilename==null) {
			throw new NullPointerException("Must specify IPFS filename");
		}
		if (localFile==null) {
			throw new NullPointerException("Must specify local file contents");
		}
		while (ipfsFilename.startsWith("/")) {
			ipfsFilename.substring(1);
		}
		while (ipfsFilename.endsWith("/")) {
			ipfsFilename.substring(0,ipfsFilename.length()-1);
		}
		if (ipfsFilename.trim().isEmpty()) {
			throw new IllegalArgumentException("Remote filename must not be composed only of slashes or blanks.");
		}
		this.ipfsFilename=ipfsFilename;
		this.localFile=localFile;
	}
}