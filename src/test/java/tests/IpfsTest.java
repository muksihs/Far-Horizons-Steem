package tests;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.muksihs.farhorizons.ipfs.IpfsFolder;

import fr.rhaz.ipfs.IPFSDaemon;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;

public class IpfsTest {

	private static final String TEST_DIRNAME = "leather-dog-chess/game/152";
	private static final String TEST_FILENAME = "Random Text.txt";
	private IPFSDaemon ipfsd;
	private Process dp;

	@BeforeTest
	public void setup() throws InterruptedException, IOException {
		System.out.println("=== setup");
		ipfsd = new IPFSDaemon();
		ipfsd.download();
		ipfsd.getBin().setExecutable(true);
		// following doesn't work - ipfs says update isn't installed.
		// ipfsd.process("update", "fetch");
		// following doesn't work - ipfs says update isn't installed.
		// ipfsd.process("update", "install", "latest");
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

	@AfterTest
	public void teardown() throws IOException {
		System.out.println("=== teardown");
		dp.destroy();
		for (int wait = 0; wait < 300 && dp.isAlive(); wait++) {
			sleep(100);
		}
		dp.destroyForcibly();
	}

	@Test
	public void catReadme() throws IOException {
		System.out.println("=== catReadme");
		System.out.println("VERSION: " + ipfsd.getVersion());
		System.out.println("STORE: " + ipfsd.getStore().getAbsolutePath());
		Process cp = ipfsd.process("cat", "/ipfs/QmS4ustL54uo8FzR9455qaxZwuMiUhyvMcX9Ba8nUH4uVv/readme");
		System.out.println(IOUtils.toString(cp.getInputStream(), StandardCharsets.UTF_8));
		System.err.println(IOUtils.toString(cp.getErrorStream(), StandardCharsets.UTF_8));
		sleep(500);
	}

	@Test
	public void printStats() throws IOException {
		System.out.println("=== printStats");
		Process stats = ipfsd.process("stats", "repo");
		System.out.println(IOUtils.toString(stats.getInputStream(), StandardCharsets.UTF_8));
		System.err.println(IOUtils.toString(stats.getErrorStream(), StandardCharsets.UTF_8));
		sleep(500);
	}
	
	@Test
	public void ipfsFolderUtilAdd() throws IOException {
		System.out.println("=== ipfsFolderUtilAdd");
		try (IpfsFolder ipfs = new IpfsFolder("test folder")) {
			ipfs.setDebug(true);
			ipfs.add(new File("test-data", "hudson-101x125.jpg"));
			ipfs.add(new File("test-data/subfolder", "dog park dissidents.jpg"));
			ipfs.add(new File("test-data", "LeaDog_Pride_Flag.jpg"));
			ipfs.commit();
		}
	}
	
	@Test
	public void ipfsFolderUtilAdd2() throws IOException {
		System.out.println("=== ipfsFolderUtilAdd2");
		try (IpfsFolder ipfs = new IpfsFolder("game-1536431132")) {
			ipfs.setDebug(true);
			ipfs.add(new File("test-data/final-game-reports", "sp01.rpt.t46.txt"));
			ipfs.add(new File("test-data/final-game-reports", "sp02.rpt.t46.txt"));
			String ipfsHash = ipfs.commit();
			System.out.println("https://cloudflare-ipfs.com/ipfs/"+ipfsHash+"/game-1536431132/sp01.rpt.t46.txt");
			System.out.println("https://cloudflare-ipfs.com/ipfs/"+ipfsHash+"/game-1536431132/sp02.rpt.t46.txt");
			System.out.println("https://ipfs.io/ipfs/"+ipfsHash+"/game-1536431132/sp01.rpt.t46.txt");
			System.out.println("https://ipfs.io/ipfs/"+ipfsHash+"/game-1536431132/sp02.rpt.t46.txt");
		}
	}

	@Test(dependsOnMethods= {"ipfsFolderUtilAdd"})
	public void ipfsAdd() throws IOException {
		System.out.println("=== ipfsAdd");
		// create random text string
		StringBuilder sb = new StringBuilder();
		Random r = new Random();
		for (int ix = 0; ix < 16; ix++) {
			sb.append((char) (r.nextInt(26) + 'A'));
		}
		IPFS ipfs = new IPFS("/ip4/127.0.0.1/tcp/5001");
		NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(TEST_FILENAME,
				sb.toString().getBytes(StandardCharsets.UTF_8));
		NamedStreamable.DirWrapper dir = new NamedStreamable.DirWrapper(TEST_DIRNAME, Arrays.asList(file));
		List<MerkleNode> addResults = ipfs.add(dir, true);
		Collections.sort(addResults, (a,b)->a.name.get().length()-b.name.get().length());
		MerkleNode dirResult = null;
		MerkleNode fileResult = null;
		for (MerkleNode addResult : addResults) {
			System.out.println("Added: "+addResult.name.get());
			if (addResult.name.orElse("*").equals("")) {
				dirResult = addResult;
			}
			if (addResult.name.orElse("*").equals(TEST_DIRNAME+"/"+TEST_FILENAME)) {
				fileResult = addResult;
			}
		}
		
		assert dirResult != null : "Directory did not get put into local IPFS!";
		assert fileResult != null : "File did not get put into local IPFS!";

		for (MerkleNode addResult : addResults) {
			doHeadOnHash(addResult);
		}

		doGetOnFile(dirResult, fileResult);
	}

	private void doGetOnFile(MerkleNode dirResult, MerkleNode fileResult) throws IOException {
		System.out.println("--- http get on file: "+dirResult.name.get()+"/"+fileResult.name.get());
		URL url = new URL(
				"https://cloudflare-ipfs.com/ipfs/" + dirResult.hash.toBase58() + "/" + fileResult.name.get().replace(" ", "%20"));
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.connect();
		System.out.println(url.toExternalForm() + " [" + con.getResponseCode() + "]");
	}

	private void doHeadOnHash(MerkleNode addResult) throws IOException {
		System.out.println("--- http head on hash: "+addResult.name.get());
		URL url = new URL("https://cloudflare-ipfs.com/ipfs/" + addResult.hash.toBase58());
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("HEAD");
		con.connect();
		System.out.println(url.toExternalForm() + " [" + con.getResponseCode() + "]");
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}
}
