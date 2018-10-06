package com.muksihs.farhorizons.ipfs;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import fr.rhaz.ipfs.IPFSDaemon;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.api.NamedStreamable.FileWrapper;

public class IpfsFolder implements Closeable {
	protected static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	private boolean debug=false;
	private IPFSDaemon ipfsd;
	private Process dp;
	private final Set<File> entries;

	public IpfsFolder() {
		this("/");
	}

	public void add(File localFile) {
		entries.add(localFile);
	}

	public void remove(File entry) {
		entries.remove(entry);
	}

	public void clear() {
		entries.clear();
	}

	public void commit() throws IOException {
		List<NamedStreamable> children=new ArrayList<>();
		for (File entry: entries) {
			FileWrapper child = new NamedStreamable.FileWrapper(entry);
			children.add(child);
		}
		NamedStreamable.DirWrapper folder = new NamedStreamable.DirWrapper(parentFolder, children);
		IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
		List<MerkleNode> addResults = ipfs.add(folder, true);
		Collections.sort(addResults, (a,b)->a.name.get().length()-b.name.get().length());
		MerkleNode dirResult=null;
		for (MerkleNode addResult : addResults) {
			if (isDebug()) {
				System.out.println("Added: "+addResult.name.get());
			}
			if (addResult.name.orElse("*").equals("")) {
				dirResult = addResult;
			}
		}
		if (dirResult==null) {
			return;
		}
		for (MerkleNode addResult : addResults) {
			head(addResult);
			if (isDebug()) {
				System.out.println("https://cloudflare-ipfs.com/ipfs/"+dirResult.hash.toBase58()+addResult.name.get().replace(" ", "%20"));
				System.out.println("https://ipfs.io/ipfs/"+dirResult.hash.toBase58()+addResult.name.get().replace(" ", "%20"));
			}
		}
	}
	
	private final String parentFolder;
	public IpfsFolder(String parentFolder) {
		if (parentFolder == null) {
			throw new NullPointerException("Parent folder must not be null.");
		}
		if (!parentFolder.startsWith("/")) {
			parentFolder = "/" + parentFolder;
		}
		if (!parentFolder.endsWith("/")) {
			parentFolder += "/";
		}
		this.parentFolder=parentFolder;
		entries = new TreeSet<>();
		ipfsd = new IPFSDaemon();
		ipfsd.download();
		ipfsd.getBin().setExecutable(true);
		
		Process isDaemonAlready = ipfsd.process("id");
		while (isDaemonAlready.isAlive()) {
			sleep(250);
		}
		if (isDaemonAlready.exitValue() == 0) {
			dp = null;
			return;
		}
		
		dp = ipfsd.process("daemon", "--init", "--unrestricted-api");
		Runtime.getRuntime().addShutdownHook(new Thread(() -> dp.destroy()));
		do {
			if (!dp.isAlive()) {
				throw new IllegalStateException("My IPFS Daemon Not Running! Maybe there is another running?");
			}
			Process idp = ipfsd.process("id");
			while (idp.isAlive()) {
				sleep(250);
			}
			if (idp.exitValue() == 0) {
				break;
			}
			sleep(250);
		} while (true);
	}

	private int head(MerkleNode addResult) throws IOException {
		Random r = new Random();
		int result = 0;
		if (r.nextBoolean()) {
			result = headCloudflare(addResult);
			result = headIpfsIo(addResult);
		} else {
			result = headIpfsIo(addResult);
			result = headCloudflare(addResult);
		}
		if (result == 524) { // timeout on response from server
			if (isDebug()) {
				System.out.println(" - Timeout: Retrying");
			}
			return head(addResult);
		}
		return result;
	}

	private int headCloudflare(MerkleNode addResult) throws IOException {
		URL url = new URL("https://cloudflare-ipfs.com/ipfs/" + addResult.hash.toBase58());
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("HEAD");
		con.connect();
		con.disconnect();
		if (isDebug()) {
			System.out.println("=> "+url.toExternalForm());
		}
		return con.getResponseCode();
	}

	private int headIpfsIo(MerkleNode addResult) throws IOException {
		URL url = new URL("https://ipfs.io/ipfs/" + addResult.hash.toBase58());
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("HEAD");
		con.connect();
		con.disconnect();
		if (isDebug()) {
			System.out.println("=> "+url.toExternalForm());
		}
		return con.getResponseCode();
	}

	@Override
	public void close() throws IOException {
		if (dp==null) {
			return;
		}
		dp.destroy();
		for (int wait = 0; wait < 300 && dp.isAlive(); wait++) {
			sleep(100);
		}
		dp.destroyForcibly();
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}
}
