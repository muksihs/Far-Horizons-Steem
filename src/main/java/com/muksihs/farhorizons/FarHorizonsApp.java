package com.muksihs.farhorizons;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muksihs.farhorizons.ipfs.IpfsFolder;
import com.muksihs.farhorizons.steemapi.RcAccount;
import com.muksihs.farhorizons.steemapi.RcAccounts;
import com.muksihs.farhorizons.steemapi.SteemRcApi;

import blazing.chain.LZSEncoding;
import eu.bittrade.libs.steemj.SteemJ;
import eu.bittrade.libs.steemj.apis.database.models.state.Comment;
import eu.bittrade.libs.steemj.apis.database.models.state.Discussion;
import eu.bittrade.libs.steemj.apis.follow.model.BlogEntry;
import eu.bittrade.libs.steemj.apis.follow.model.CommentBlogEntry;
import eu.bittrade.libs.steemj.base.models.AccountName;
import eu.bittrade.libs.steemj.base.models.AppliedOperation;
import eu.bittrade.libs.steemj.base.models.Asset;
import eu.bittrade.libs.steemj.base.models.ExtendedAccount;
import eu.bittrade.libs.steemj.base.models.Permlink;
import eu.bittrade.libs.steemj.base.models.SignedTransaction;
import eu.bittrade.libs.steemj.base.models.TimePointSec;
import eu.bittrade.libs.steemj.base.models.VoteState;
import eu.bittrade.libs.steemj.base.models.operations.CommentOperation;
import eu.bittrade.libs.steemj.base.models.operations.Operation;
import eu.bittrade.libs.steemj.base.models.operations.TransferOperation;
import eu.bittrade.libs.steemj.base.models.operations.virtual.AuthorRewardOperation;
import eu.bittrade.libs.steemj.configuration.SteemJConfig;
import eu.bittrade.libs.steemj.enums.AssetSymbolType;
import eu.bittrade.libs.steemj.enums.PrivateKeyType;
import eu.bittrade.libs.steemj.exceptions.SteemCommunicationException;
import eu.bittrade.libs.steemj.exceptions.SteemInvalidTransactionException;
import eu.bittrade.libs.steemj.exceptions.SteemResponseException;
import eu.bittrade.libs.steemj.util.SteemJUtils;
import models.FarHorizonsGameData;
import models.NewGameInfo;
import steem.models.CommentMetadata;

public class FarHorizonsApp implements Runnable {

	private static final BigDecimal _MIN_RCS_TO_RUN = new BigDecimal("18873834001");

	private static final String KEY_GAME_DATA = "farHorizonsGameData";

	private Map<String, Object> defaultMetadata;
	private static final String MIME_HTML = "text/html";

	private static final String SECTION_MARKER_START = "* * *";

	@SuppressWarnings("unused")
	private static final int MAX_COMMENT_SIZE = 16 * 1024;

	private static final String SEM_GAMECOMPLETE = "_game-complete";
	private static final String SEM_FORCEGAMECOMPLETE = "_force-game-complete";
	private static final String GAME_STATS_DIR = "reports/stats";

	private static final String[] MAP_LIST = new String[] { "galaxy_map-1.png", "galaxy_map_3d-1.png",
			"galaxy_map-2.png" };

	private static final String DIV_PULL_RIGHT_START = "<div class='pull-right' style='float:right;padding:1rem;max-width:50%;'>";

	@SuppressWarnings("unused")
	private static final String DIV_PULL_LEFT_START = "<div class='pull-left' style='float:left;padding:1rem;max-width:50%;'>";

	private static final TimeZone EST5EDT = TimeZone.getTimeZone("EST5EDT");

	private static final String _PERMLINK = "_permlink_";

	private static final File binDir = new File("/home/muksihs/git/Far-Horizons/bin");
	private static final File dataDir = new File("/home/muksihs/Far-Horizons");

	public static final String STEEM_CONFIG = ".steem/far-horizons.properties";

	private static final int MAX_PLAYERS = 20;
	private String postingKey;
	private String activeKey;
	private String accountName;

	private final String[] args;
	private FarHorizonsSteemJ steemJ;
	private AccountName botAccount;
	private ObjectMapper json;

	public FarHorizonsApp(String[] args) {
		this.args = args;
		defaultMetadata = new LinkedHashMap<>();
		defaultMetadata.put("app", "FarHorizons/20180831-00");
	}

	public static boolean haveEnoughRcs(AccountName botAccount) {
		RcAccounts rcs;
		try {
			rcs = SteemRcApi.getRc(botAccount);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return true;
		}
		List<RcAccount> rcAccounts = rcs.getRcAccounts();
		if (rcAccounts.isEmpty()) {
			return true;
		}
		BigDecimal minRcsToRun = minRcsToRun(botAccount);
		for (RcAccount rc : rcAccounts) {
			if (!rc.getAccount().equals(botAccount.getName())) {
				continue;
			}
			BigDecimal estimatedMana = rc.getEstimatedMana();
			if (minRcsToRun.compareTo(estimatedMana) < 0) {
				return true;
			}
			System.out.println("--- Available RCs " + NumberFormat.getInstance().format(estimatedMana) + " < "
					+ NumberFormat.getInstance().format(minRcsToRun));
			return false;
		}
		return false;
	}

	private void loadSteemAccountInformation() throws FileNotFoundException, IOException {
		File configFile = new File(System.getProperty("user.home"), STEEM_CONFIG);
		Properties steemConfig = new Properties();
		steemConfig.load(new FileInputStream(configFile));
		this.activeKey = steemConfig.getProperty("activeKey");
		if (this.activeKey == null || this.activeKey.trim().isEmpty()) {
			this.activeKey = "";
		}
		this.postingKey = steemConfig.getProperty("postingKey");
		if (this.postingKey == null || this.postingKey.trim().isEmpty()) {
			this.postingKey = "";
		}
		this.accountName = steemConfig.getProperty("accountName");
		if (this.accountName == null || this.accountName.trim().isEmpty()) {
			throw new IllegalArgumentException("accountName= for steem account name not found");
		}
	}

	@Override
	public void run() {
		try {
			execute();
			System.out.flush();
			System.err.flush();
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.flush();
			System.err.flush();
			System.exit(-1);
		}
	}

	private void execute() throws FileNotFoundException, IOException, SteemCommunicationException,
			SteemResponseException, InterruptedException, SteemInvalidTransactionException {
		init();
		processOptions();
	}

	private void init() throws FileNotFoundException, IOException, SteemCommunicationException, SteemResponseException {
		loadSteemAccountInformation();
		initilizeSteemJ();
		initJackson();
	}

	private void initJackson() {
		json = new ObjectMapper();
		json.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		json.setDateFormat(dateFormat);
	}

	private void initilizeSteemJ() throws SteemCommunicationException, SteemResponseException {
		SteemJConfig myConfig = SteemJConfig.getInstance();
		myConfig.setEncodingCharset(StandardCharsets.UTF_8);
		myConfig.setIdleTimeout(250);
		myConfig.setResponseTimeout(1000);
		// Maximize participation rewards
		myConfig.setSteemJWeight((short) 0);
		botAccount = new AccountName(this.accountName);
		myConfig.setDefaultAccount(botAccount);
		List<ImmutablePair<PrivateKeyType, String>> privateKeys = new ArrayList<>();
		if (!this.activeKey.trim().isEmpty()) {
			privateKeys.add(new ImmutablePair<>(PrivateKeyType.ACTIVE, this.activeKey));
		}
		if (!this.postingKey.trim().isEmpty()) {
			privateKeys.add(new ImmutablePair<>(PrivateKeyType.POSTING, this.postingKey));
		}
		myConfig.getPrivateKeyStorage().addAccount(myConfig.getDefaultAccount(), privateKeys);
		steemJ = new FarHorizonsSteemJ();
	}

	private void processOptions() throws SteemCommunicationException, SteemResponseException, JsonParseException,
			JsonMappingException, IOException, InterruptedException, SteemInvalidTransactionException {
		Iterator<String> iArgs = Arrays.asList(args).iterator();
		while (iArgs.hasNext()) {
			String arg = iArgs.next();
			if (arg.equals("--get-rcs")) {
				RcAccounts rcs = SteemRcApi.getRc(botAccount);
				for (RcAccount rc : rcs.getRcAccounts()) {
					System.out.println(rc.getAccount() + ": " + rc.getRcManabar().getCurrentMana().toPlainString()
							+ " > " + rc.getEstimatedMana().toPlainString() + " > " + rc.getMaxRc().toPlainString());
				}
				rcs = SteemRcApi.getRc(new AccountName("muksihs"));
				for (RcAccount rc : rcs.getRcAccounts()) {
					System.out.println(rc.getAccount() + ": " + rc.getRcManabar().getCurrentMana().toPlainString()
							+ " > " + rc.getEstimatedMana().toPlainString() + " > " + rc.getMaxRc().toPlainString());
				}
				rcs = SteemRcApi.getRc(new AccountName("magali"));
				for (RcAccount rc : rcs.getRcAccounts()) {
					System.out.println(rc.getAccount() + ": " + rc.getRcManabar().getCurrentMana().toPlainString()
							+ " > " + rc.getEstimatedMana().toPlainString() + " > " + rc.getMaxRc().toPlainString());
				}
				continue;
			}
			if (arg.equals("--test-deadline")) {
				System.out.println(
						"GAME JOIN DEADLINE: " + joinGameDeadline(new GregorianCalendar()).getTime().toString());
				System.out.println("SUBMIT ORDERS DEADLINE: "
						+ submitOrdersDeadline(new GregorianCalendar()).getTime().toString());
				continue;
			}
			if (arg.equals("--start-game")) {
				if (!haveEnoughRcs(botAccount)) {
					continue;
				}
				doProcessAnnounceReplies();
				continue;
			}
			if (arg.equals("--upvote-check")) {
				if (!haveEnoughRcs(botAccount)) {
					continue;
				}
				doUpvoteCheck();
				continue;
			}
			if (arg.equals("--announce-game")) {
				if (!haveEnoughRcs(botAccount)) {
					continue;
				}
				doAnnounceGamePost();
				continue;
			}
			if (arg.equals("--generate-html")) {
				doCreateHtmlResultFiles();
				continue;
			}
			if (arg.equals("--create-maps")) {
				doCreateMaps();
				continue;
			}
			if (arg.equals("--run-game")) {
				if (!haveEnoughRcs(botAccount)) {
					continue;
				}
				System.out.println("-> doRunGameTurn");
				doRunGameTurn();
				if (haveEnoughRcs(botAccount)) {
					System.out.println("-> doUpvoteCheck");
					doUpvoteCheck();
				}
				continue;
			}

			if (arg.equals("--cleanup")) {
				doGameCleanup();
				continue;
			}

			if (arg.equals("--payouts")) {
				if (!haveEnoughRcs(botAccount)) {
					continue;
				}
				doGamePayouts();
				continue;
			}
			
			System.err.println("UNKNOWN OPTION: "+arg);
		}
	}

	/**
	 * Upvote any posts that we haven't up voted yet that others have voted on, this
	 * rewards people who upvote our posts to encourage them to keep up voting our
	 * posts. Note: We do not up vote posts that have no up votes. We want to reward
	 * users, not waste voting power on looking like only doing self serving voting.
	 * Only vote if we have enough effective voting power currently available to
	 * have a worthwhile impact.
	 * 
	 * @throws SteemCommunicationException
	 * @throws SteemResponseException
	 * @throws IOException
	 * @throws JsonMappingException
	 * @throws JsonParseException
	 */
	private void doUpvoteCheck() throws SteemCommunicationException, SteemResponseException, JsonParseException,
			JsonMappingException, IOException {

		BigDecimal voteThreshold = new BigDecimal("90.00");
		List<BlogEntry> entriesForUpvote = new ArrayList<>();
		for (int retries = 0; retries < 10; retries++) {
			try {
				entriesForUpvote = steemJ.getBlogEntries(botAccount, 0, (short) 100);
			} catch (SteemResponseException e1) {
				e1.printStackTrace();
				break;
			}
		}
		//sort oldest to newest by entry id
		Collections.sort(entriesForUpvote, (a,b)->a.getEntryId()-b.getEntryId());
		
		forBlogEntries: for (BlogEntry entry : entriesForUpvote) {
			// if not by game master, SKIP
			if (!entry.getAuthor().equals(botAccount)) {
				continue;
			}
			ExtendedAccount extendedBotAccount = getExtendedAccount(botAccount);
			if (extendedBotAccount == null) {
				continue forBlogEntries;
			}
			// stop up voting if our voting power drops too low
			BigDecimal votingPower = getEstimateVote(extendedBotAccount);
			// stop up voting if our voting power drops too low
			if (votingPower.compareTo(voteThreshold) < 0) {
				System.out.println("Not up voting. Power " + votingPower + "% < " + voteThreshold + "%");
				break forBlogEntries;
			}
			Discussion article = null;
			for (int retries = 0; retries < 10; retries++) {
				try {
					article = steemJ.getContent(botAccount, entry.getPermlink());
					break;
				} catch (SteemResponseException e1) {
					if (retries == 9) {
						throw e1;
					}
					sleep(500);
				}
			}
			CommentMetadata metadata = json.readValue(article.getJsonMetadata(), CommentMetadata.class);
			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue forBlogEntries;
			}
			boolean isGame = false;
			for (String tag : tags) {
				if (tag.startsWith("game-")) {
					isGame = true;
					break;
				}
			}
			if (!isGame) {
				continue forBlogEntries;
			}
			// must have at least two other votes
			if (article.getNetVotes() <= 1) {
				continue forBlogEntries;
			}
			// must have at least 0.017 payout value
			if (article.getPendingPayoutValue().toReal() < 0.017d) {
				continue forBlogEntries;
			}
			List<VoteState> votes = article.getActiveVotes();
			for (VoteState vote : votes) {
				if (vote.getVoter().equals(botAccount)) {
					continue forBlogEntries;
				}
			}
			GregorianCalendar sevenDaysAgo = new GregorianCalendar(EST5EDT);
			sevenDaysAgo.add(Calendar.DAY_OF_YEAR, -7);
			sevenDaysAgo.add(Calendar.MINUTE, 5);//add 5 minutes as a safety measure
			//if too old, skip it, as the vote will be wasted
			if (article.getCreated().getDateTimeAsDate().before(sevenDaysAgo.getTime())) {
				continue;
			}
			// up vote this post. it has other votes already and we haven't up voted it yet.
			try {
				System.out.println("Upvoting: " + votingPower + "%");
				System.out.println(article.getTitle());
				waitCheckBeforeReplying(steemJ);
				steemJ.vote(botAccount, article.getPermlink(), (short) 100);
			} catch (Exception e) {
				// ignore any up vote errors
				System.err.println("Error on up vote. IGNORED.");
				System.err.println(e.getClass().getName() + ":\n" + e.getMessage());
			}
		}
	}

	private ExtendedAccount getExtendedAccount(AccountName accountName)
			throws SteemCommunicationException, SteemResponseException {
		List<ExtendedAccount> accounts = steemJ.getAccounts(Arrays.asList(accountName));
		for (ExtendedAccount account : accounts) {
			if (account.getName().equals(botAccount)) {
				return account;
			}
		}
		return null;
	}

	/**
	 * Get Estimated voting power as a percentage x 100.
	 * 
	 * @param lastVoteTimeSecs
	 * @param lastVotingPower
	 * @return
	 */
	public static BigDecimal getEstimateVote(ExtendedAccount account) {
		return getEstimateVote(account.getLastVoteTime().getDateTimeAsInt(), account.getVotingPower());
	}

	/**
	 * Get Estimated voting power as a percentage x 100.
	 * 
	 * @param lastVoteTimeSecs
	 * @param lastVotingPower
	 * @return
	 */
	public static BigDecimal getEstimateVote(int lastVoteTimeSecs, int lastVotingPower) {
		BigDecimal maxVotingPower = BigDecimal.valueOf(10000);
		BigDecimal now = new BigDecimal(System.currentTimeMillis() / 1000l);
		BigDecimal lastVoteTime = new BigDecimal(lastVoteTimeSecs);
		BigDecimal elapsedTime = now.subtract(lastVoteTime);
		BigDecimal percent = elapsedTime.divide(FIVE_DAYS_SECONDS, 3, RoundingMode.DOWN);
		BigDecimal votingPowerGained = maxVotingPower.multiply(percent).setScale(0, RoundingMode.DOWN);
		BigDecimal estimatedVotingPower = new BigDecimal(lastVotingPower).add(votingPowerGained);
		return estimatedVotingPower.min(maxVotingPower).movePointLeft(2);
	}

	public static final BigDecimal FIVE_DAYS_SECONDS = new BigDecimal((long) 5 * 24 * 60 * 60);

	private void doGamePayouts()
			throws SteemCommunicationException, SteemResponseException, SteemInvalidTransactionException,
			JsonParseException, JsonMappingException, IOException, InterruptedException {
		steemJ.claimRewards();
		Map<Integer, AppliedOperation> history = steemJ.getAccountHistory(botAccount, -1, 1000);
		System.out.println("ACCOUNT HISTORY SIZE: "+history.size());
		Set<String> alreadyPaid = new HashSet<>();

		List<Integer> keys = new ArrayList<>(history.keySet());
		Collections.sort(keys); //oldest to newest

		for (Integer key : keys) {
			AppliedOperation op = history.get(key);
			if (!(op.getOp() instanceof TransferOperation)) {
				continue;
			}
			TransferOperation tro = (TransferOperation) op.getOp();
			if (!tro.getFrom().getName().equals(accountName)) {
				continue;
			}
			String player = tro.getTo().getName();
			alreadyPaid.add(player + "|" + tro.getMemo().toLowerCase());
			if (tro.getMemo().contains(":")) {
				alreadyPaid.add(player + "|" + StringUtils.substringAfter(tro.getMemo(), ":").toLowerCase().trim());
			}
		}

		history: for (Integer key : keys) {
			AppliedOperation op = history.get(key);
			if (!(op.getOp() instanceof AuthorRewardOperation)) {
				continue history;
			}
			AuthorRewardOperation aro = (AuthorRewardOperation) op.getOp();
			Discussion gameTurnPost = steemJ.getContent(botAccount, aro.getPermlink());
			String turn = "";
			if (gameTurnPost.getTitle().matches(".*Turn \\d+.*")) {
				turn = gameTurnPost.getTitle().replaceAll(".*Turn (\\d+).*", "$1");
			}
			// if not by game master, SKIP
			if (!gameTurnPost.getAuthor().equals(botAccount)) {
				continue;
			}

			CommentMetadata metadata = json.readValue(gameTurnPost.getJsonMetadata(), CommentMetadata.class);
			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue history;
			}
			String gameId = "";
			tags: for (String tag : tags) {
				if (!tag.toLowerCase().startsWith("game-")) {
					continue tags;
				}
				gameId = tag;
				break tags;
			}
			if (isBlank(gameId)) {
				continue history;
			}
			File gameDir = new File(dataDir, gameId);

			if (!gameDir.exists()) {
				System.err.println("BAD GAME ID: " + aro.getPermlink());
				continue history;
			}

			File semaphore = new File(gameDir, "steem-data/payouts/_steem-payout-" + gameTurnPost.getId() + ".paid");
			semaphore.getParentFile().mkdirs();
			if (semaphore.exists()) {
				System.out.println("ALREADY PAID: " + aro.getPermlink().getLink() + " [" + gameTurnPost.getId() + "]");
				continue history;
			}
			if (!turn.isEmpty()) {
				System.out.println("PAYOUTS FOR TURN: " + turn);
			}
			File steemDir = new File(gameDir, "steem-data");
			steemDir.mkdirs();

			BigDecimal sbdPayout = BigDecimal.valueOf(aro.getSbdPayout().getAmount(),
					aro.getSbdPayout().getPrecision());
			BigDecimal stmPayout = BigDecimal.valueOf(aro.getSteemPayout().getAmount(),
					aro.getSteemPayout().getPrecision());

			boolean isSbdPayout = (sbdPayout.compareTo(stmPayout) > 0);

			BigDecimal payoutPool;
			if (isSbdPayout) {
				payoutPool = sbdPayout;
			} else {
				payoutPool = stmPayout;
			}

			System.out.println("LINK: " + aro.getPermlink().getLink());
			String currency = isSbdPayout ? " SBD" : " STEEM";
			System.out.println("PAYOUT: " + payoutPool.toPlainString() + currency);
			List<String> playerList = FileUtils.readLines(new File(gameDir, "_players.tab"),
					StandardCharsets.UTF_8.name());
			Set<String> registeredPlayers = new HashSet<>();
			Map<String, String> registeredSpecies = new HashMap<>();
			for (String player : playerList) {
				if (StringUtils.countMatches(player, "\t") < 2) {
					continue;
				}
				String activePlayer = player.split("\t")[1].trim();
				String activeSpecies = player.split("\t")[2].trim();
				registeredPlayers.add(activePlayer);
				registeredSpecies.put(activePlayer, activeSpecies);
			}
			System.out.println("Registered players: " + registeredPlayers.toString());

			GregorianCalendar payoutDeadline = postPayoutDeadline(gameTurnPost.getCreated().getDateTimeAsDate());
			// only pay players who actually played
			List<Discussion> replies = steemJ.getContentReplies(botAccount, gameTurnPost.getPermlink());
			Set<String> activePlayers = new TreeSet<>();
			playersScan: for (Discussion playerReply : replies) {
				String name = playerReply.getAuthor().getName();
				String body = playerReply.getBody();
				body = StringUtils.substringBetween(body, "<html>", "</html>");
				if (body == null || body.trim().isEmpty()) {
					continue playersScan;
				}
				body = decompress(body);
				String bodyUc = body.toUpperCase();
				if (body == null || (!bodyUc.contains("START COMBAT")
						&& !bodyUc.replaceAll("\\s", "").contains("SPECIESNAME:"))) {
					System.out.println(" - Ignoring non-orders comment by " + playerReply.getAuthor().getName());
					continue playersScan;
				}
				Date playedWhen = playerReply.getCreated().getDateTimeAsDate();
				if (playedWhen.after(payoutDeadline.getTime())) {
					System.err.println("Ignoring late turn: " + name);
					continue;
				}
				activePlayers.add(name);
			}
			activePlayers.retainAll(registeredPlayers);
			System.out.println("Active players: " + activePlayers.toString());
			registeredPlayers = null; // make sure we don't use wrong set with a NPE!

			Set<String> votingPlayers = new HashSet<>();
			List<VoteState> votes = gameTurnPost.getActiveVotes();
			Map<String, BigDecimal> votingShares = new HashMap<>();
			for (VoteState vote : votes) {
				Date voteWhen = vote.getTime().getDateTimeAsDate();
				String name = vote.getVoter().getName();
				if (voteWhen.after(payoutDeadline.getTime())) {
					System.err.println("Ignoring late vote: " + name);
					continue;
				}
				BigInteger rshares = vote.getRshares();
				if (rshares.compareTo(BigInteger.ZERO) <= 0) {
					// skip down votes and zero votes - not eligible for payouts
					continue;
				}
				votingPlayers.add(name);
				votingShares.put(name, new BigDecimal(rshares));
			}

			activePlayers.retainAll(votingPlayers); // only keep players who are voters
			votingShares.keySet().retainAll(activePlayers);
			votingPlayers = null; // make sure we don't use wrong set with a NPE!

			System.out.println("Have " + activePlayers.size() + " voting players in reward pool.");
			// only use rshares from active voting players for calculations
			BigDecimal rsharesDivisor = BigDecimal.ZERO;
			for (String player : activePlayers) {
				rsharesDivisor = rsharesDivisor.add(votingShares.get(player));
			}
			// prevent negative payout math
			boolean noRewards = false;
			if (rsharesDivisor.compareTo(BigDecimal.ZERO) <= 0) {
				rsharesDivisor = BigDecimal.ZERO;
				noRewards = true;
			}
			// convert player rshares into payout percents
			for (String player : activePlayers) {
				BigDecimal weightPercent;
				if (noRewards) {
					weightPercent = BigDecimal.ZERO;
				} else {
					weightPercent = votingShares.get(player).divide(rsharesDivisor, 8, RoundingMode.DOWN);
				}
				votingShares.put(player, weightPercent);
			}
			Map<String, BigDecimal> payouts = new HashMap<>();
			BigDecimal pool = BigDecimal.ZERO;
			for (String player : activePlayers) {
				BigDecimal payout = votingShares.get(player).multiply(payoutPool).setScale(3, RoundingMode.DOWN);
				payouts.put(player, payout);
				pool = pool.add(payout);
			}
			System.out.println(" - Pool size: " + pool.toPlainString() + currency);
			System.out.println(" - Per player rewards: ");
			for (String player : activePlayers) {
				BigDecimal payout = payouts.get(player);
				System.out.println("  " + player + " = " + payout.toPlainString() + currency);
			}
			if (pool.compareTo(BigDecimal.ZERO) <= 0) {
				FileUtils.touch(semaphore);
				continue;
			}
			System.out.println(" - Distributing rewards.");
			for (String player : activePlayers) {
				BigDecimal payout = payouts.get(player);
				AccountName to = new AccountName(player);
				AssetSymbolType symbol;
				if (isSbdPayout) {
					symbol = AssetSymbolType.SBD;
				} else {
					symbol = AssetSymbolType.STEEM;
				}
				Asset amount = new Asset(payout.movePointRight(payout.scale()).longValue(), symbol);
				String memo = "Far Horizons Payout: " + gameTurnPost.getPermlink().getLink();
				if (alreadyPaid.contains(player + "|" + memo)
						|| alreadyPaid.contains(player + "|" + gameTurnPost.getPermlink().getLink())) {
					System.out.println(" - Already Paid: " + player);
					continue;
				}
				if (BigDecimal.ZERO.compareTo(payout) == 0) {
					System.out.println(" - " + player + " doesn't get a payout.");
					continue;
				} else {
					System.out.println(" - Paying " + player + " " + payout.toPlainString() + currency);
				}
				TransferOperation transfer = new TransferOperation(botAccount, to, amount, memo);
				try {
					doSteemOps(transfer);
				} catch (SteemCommunicationException | SteemResponseException | SteemInvalidTransactionException e) {
					// abort payout sequence, hopefully can get it right later!
					System.out.println(" - Payout transaction error. Aborting.");
					return;
				}
			}
			System.out.println(" - Posting reward notification.");
			doPostRewardNotification(gameDir, aro.getPermlink().getLink(), turn, activePlayers, payouts, pool,
					isSbdPayout);
			FileUtils.touch(semaphore);
		}
	}

	private void doPostRewardNotification(File gameDir, String payfromlink, String turn, Set<String> payees,
			Map<String, BigDecimal> payouts, BigDecimal pool, boolean isSbdPayout)
			throws IOException, InterruptedException, SteemCommunicationException, SteemResponseException {

		String title = generatePayoutTitle(gameDir, pool, turn);
		Permlink permlink = new Permlink(SteemJUtils.createPermlinkString(title));
		Discussion maybeAlready = steemJ.getContent(botAccount, permlink);
		if (maybeAlready != null && !isBlank(maybeAlready.getTitle())) {
			System.out.println(" - Already posted: " + maybeAlready.getTitle());
			return;
		}
		String payoutHtml = generateAndSavePayoutResultsHtml(gameDir, turn, payfromlink, payees, payouts, isSbdPayout);
		/*
		 * do NOT use game id in these tags, it will confuse the game client!
		 */
		String[] tags = new String[5];
		tags[0] = "playbypost";
		tags[1] = "far-horizons";
		tags[2] = "strategy-game";
		tags[3] = "steem-game";
		tags[4] = "games";

		while (true) {
			try {
				System.out.println("POSTING: " + title);
				waitCheckBeforePosting(steemJ);
				steemJ.createPost(title, payoutHtml, tags, MIME_HTML, defaultMetadata);
				return;
			} catch (Exception e) {
				System.err.println("Posting error. Sleeping 5 minutes.");
				if (e.getMessage().contains("STEEMIT_MIN_ROOT_COMMENT_INTERVAL")) {
					System.err.println("STEEMIT_MIN_ROOT_COMMENT_INTERVAL");
				} else {
					System.err.println(e.getClass().getName() + ":\n" + e.getMessage());
				}
				sleep(5l * 60l * 1000l);
			}
		}
	}

	private void doSteemOps(Operation... operations)
			throws SteemCommunicationException, SteemResponseException, SteemInvalidTransactionException {
		doSteemOps(Arrays.asList(operations));
	}

	private void doSteemOps(List<Operation> operations)
			throws SteemCommunicationException, SteemResponseException, SteemInvalidTransactionException {
		SignedTransaction signedTransaction = new SignedTransaction(
				steemJ.getDynamicGlobalProperties().getHeadBlockId(), operations, null);
		signedTransaction.sign();
		steemJ.broadcastTransaction(signedTransaction);
		return;
	}

	private boolean isBlank(String gameId) {
		return gameId == null || gameId.trim().isEmpty();
	}

	private void doGameCleanup() throws SteemCommunicationException, SteemResponseException, JsonParseException,
			JsonMappingException, IOException, InterruptedException {
		Set<String> already = new HashSet<>();
		List<CommentBlogEntry> entries = steemJ.getBlog(botAccount, 0, (short) 100);
		gameScan: for (CommentBlogEntry entry : entries) {
			// if not by game master, SKIP
			if (!entry.getComment().getAuthor().equals(botAccount)) {
				// System.out.println("- SKIPPING POST BY : " +
				// entry.getComment().getAuthor().getName());
				continue;
			}

			CommentMetadata metadata = json.readValue(entry.getComment().getJsonMetadata(), CommentMetadata.class);
			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue;
			}
			/*
			 * get game# tag and abort if game data doesn't exist
			 */
			File gameDir = null;
			for (String tag : tags) {
				if (!tag.startsWith("game-")) {
					continue;
				}
				if (already.contains(tag)) {
					continue gameScan;
				}
				gameDir = new File(dataDir, tag);
				if (!new File(gameDir, "galaxy.dat").exists()) {
					already.add(tag);
					continue gameScan;
				}
				already.add(tag);
				break;
			}
			if (gameDir == null) {
				continue gameScan;
			}
			backupAndClean(gameDir);
		}
	}

	private void backupAndClean(File gameDir) throws InterruptedException, IOException {
		String tn = getTurnNumber(gameDir);
		File backupDir = new File(gameDir, "backup/start-of-turn-" + tn);
		File reportsDir = new File(gameDir, "reports");
		backupDir.mkdirs();
		for (File file : FileUtils.listFiles(gameDir, null, false)) {
			if (file.getName().endsWith(".ord")) {
				System.out.println("(move) " + file.getName() + " => " + backupDir.getName());
				FileUtils.deleteQuietly(new File(backupDir, file.getName()));
				FileUtils.moveFileToDirectory(file, backupDir, true);
				continue;
			}
			if (file.getName().contains(".rpt.")) {
				System.out.println("(move) " + file.getName() + " => " + reportsDir.getName());
				FileUtils.deleteQuietly(new File(reportsDir, file.getName()));
				FileUtils.moveFileToDirectory(file, reportsDir, true);
				continue;
			}
			if (file.getName().endsWith(".html")) {
				System.out.println("(move) " + file.getName() + " => " + reportsDir.getName());
				FileUtils.deleteQuietly(new File(reportsDir, file.getName()));
				FileUtils.moveFileToDirectory(file, reportsDir, true);
				continue;
			}
			if (file.getName().endsWith(".dat")) {
				System.out.println("(copy) " + file.getName() + " => " + backupDir.getName());
				FileUtils.copyFileToDirectory(file, backupDir, true);
				continue;
			}
		}
		File statsDir = new File(reportsDir, "stats");
		statsDir.mkdir();

		ProcessBuilder xpb = new ProcessBuilder(new File(gameDir, "bin/Stats").getAbsolutePath());
		xpb.redirectOutput(new File(statsDir, "stats.t" + tn));
		xpb.redirectErrorStream(true);
		xpb.directory(gameDir);
		Process xprocess = xpb.start();
		xprocess.waitFor();
		System.out.println("Stats created");

		for (File file : FileUtils.listFiles(gameDir, null, false)) {
			if (file.getName().equals("interspecies.dat")) {
				System.out.println("(delete) " + file.getName());
				file.delete();
				continue;
			}
			if (file.getName().endsWith(".log")) {
				System.out.println("(delete) " + file.getName());
				file.delete();
				continue;
			}
			if (file.getName().endsWith(".msg")) {
				System.out.println("(delete) " + file.getName());
				file.delete();
				continue;
			}
		}
	}

	private void doRunGameTurn() throws SteemCommunicationException, SteemResponseException, JsonParseException,
			JsonMappingException, IOException, InterruptedException, SteemInvalidTransactionException {
		Set<String> already = new HashSet<>();
		List<CommentBlogEntry> entries = steemJ.getBlog(botAccount, 0, (short) 100);
		List<CommentBlogEntry> activeGames = new ArrayList<>();
		Map<Integer, CommentMetadata> metadataMap = new HashMap<>();
		Map<Integer, String> gameIdMap = new HashMap<>();
		Map<Integer, File> gameDirMap = new HashMap<>();
		Map<Integer, Set<String>> tagsMap = new HashMap<>();
		Map<Integer, String> turnNoMap = new HashMap<>();
		// Ensure sort is newest to oldest so that "already" tracking works correctly.
		Collections.sort(entries, (a, b) -> b.getEntryId() - a.getEntryId());
		gameScan: for (CommentBlogEntry gameTurnEntry : entries) {
			// if not by game master, SKIP
			Comment gameTurnComment = gameTurnEntry.getComment();
			if (gameTurnComment == null) {
				System.err.println("NULL Comment?");
				continue gameScan;
			}
			if (!gameTurnComment.getAuthor().equals(botAccount)) {
				continue gameScan;
			}

			CommentMetadata metadata = json.readValue(gameTurnComment.getJsonMetadata(),
					CommentMetadata.class);
			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue gameScan;
			}
			if (tags.contains("new-game")) {
				continue gameScan;
			}
			/*
			 * get game# tag and abort if game data doesn't exist
			 */
			File gameDir = null;
			String gameId = "INVALID";
			for (String tag : tags) {
				if (!tag.startsWith("game-")) {
					continue;
				}
				gameId = StringUtils.substringAfter(tag, "-");
				if (already.contains(tag)) {
					continue gameScan;
				}
				gameDir = new File(dataDir, tag);
				if (!new File(gameDir, "galaxy.dat").exists()) {
					already.add(tag);
					System.err.println("Invalid game: "+gameTurnComment.getTitle());
					continue gameScan;
				}
				already.add(tag);
				break;
			}
			if (gameDir == null) {
				continue gameScan;
			}
			// make sure the post's turn number matches the game data turn number!
			String turnNumber = getTurnNumber(gameDir);
			FarHorizonsGameData farHorizonsGameData = metadata.getFarHorizonsGameData();
			String gameTurnTitle = StringUtils.normalizeSpace(gameTurnComment.getTitle());
			if (farHorizonsGameData == null || farHorizonsGameData.getStartingTurnNumber() == null
					|| farHorizonsGameData.getStartingTurnNumber().trim().isEmpty()) {
				// Attempt to extract turn number from post title
				String lcTitle = gameTurnTitle.toLowerCase();
				if (lcTitle.contains("game complete")) {
					System.err.println("Ignoring: "+gameTurnComment.getTitle());
					continue gameScan;
				}
				if (!lcTitle.contains("turn")) {
					System.err.println("UNABLE TO DETERMINE TURN NUMBER!");
					System.err.println(" - '" + gameTurnTitle + "'");
					continue gameScan;
				}
				String titleTurnNumber = lcTitle.replaceAll(".*turn (\\d+).*", "$1");
				if (!titleTurnNumber.matches("\\d+")) {
					System.err.println("MALFORMED TURN NUMBER: " + titleTurnNumber);
					System.err.println(" - '" + gameTurnTitle + "'");
					continue gameScan;
				}
				if (!titleTurnNumber.equals(turnNumber)) {
					System.err.println("WRONG TURN NUMBER. Expecting " + turnNumber + " but have " + titleTurnNumber);
					System.err.println(" - '" + gameTurnTitle + "'");
					continue gameScan;
				}
			} else {
				// Use turn number from metadata
				if (!farHorizonsGameData.getStartingTurnNumber().equals(turnNumber)) {
					System.err.println("WRONG TURN NUMBER. Expecting " + turnNumber + " but have "
							+ farHorizonsGameData.getStartingTurnNumber());
					System.err.println(" - '" + gameTurnTitle + "'");
					continue gameScan;
				}
			}

			File semGameComplete = new File(gameDir, SEM_GAMECOMPLETE);
			if (semGameComplete.exists()) {
				System.out.println("Game over: " + gameId);
				continue gameScan;
			}
			activeGames.add(gameTurnEntry);
			int entryId = gameTurnEntry.getEntryId();
			metadataMap.put(entryId, metadata);
			gameIdMap.put(entryId, gameId);
			gameDirMap.put(entryId, gameDir);
			tagsMap.put(entryId, tags);
			turnNoMap.put(entryId, turnNumber);
		}
		/*
		 * sort from oldest to newest to make sure oldest games are processed in case of
		 * RC drop
		 */
		Collections.sort(activeGames, (a, b) -> a.getEntryId() - b.getEntryId());
		/*
		 * process the game until RCs run out
		 */
		gameScan: for (CommentBlogEntry gameTurnEntry : activeGames) {

			if (!haveEnoughRcs(botAccount)) {
				System.err.println("RCs dropped - skipping: "+gameTurnEntry.getComment().getTitle());
				break gameScan;
			}

			int entryId = gameTurnEntry.getEntryId();

			String gameId = gameIdMap.get(entryId);
			File gameDir = gameDirMap.get(entryId);
			String turnNumber = turnNoMap.get(entryId);
			File semGameComplete = new File(gameDir, SEM_GAMECOMPLETE);

			Map<String, String> playerInfo = new HashMap<>();
			Map<String, Permlink> playerPermlinks = new HashMap<>();
			Comment gameTurnComment = gameTurnEntry.getComment();
			List<Discussion> playerReplies = steemJ.getContentReplies(botAccount, gameTurnComment.getPermlink());
			playersScan: for (Discussion playerReply : playerReplies) {
				String body = playerReply.getBody();
				body = StringUtils.substringBetween(body, "<html>", "</html>");
				if (body == null || body.trim().isEmpty()) {
					continue playersScan;
				}
				body = decompress(body);
				if (body == null || !body.contains("START COMBAT")) {
					System.out.println(" - Ignoring non-orders comment by " + playerReply.getAuthor().getName());
					continue playersScan;
				}
				// parse for required fields
				playerInfo.put(playerReply.getAuthor().getName(), body);
				playerPermlinks.put(playerReply.getAuthor().getName(), playerReply.getPermlink());
			}

			System.out.println("Game " + gameId + " has " + playerInfo.size() + " active players.");

			List<String> playerList = FileUtils.readLines(new File(gameDir, "_players.tab"),
					StandardCharsets.UTF_8.name());
			Map<String, String> registeredPlayers = new HashMap<>();
			Map<String, String> registeredSpecies = new HashMap<>();
			for (String player : playerList) {
				if (StringUtils.countMatches(player, "\t") < 2) {
					continue;
				}
				String activePlayerNo = player.split("\t")[0].trim();
				String activePlayer = player.split("\t")[1].trim();
				String activeSpecies = player.split("\t")[2].trim();
				registeredPlayers.put(activePlayer, activePlayerNo);
				registeredSpecies.put(activePlayer, activeSpecies);
			}

			boolean isForceGameComplete = new File(gameDir, SEM_FORCEGAMECOMPLETE).exists();
			Random r = new Random();
			int tn;
			try {
				tn = Integer.valueOf(turnNumber);
			} catch (NumberFormatException e) {
				tn = 21;
			}
			float gameOverOdds = (float) (tn - 20) / 2000f;
			boolean isGameOver = (r.nextFloat() <= gameOverOdds) || isForceGameComplete;

			if (playerInfo.size() == 0) {
				System.out.println("No players have submitted orders yet..");
				continue gameScan;
			}

			boolean allPlayersAccountedFor = true;
			for (String registeredPlayer : registeredPlayers.keySet()) {
				if (!playerInfo.containsKey(registeredPlayer)) {
					allPlayersAccountedFor = false;
					System.out.println("Not all players accounted for.");
					break;
				}
			}

			Date posted = gameTurnComment.getCreated().getDateTimeAsDate();

			if (!allPlayersAccountedFor && !submitOrdersDeadlinePast(posted)) {
				GregorianCalendar cal = submitOrdersDeadline(posted);
				DateFormat df = DateFormat.getDateTimeInstance();
				df.setTimeZone(EST5EDT);
				String closesEst5Edt = df.format(cal.getTime());
				df.setTimeZone(TimeZone.getTimeZone("UTC"));
				String closesUtc = df.format(cal.getTime());
				System.out.println("Player response period still open. Closes " + closesEst5Edt + " EST5EDT ("
						+ closesUtc + " UTC)");
				continue gameScan;
			}

			if (!allPlayersAccountedFor) {
				System.out.println("Response period closed. Running game.");
				FileUtils.write(new File(gameDir, "noorders.txt"), "Response period closed. Running game.\n",
						StandardCharsets.UTF_8);
			} else {
				System.out.println("All players account for. Running game.");
			}

			for (String playerKey : playerInfo.keySet()) {
				String speciesNo = registeredPlayers.get(playerKey);
				while (speciesNo.length() < 2) {
					speciesNo = "0" + speciesNo;
				}
				String orderFilename = "sp" + speciesNo + ".ord";
				System.out.println("Saving orders for " + registeredSpecies.get(playerKey) + " to " + orderFilename);
				FileUtils.write(new File(gameDir, orderFilename), playerInfo.get(playerKey) + "\n",
						StandardCharsets.UTF_8);
			}

			ProcessBuilder xpb;
			Process xprocess;
			File logDir = new File(gameDir, "log");
			logDir.mkdirs();
			for (String cmd : new String[] { "NoOrders", "Combat", //
					"PreDeparture", "Jump", "Production", //
					"PostArrival", "Locations", "Strike", //
					"Finish", "Report" }) {
				System.out.println("Running: " + cmd);
				xpb = new ProcessBuilder(new File(gameDir, "bin/" + cmd).getAbsolutePath());
				if ("Combat".equals(cmd) || "Strike".equals(cmd)) {
					/*
					 * Combat sequences causing post size issues, force summary mode.
					 */
					xpb.command().add("-s");
				}
				xpb.redirectOutput(new File(logDir, "_" + cmd + ".log.txt"));
				xpb.redirectErrorStream(true);
				xpb.directory(gameDir);
				xprocess = xpb.start();
				xprocess.waitFor();
				if (xprocess.exitValue() != 0) {
					throw new RuntimeException("Command " + cmd + " failed.");
				}
			}
			backupAndClean(gameDir);
			if (isGameOver) {
				@SuppressWarnings("unused")
				String gameCompletePermlink = postGameComplete(gameDir);
				// for (String player : playerPermlinks.keySet()) {
				// Permlink playerPermlink = playerPermlinks.get(player);
				// markGameComplete(player, playerPermlink, gameCompletePermlink, tags);
				// }
				FileUtils.touch(semGameComplete);
			} else {
				String newTurnPermlink = postTurnResults(gameDir);
				postGameMaps(gameDir, new Permlink(newTurnPermlink));
				// for (String player : playerPermlinks.keySet()) {
				// Permlink playerPermlink = playerPermlinks.get(player);
				// markTurnComplete(player, playerPermlink, newTurnPermlink, tags);
				// }
			}
		}
	}

	public static String basicUnescape(String text) {
		return text.replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
	}

	private void doCreateMaps() throws SteemCommunicationException, SteemResponseException, JsonParseException,
			JsonMappingException, IOException, InterruptedException {
		List<CommentBlogEntry> entries = steemJ.getBlog(botAccount, 0, (short) 100);
		newGameScan: for (CommentBlogEntry entry : entries) {
			// if not by game master, SKIP
			if (!entry.getComment().getAuthor().equals(botAccount)) {
				continue;
			}

			CommentMetadata metadata = json.readValue(entry.getComment().getJsonMetadata(), CommentMetadata.class);
			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue;
			}
			/*
			 * get game# tag and abort if game data already exists
			 */
			File gameDir = null;
			for (String tag : tags) {
				if (!tag.startsWith("game-")) {
					continue;
				}
				gameDir = new File(dataDir, tag);
				if (new File(gameDir, "galaxy_map.pdf").exists()) {
					System.out.println("Map already exists: " + tag);
					continue newGameScan;
				}
			}
			if (gameDir == null) {
				continue newGameScan;
			}
			createMaps(gameDir);
		}
	}

	private void createMaps(File gameDir) throws IOException, InterruptedException {
		File script = new File(gameDir, "create-map.sh");
		FileUtils.write(script, CreateMapShellScript.script, StandardCharsets.UTF_8);
		script.setExecutable(true);
		ProcessBuilder xpb = new ProcessBuilder(script.getAbsolutePath());
		xpb.redirectOutput(new File(gameDir, "_create-map.log.txt"));
		xpb.redirectErrorStream(true);
		xpb.directory(gameDir);
		Process xprocess = xpb.start();
		xprocess.waitFor();
	}

	private void doAnnounceGamePost() throws IOException {
		NewGameInviteInfo info = generateNewGameInviteHtml();
		String[] tags = new String[5];
		tags[0] = "playbypost";
		tags[1] = "far-horizons";
		tags[2] = "games";
		tags[3] = "new-game";
		tags[4] = info.getGameDir().getName();
		while (true) {
			try {
				System.out.println("POSTING: " + info.getTitle());
				waitCheckBeforePosting(steemJ);
				steemJ.createPost(info.getTitle(), info.getHtml(), tags, MIME_HTML, defaultMetadata);
				return;
			} catch (Exception e) {
				System.err.println("Posting error. Sleeping 5 minutes.");
				if (e.getMessage().contains("STEEMIT_MIN_ROOT_COMMENT_INTERVAL")) {
					System.err.println("STEEMIT_MIN_ROOT_COMMENT_INTERVAL");
				} else {
					System.err.println(e.getClass().getName() + ":\n" + e.getMessage());
				}
				sleep(5l * 60l * 1000l);
			}
		}
	}

	private void doCreateHtmlResultFiles() throws SteemCommunicationException, SteemResponseException,
			JsonParseException, JsonMappingException, IOException, InterruptedException {
		Set<String> already = new HashSet<>();
		List<CommentBlogEntry> entries = steemJ.getBlog(botAccount, 0, (short) 100);
		gameScan: for (CommentBlogEntry entry : entries) {
			if (!entry.getComment().getAuthor().equals(botAccount)) {
				continue;
			}
			CommentMetadata metadata = json.readValue(entry.getComment().getJsonMetadata(), CommentMetadata.class);
			// if not by game master, SKIP

			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue;
			}
			/*
			 * get game# tag and abort if game data doesn't exist
			 */
			File gameDir = null;
			for (String tag : tags) {
				if (!tag.startsWith("game-")) {
					continue;
				}
				gameDir = new File(dataDir, tag);
				if (already.contains(tag)) {
					continue;
				}
				if (!gameDir.exists()) {
					System.out.println("Game doesn't exist: " + tag);
					continue gameScan;
				}
				generateAndSaveTurnResultsHtml(gameDir);
				already.add(tag);
			}
		}
	}

	private int getLength(String text) {
		int l1 = text.length();
		int l2 = text.getBytes(StandardCharsets.UTF_8).length;
		return l1 > l2 ? l1 : l2;
	}

	private void doProcessAnnounceReplies()
			throws SteemCommunicationException, SteemResponseException, JsonParseException, JsonMappingException,
			IOException, InterruptedException, SteemInvalidTransactionException {
		List<CommentBlogEntry> entries = steemJ.getBlog(botAccount, 0, (short) 100);
		Set<String> already = new HashSet<>();
		newGameScan: for (CommentBlogEntry entry : entries) {
			if (entry.getComment() == null) {
				System.err.println("NULL Comment?");
				continue;
			}
			// if not by game master, SKIP
			if (!entry.getComment().getAuthor().equals(botAccount)) {
				// System.out.println("- SKIPPING POST BY : " +
				// entry.getComment().getAuthor().getName());
				continue;
			}

			CommentMetadata metadata = json.readValue(entry.getComment().getJsonMetadata(), CommentMetadata.class);
			Set<String> tags = new HashSet<>(Arrays.asList(metadata.getTags()));
			if (!tags.contains("far-horizons")) {
				continue;
			}

			String gameId = "";
			for (String tag : tags) {
				if (!tag.startsWith("game-")) {
					continue;
				}
				gameId = tag.trim().toLowerCase();
				if (already.contains(gameId)) {
					continue newGameScan;
				}
				already.add(gameId);
				break;
			}
			if (gameId.trim().isEmpty()) {
				continue newGameScan;
			}
			if (!tags.contains("new-game")) {
				continue newGameScan;
			}
			if (!dataDir.exists()) {
				dataDir.mkdirs();
			}
			/*
			 * get game# tag and abort if game data already exists
			 */
			File gameDir = new File(dataDir, gameId);
			if (new File(gameDir, "galaxy.dat").exists()) {
				System.out.println("Game already started: " + gameId);
				continue newGameScan;
			}
			gameDir.mkdirs();

			Map<String, NewGameInfo> playerInfo = new HashMap<>();
			Map<String, Permlink> playerPermlinks = new HashMap<>();
			List<Discussion> discussions = steemJ.getContentReplies(botAccount, entry.getComment().getPermlink());
			boolean gameFull = false;
			newPlayersScan: for (Discussion discussion : discussions) {
				String body = discussion.getBody();
				if (body.startsWith("<html>")) {
					body = StringUtils.substringBetween(body, "<html>", "</html>");
					body = decompress(body);
					if (body == null) {
						continue;
					}
				}
				// parse for required fields
				NewGameInfo info = NewGameInfo.parse(body);
				while (getLength(info.getSpeciesName()) < 7) {
					info.setSpeciesName("-" + info.getSpeciesName() + "-");
				}
				if (!info.isValid()) {
					continue;
				}
				info.setPlayer(discussion.getAuthor().getName());
				playerInfo.put(info.getPlayer(), info);
				playerPermlinks.put(info.getPlayer(), discussion.getPermlink());
				if (playerInfo.size() > MAX_PLAYERS) { // immediate start player count
					gameFull = true;
				}
				if (playerInfo.size() > 98) {// back-end has a hard max of 99 players
					gameFull = true;
					break newPlayersScan;
				}
			}
			System.out.println("Game " + gameId + " has " + playerInfo.size() + " players.");
			Date posted = entry.getComment().getCreated().getDateTimeAsDate();
			if (!gameFull && !joinGameDeadlinePast(posted)) {
				GregorianCalendar joinGameDeadline = joinGameDeadline(posted);
				DateFormat df = DateFormat.getDateTimeInstance();
				df.setTimeZone(EST5EDT);
				String closesEst5Edt = df.format(joinGameDeadline.getTime());
				df.setTimeZone(TimeZone.getTimeZone("UTC"));
				String closesUtc = df.format(joinGameDeadline.getTime());
				System.out.println(
						"Player join period still open. Closes " + closesEst5Edt + " EST5EDT (" + closesUtc + " UTC)");
				continue newGameScan;
			}
			if (playerInfo.size() < 2) {
				System.out.println("Game " + gameId + " doesn't have enough players yet.");
				continue newGameScan;
			}
			if (gameFull) {
				System.out.println("MAX PLAYERS REACHED - DOING IMMEDIATE GAME START: " + gameDir.getName());
			}
			System.out.println("Setting up game folder: " + gameDir.getName());

			/*
			 * prevent duplicate species names and truncate over long names
			 */
			Set<String> speciesAlready = new HashSet<>();
			List<NewGameInfo> values = new ArrayList<>(playerInfo.values());
			Collections.shuffle(values);
			for (NewGameInfo info : values) {
				while (getLength(info.getSpeciesName()) > 31) {
					info.setSpeciesName(info.getSpeciesName().substring(0, info.getSpeciesName().length() - 1));
				}
				while (getLength(info.getGovernmentName()) > 31) {
					info.setGovernmentName(
							info.getGovernmentName().substring(0, info.getGovernmentName().length() - 1));
				}
				while (getLength(info.getHomePlanetName()) > 31) {
					info.setHomePlanetName(
							info.getHomePlanetName().substring(0, info.getHomePlanetName().length() - 1));
				}
				while (getLength(info.getGovernmentType()) > 31) {
					info.setGovernmentType(
							info.getGovernmentType().substring(0, info.getGovernmentType().length() - 1));
				}

				String speciesName = info.getSpeciesName();
				String newName = speciesName;
				while (speciesAlready.contains(newName)) {
					while (getLength(speciesName) > 28) {
						speciesName = speciesName.substring(0, speciesName.length() - 1);
					}
					newName = speciesName + "-" + (new Random().nextInt(99));
				}
				if (!newName.equals(speciesName)) {
					info.setSpeciesName(newName);
				}
				speciesAlready.add(newName);
			}

			FileUtils.copyDirectory(binDir, new File(gameDir, "bin"));
			StringBuilder setup = new StringBuilder();
			setup.append("#!/bin/bash\n");
			setup.append("set -e\n");
			setup.append("set -o pipefail\n");
			setup.append("\n");
			setup.append("cd \"$(dirname \"$0\")\"\n");
			setup.append("\n");
			setup.append("chmod +x ./bin/*\n");
			setup.append("while [ 1 ]; do\n");
			int sparserGalaxy = (playerInfo.size() * 3) / 2;
			setup.append("./bin/NewGalaxy " + sparserGalaxy + "\n");
			setup.append("d=\"$(./bin/ListGalaxy|grep -v '^$' |sort|uniq -cd)\"\n");
			setup.append("if [ \"$d\"x = x ]; then break; fi\n");
			setup.append("echo \"Regenerating galaxy. Duplicate star found.\"\n");
			setup.append("done\n");
			setup.append("./bin/MakeHomes\n");
			setup.append("./bin/ListGalaxy -p\n");
			setup.append("echo 99999|./bin/MapGalaxy\n");
			setup.append("\n");
			int speciesNo = 0;
			List<String> playerList = new ArrayList<>();
			for (NewGameInfo player : values) {
				speciesNo++;
				int b = player.getBiology();
				int g = player.getBiology();
				int l = player.getLifesupport();
				int m = player.getMilitary();
				// make sure factors don't exceed 15
				while (b + g + l + m > 15) {
					switch (new Random().nextInt(4)) {
					case 0:
						if (b > 1) {
							b--;
						}
						break;
					case 1:
						if (g > 1) {
							g--;
						}
						break;
					case 2:
						if (l > 1) {
							l--;
						}
						break;
					case 3:
						if (m > 1) {
							m--;
						}
						break;
					default:
					}
				}
				// make sure factors aren't less than 15
				while (b + g + l + m < 15) {
					switch (new Random().nextInt(4)) {
					case 0:
						b++;
						break;
					case 1:
						g++;
						break;
					case 2:
						l++;
						break;
					case 3:
						m++;
						break;
					default:
					}
				}

				String gn = player.getGovernmentName();
				String gt = player.getGovernmentType();
				String hpn = player.getHomePlanetName();
				String sn = player.getSpeciesName();
				setup.append("echo $(./bin/HomeSystemAuto 12)|while read x y z n; do\n");

				setup.append("./bin/AddSpeciesAuto " + speciesNo);
				setup.append(" " + simpleBashQuote(sn));
				setup.append(" " + simpleBashQuote(hpn));
				setup.append(" " + simpleBashQuote(gn));
				setup.append(" " + simpleBashQuote(gt));
				setup.append(" $x $y $z $n ");
				setup.append(m + " " + g + " " + l + " " + b);
				setup.append("\n");
				setup.append("break\n");
				setup.append("done\n");
				playerList.add(speciesNo + "\t" + player.getPlayer() + "\t" + sn);
			}
			setup.append("./bin/Finish\n");
			setup.append("./bin/Report\n");

			/*
			 * save out player to species mapping
			 */
			FileUtils.writeLines(new File(gameDir, "_players.tab"), StandardCharsets.UTF_8.name(), playerList);

			/*
			 * save out setup script and run it
			 */
			File setupScript = new File(gameDir, "setup.sh");
			FileUtils.write(setupScript, setup.toString(), StandardCharsets.UTF_8);
			setupScript.setExecutable(true);
			ProcessBuilder pb = new ProcessBuilder(setupScript.getAbsolutePath());
			pb.redirectOutput(new File(gameDir, "_setup.log"));
			pb.redirectErrorStream(true);
			pb.directory(gameDir);
			Process process = pb.start();
			int status = process.waitFor();
			if (status != 0) {
				System.err.println("BAD JUJU - RENAMING GAME DIRECTORY");
				gameDir.renameTo(new File(gameDir.getAbsolutePath() + "-BAD-" + System.currentTimeMillis()));
				continue newGameScan;
			}

			// assuming all went well with no error exit status...
			createMaps(gameDir);

			backupAndClean(gameDir);
			@SuppressWarnings("unused")
			String newTurnPermlink = postTurnResults(gameDir);
			// post back to previous post a reply with new post link
			// markGameStarted(accountName, entry.getComment().getPermlink(),
			// newTurnPermlink, tags);

			// for (String player : playerPermlinks.keySet()) {
			// Permlink playerPermlink = playerPermlinks.get(player);
			// markTurnComplete(player, playerPermlink, newTurnPermlink, tags);
			// }
		}
	}

	private boolean submitOrdersDeadlinePast(Date posted) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(posted);
		return new GregorianCalendar().after(submitOrdersDeadline(cal));
	}

	private boolean joinGameDeadlinePast(Date posted) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(posted);
		return new GregorianCalendar().after(joinGameDeadline(cal));
	}

	private String simpleBashQuote(String arg) {
		return "'" + arg.replace("'", "'\\''") + "'";
	}

	private void postGameMaps(File gameDir, Permlink parentPermlink) {
		String mapImagesHtml = getMapImagesHtml(gameDir);
		if (mapImagesHtml.isEmpty()) {
			System.out.println("NO MAPS TO POST!");
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<h3>GAME STAR MAPS</h3>");
		sb.append(mapImagesHtml);
		sb.append("</html>");
		while (true) {
			sleep(25l * 1000l);// 25 seconds
			try {
				String[] tags = getGameTurnTags(gameDir);
				waitCheckBeforeReplying(steemJ);
				steemJ.createComment(botAccount, parentPermlink, sb.toString(), tags, MIME_HTML, defaultMetadata);
				System.out.println("MAPS POSTED! [" + parentPermlink.getLink() + "]");
				return;
			} catch (SteemCommunicationException | SteemResponseException | SteemInvalidTransactionException e) {
				System.err.println("Posting error. Retry in 25 seconds. [" + parentPermlink.getLink() + "]");
			}
		}
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e1) {
		}
	}

	private TurnResults generateAndSaveTurnResultsHtml(File gameDir) throws IOException, InterruptedException {
		String tn = getTurnNumber(gameDir);
		File htmlFile = new File(gameDir, "reports/_steem-post-" + tn + ".html");
		File gamedataFile = new File(gameDir, "reports/_steem-post-" + tn + ".data");
		GregorianCalendar cal = submitOrdersDeadline(new GregorianCalendar(EST5EDT));
		DateFormat df = DateFormat.getDateTimeInstance();
		df.setTimeZone(EST5EDT);
		String deadlineEst5Edt = basicEscape(df.format(cal.getTime())) + " EST5EDT";
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		String deadlineUtc = basicEscape(df.format(cal.getTime())) + " UTC";

		System.out.println("Generating HTML for game: " + gameDir.getName());
		StringBuilder turnMessage = new StringBuilder();
		turnMessage.append("<html charset='UTF-8'>");
		turnMessage.append("<div>");
		turnMessage.append("<h1>Far Horizons Steem</h1>");
		turnMessage.append(DIV_PULL_RIGHT_START);
		turnMessage.append("<img style='max-width:100%;' src='" + NasaApod.getApodImageUrl(gameDir) + "'/>");
		turnMessage.append("<center>(Image courtesy of NASA's \"Astronomy Picture of the Day\")</center>");
		turnMessage.append("</div>");
		turnMessage.append("<h2>Start of Turn ");
		turnMessage.append(tn);
		turnMessage.append("</h2>");
		/*
		 * load players
		 */
		turnMessage.append("<h3>Players</h3>");
		turnMessage.append("<p>");
		List<String> players = getPlayers(gameDir);
		for (String player : players) {
			turnMessage.append(StringEscapeUtils.escapeHtml4(player));
			turnMessage.append(" ");
		}
		turnMessage.append("</p>");
		turnMessage.append("<h3>Attention Players</h3>");
		turnMessage.append("<p>");
		turnMessage.append("Visit: [");
		turnMessage.append("<a href='http://muksihs.com/Far-Horizons-Steem-Client/#");
		turnMessage.append(_PERMLINK);
		turnMessage.append("' target='_blank'>Far-Horizons-Steem-Client</a>]");
		turnMessage.append(" to view your reports for this turn and to submit your commands.");
		turnMessage.append("</p>");
		turnMessage.append("<h4>You must submit your orders by:</h4>");
		turnMessage.append("<p><strong>");
		turnMessage.append(deadlineEst5Edt);
		turnMessage.append("</strong><br/><strong>");
		turnMessage.append(deadlineUtc);
		turnMessage.append("</strong>");
		turnMessage.append("</p>");
		turnMessage.append("<p>The next turn will commence shortly");
		turnMessage.append(" after all players have submitted their orders");
		turnMessage.append(" or after the submit orders deadline has passed.</p>");
		turnMessage.append("<p>The game manual is here: [");
		turnMessage.append("<a href='http://muksihs.com/Far-Horizons-Steem-Client/Far-Horizons-Manual.pdf'"
				+ " target='_blank'>PDF GAME RULES</a>");
		turnMessage.append("]. The game star maps for this game are below and are clickable.");
		turnMessage.append("</p>");
		turnMessage.append("<p>");
		turnMessage.append("<strong>Reminder</strong>: ");
		turnMessage.append("To be in the participation reward pool,");
		turnMessage.append(" you must <em>both</em> up vote this post and submit your game orders");
		turnMessage.append(" before the deadline.");
		turnMessage.append(" The participation pool is calculated on a per turn basis");
		turnMessage.append(" <em>after</em> each payout is received.");
		turnMessage.append(" No votes means no payouts means no rewards!");
		turnMessage.append("</p>");

		turnMessage.append("<h3>Want to join in to play and earn rewards?</h3>");
		turnMessage.append("<p>");
		turnMessage.append("Reply to this post asking the gamemaster to start a new game.");
		turnMessage.append(" <em>Players can only join at the start of a new game.</em>");
		turnMessage.append("</p>");

		/**
		 * For private reports.
		 */
		StringBuilder transmission = new StringBuilder();
		/**
		 * For public reports.
		 */
		StringBuilder publicInfo = new StringBuilder();
		for (String player : getPlayersRecords(gameDir)) {
			if (!player.contains("\t")) {
				continue;
			}
			String speciesNo = player.split("\t")[0].trim();
			if (speciesNo.length() < 2) {
				speciesNo = "0" + speciesNo;
			}
			String playerName = player.split("\t")[1].trim();
			File reportFile = new File(gameDir, "reports/sp" + speciesNo + ".rpt.t" + tn);
			if (!reportFile.canRead()) {
				System.err.println("CAN'T READ " + reportFile.getAbsolutePath());
			}
			String playerReport = FileUtils.readFileToString(reportFile, StandardCharsets.UTF_8);
			transmission.append("<p>");
			transmission.append("@");
			transmission.append(StringEscapeUtils.escapeHtml4(playerName));
			transmission.append(" BEGIN\n");
			transmission.append(playerReport);
			transmission.append("\n");
			transmission.append("@");
			transmission.append(StringEscapeUtils.escapeHtml4(playerName));
			transmission.append(" END\n");

			String tmp = playerReport;
			tmp = StringUtils.substringAfter(tmp, "START OF TURN");
			tmp = "Species name:" + StringUtils.substringAfter(tmp, "Species name:");
			tmp = StringUtils.substringBefore(tmp, "ORDER SECTION.");
			String[] tmpx = tmp.split("\n");
			tmp = "";
			for (String tmpy : tmpx) {
				if (tmpy.contains("Coordinates")) {
					continue;
				}
				if (tmpy.contains("Atmospheric Requirement:")) {
					continue;
				}
				if (tmpy.contains("Neutral Gases:")) {
					continue;
				}
				if (tmpy.contains("Poisonous Gases:")) {
					continue;
				}
				if (tmpy.contains("Fleet maintenance cost =")) {
					break;
				}
				if (tmpy.contains("Economic units =")) {
					continue;
				}
				if (tmpy.contains("raw material units")) {
					continue;
				}
				if (tmpy.contains("Production capacity this turn")) {
					continue;
				}
				if (tmpy.contains(SECTION_MARKER_START)) {
					break;
				}
				if (tmpy.contains("=")) {
					tmpy = tmpy.replaceAll("\\d+", "???");
				}
				tmp += tmpy + "\n";
			}
			tmp = StringEscapeUtils.escapeHtml4(tmp);
			tmp = tmp.replaceAll("\n+", "<br/>");
			tmp = tmp.replace(" ", "&nbsp;");
			publicInfo.append("=== ");
			publicInfo.append(tmp);
			publicInfo.append("---<br/>");
		}
		// turnResults.append(publicInfo);
		turnMessage.append("</div>");
		// turnMessage.append("<h6>GAME STAR MAPS.</h6>");
		// turnMessage.append(getMapImagesHtml(gameDir));
		// turnResults.append("<h4>BEGIN SECRET GALACTIC DATABASE TRANSMISSION:</h4>");
		// String secretMessage =
		// compress(transmission.toString());
		// secretMessage = "<hr/><div id='secret-message'>" + secretMessage +
		// "</div><hr/>";
		String secretMessage = compress(transmission.toString());
		System.out.println(
				"PLAYER REPORTS: " + transmission.toString().getBytes(StandardCharsets.UTF_8).length + " UTF-8 bytes, "
						+ secretMessage.getBytes(StandardCharsets.UTF_8).length + " UTF-8 compressed bytes.");
		// turnResults.append(secretMessage);
		// turnResults.append("<h5>END SECRET GALACTIC DATABASE TRANSMISSION.</h5>");
		turnMessage.append("</html>");
		String title = generateTurnTitle(gameDir, tn);
		String permlink = "@" + accountName + "/" + SteemJUtils.createPermlinkString(title);
		String turnResultsHtml = turnMessage.toString();
		turnResultsHtml = turnResultsHtml.replace(_PERMLINK, permlink);
		FileUtils.write(htmlFile, turnResultsHtml + "\n", StandardCharsets.UTF_8);
		FileUtils.write(gamedataFile, secretMessage, StandardCharsets.UTF_8);
		TurnResults turnResults = new TurnResults();
		turnResults.setMessage(turnResultsHtml + "\n");
		turnResults.setCompressedGameData(secretMessage);
		return turnResults;
	}

	private List<String> getPlayers(File gameDir) throws IOException {
		List<String> playerList = new ArrayList<>();
		String playersTxt = FileUtils.readFileToString(new File(gameDir, "_players.tab"), StandardCharsets.UTF_8);
		String[] players = playersTxt.split("\n");
		for (String player : players) {
			if (!player.contains("\t")) {
				continue;
			}
			playerList.add("@" + StringEscapeUtils.escapeHtml4(player.split("\t")[1].trim()));
		}
		return playerList;
	}

	private List<String> getPlayersRecords(File gameDir) throws IOException {
		List<String> playerList = new ArrayList<>();
		String playersTxt = FileUtils.readFileToString(new File(gameDir, "_players.tab"), StandardCharsets.UTF_8);
		String[] players = playersTxt.split("\n");
		for (String player : players) {
			if (!player.contains("\t")) {
				continue;
			}
			playerList.add(player);
		}
		return playerList;
	}

	private String generateAndSavePayoutResultsHtml(File gameDir, String turn, String permlink, Set<String> payees,
			Map<String, BigDecimal> payouts, boolean isSbdPayout) throws IOException, InterruptedException {
		File htmlFile = new File(gameDir, "reports/_steem-payout-" + permlink + ".html");
		if (htmlFile.exists()) {
			return FileUtils.readFileToString(htmlFile, StandardCharsets.UTF_8);
		}
		System.out.println("Generating HTML for payout report: " + permlink);
		if (turn == null || turn.trim().isEmpty()) {
			turn = "";
		} else {
			turn = " - For Turn " + turn;
		}
		String currency = isSbdPayout ? " SBD" : " STEEM";
		StringBuilder turnResults = new StringBuilder();
		turnResults.append("<html charset='UTF-8'>");
		turnResults.append("<div>");
		turnResults.append("<h1>Far Horizons Steem - PAYOUT REPORT" + turn + "</h1>");
		turnResults.append(DIV_PULL_RIGHT_START);
		turnResults.append("<img style='max-width:100%;' src='" + NasaApod.getApodImageUrl(gameDir) + "'/>");
		turnResults.append("<center>(Image courtesy of NASA's \"Astronomy Picture of the Day\")</center>");
		turnResults.append("</div>");
		turnResults.append("<h3>Participating Players</h3>");
		turnResults.append("<p>");
		for (String player : payees) {
			turnResults.append("@");
			turnResults.append(StringEscapeUtils.escapeHtml4(player));
			turnResults.append(" ");
		}
		turnResults.append("</p>");
		turnResults.append("<ul>Player payouts: ");
		for (String player : payees) {
			turnResults.append("<li>");
			BigDecimal payout = payouts.get(player);
			turnResults.append("@");
			turnResults.append(StringEscapeUtils.escapeHtml4(player));
			turnResults.append(": ");
			turnResults.append(payout.toPlainString());
			turnResults.append(currency);
			turnResults.append(".</li>");
		}
		turnResults.append("</ul>");
		turnResults.append("<h3>Attention Players</h3>");
		turnResults.append("<p>");
		turnResults.append("<strong>Reminder</strong>: ");
		turnResults.append("To be in the");
		turnResults.append(" participation reward pool,");
		turnResults.append(" you must <em>both</em> up vote the paying turn post and submit your game orders");
		turnResults.append(" before the deadline.");
		turnResults.append(" The participation pool is calculated on a per turn basis");
		turnResults.append(
				" <em>after</em> each payout is received. Rewards are directly proportional to your vote's value!");
		turnResults.append(" No votes means no payouts!");
		turnResults.append("</p>");

		turnResults.append("<h3>Want to join in to play and earn rewards?</h3>");
		turnResults.append("<p>");
		turnResults.append("Reply to this post asking the gamemaster to start a new game.");
		turnResults.append(" <em>Players can only join at the start of a new game.</em>");
		turnResults.append("</p>");
		turnResults.append("<p>Paying turn: <a href='");
		turnResults.append("https://www.steemit.com/@");
		turnResults.append(accountName);
		turnResults.append("/");
		turnResults.append(permlink);
		turnResults.append("'>");
		turnResults.append(permlink);
		turnResults.append("</a></p>");
		turnResults.append("</html>");
		String payoutResultsHtml = turnResults.toString();
		FileUtils.write(htmlFile, payoutResultsHtml + "\n", StandardCharsets.UTF_8);
		return payoutResultsHtml;
	}

	private String generatePayoutTitle(File gameDir, BigDecimal pool, String turn) {
		if (turn == null || turn.trim().isEmpty()) {
			turn = "";
		} else {
			turn = "For turn " + turn;
		}
		return "Far Horizons Steem - PAYOUT REPORT - " + gameDir.getName() + " " + turn + " - " + pool.toPlainString()
				+ " PLAYER POOL REWARD";
	}

	public static class NewGameInviteInfo {
		private String title;
		private String html;
		private String permlink;
		private File gameDir;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getPermlink() {
			return permlink;
		}

		public void setPermlink(String permlink) {
			this.permlink = permlink;
		}

		public File getGameDir() {
			return gameDir;
		}

		public void setGameDir(File gameDir) {
			this.gameDir = gameDir;
		}

		public String getHtml() {
			return html;
		}

		public void setHtml(String html) {
			this.html = html;
		}
	}

	private GregorianCalendar submitOrdersDeadline(Date date) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(date);
		return submitOrdersDeadline(cal);
	}

	private GregorianCalendar postPayoutDeadline(Date date) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(date);
		return postPayoutDeadline(cal);
	}

	/**
	 * 7 days from turn post for vote deadline. No rounding.
	 * 
	 * @param cal
	 * @return
	 */
	private GregorianCalendar postPayoutDeadline(GregorianCalendar cal) {
		GregorianCalendar deadline = new GregorianCalendar(EST5EDT);
		deadline.setTime(cal.getTime());
		deadline.add(GregorianCalendar.DAY_OF_YEAR, +7);
		return deadline;
	}

	/**
	 * 6 days from turn post for submit deadline.
	 * 
	 * @param cal
	 * @return
	 */
	private GregorianCalendar submitOrdersDeadline(GregorianCalendar cal) {
		GregorianCalendar deadline = new GregorianCalendar(EST5EDT);
		deadline.setTime(cal.getTime());
		deadline.setTime(cal.getTime());
		deadline.add(GregorianCalendar.DAY_OF_YEAR, +6);
		int minute = deadline.get(GregorianCalendar.MINUTE);
		// use int math to set to lowest matching quarter hour value;
		minute /= 15;
		minute *= 15;
		deadline.set(GregorianCalendar.MINUTE, minute);
		deadline.set(GregorianCalendar.SECOND, 0);
		deadline.set(GregorianCalendar.MILLISECOND, 0);
		return deadline;
	}

	/**
	 * 3 days from invite post for submit deadline.
	 * 
	 * @param cal
	 * @return
	 */
	private GregorianCalendar joinGameDeadline(GregorianCalendar cal) {
		GregorianCalendar deadline = new GregorianCalendar(EST5EDT);
		deadline.setTime(cal.getTime());
		deadline.setTime(cal.getTime());
		deadline.add(GregorianCalendar.DAY_OF_YEAR, +2);
		int minute = deadline.get(GregorianCalendar.MINUTE);
		// use int math to set to lowest matching quarter hour value;
		minute /= 15;
		minute *= 15;
		deadline.set(GregorianCalendar.MINUTE, minute);
		deadline.set(GregorianCalendar.SECOND, 0);
		deadline.set(GregorianCalendar.MILLISECOND, 0);
		return deadline;
	}

	private GregorianCalendar joinGameDeadline(Date date) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(date);
		return joinGameDeadline(cal);
	}

	private NewGameInviteInfo generateNewGameInviteHtml() throws IOException {
		GregorianCalendar cal = joinGameDeadline(new GregorianCalendar());

		DateFormat df = DateFormat.getDateTimeInstance();
		df.setTimeZone(EST5EDT);
		String deadlineEst5Edt = basicEscape(df.format(cal.getTime())) + " EST5EDT";
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		String deadlineUtc = basicEscape(df.format(cal.getTime())) + " UTC";
		String gameId = null;
		File gameDir = null;
		do {
			gameId = "game-" + (System.currentTimeMillis() / 1000l);
			gameDir = new File(dataDir, gameId);
		} while (gameDir.exists());

		gameDir.mkdirs();

		System.out.println("Generating HTML for new game invite.");
		StringBuilder gameInvite = new StringBuilder();
		gameInvite.append("<html charset='UTF-8'>");
		gameInvite.append("<div>");
		gameInvite.append("<h1>Far Horizons Steem - New Game Invite</h1>");
		gameInvite.append("<h3>Attention Game Players!</h3>");
		gameInvite.append("<p>");
		gameInvite.append("Visit: [");
		gameInvite.append("<a href='http://muksihs.com/Far-Horizons-Steem-Client/#");
		gameInvite.append(_PERMLINK);
		gameInvite.append("' target='_blank'>Far-Horizons-Steem-Client</a>]");
		gameInvite.append(" to fill out your species information form to join this game.");
		gameInvite.append("</p>");
		gameInvite.append("<h4>You must submit your game join form by:</h4>");
		gameInvite.append("<p><strong>");
		gameInvite.append(deadlineEst5Edt);
		gameInvite.append("</strong><br/><strong>");
		gameInvite.append(deadlineUtc);
		gameInvite.append("</strong>");
		gameInvite.append("</p>");
		// gameInvite.append("<h3>Looking for BETA testers!</h3>");
		// gameInvite.append("<p>I am in need of players willing to play!");
		// gameInvite.append(" The initial player UI work has been completed,");
		// gameInvite.append(" but alot more needs to be done and I need more players");
		// gameInvite.append(" using the UI to test it and provide feedback.</p>");
		// gameInvite.append("<p>My goal is a (hopefully) user friendly front-end to
		// the");
		// gameInvite.append(" classic play by email Far Horizons turn based RPG.</p>");
		gameInvite.append("<h4>More about Far Horizons</h4>");
		gameInvite.append("<blockquote>");
		gameInvite.append("FAR HORIZONS is a strategic role-playing game of galactic");
		gameInvite.append(" exploration, trade, diplomacy, and conquest.");
		gameInvite.append("<br/><br/>");
		gameInvite.append("At the start of a game, each player controls an intelligent");
		gameInvite.append(" species and the home planet on which it lives. As the game");
		gameInvite.append(" progresses, you can explore nearby regions of the galaxy and");
		gameInvite.append(" establish colonies.  As you range farther and farther");
		gameInvite.append(" from home, you will encounter other intelligent species.");
		gameInvite.append(" These encounters can be hostile, neutral, or friendly, depending");
		gameInvite.append(" on the participants. Interstellar war is a distinct possibility.");
		gameInvite.append("<br/><br/>");
		gameInvite.append("FAR HORIZONS, unlike some similar games, has been designed to");
		gameInvite.append(" make role-playing as easy and practical as possible. In addition");
		gameInvite.append(" to being a rich and realistic simulation, there are no true");
		gameInvite.append(" victory conditions - the game is played solely for enjoyment.");
		gameInvite.append(" However, at the end of the last turn, final statistics for all");
		gameInvite.append(" species will be sent to all of the players so that they can");
		gameInvite.append(" compare their relative strengths and weaknesses. Thus, rather");
		gameInvite.append(" than requiring a massive bloodletting as in some other similar");
		gameInvite.append(" games, it's possible for a peace-loving species to effectively");
		gameInvite.append(" \"win\".");
		gameInvite.append("<br/><br/>");
		gameInvite.append("Still, those who enjoy a more aggressive game, or those who");
		gameInvite.append(" wish to role-play an \"evil\" or warlike species");
		gameInvite.append(" will not be disappointed. FAR HORIZONS does not discriminate");
		gameInvite.append(" against anyone - it simply tries to be as realistic as possible.");
		gameInvite.append("</blockquote>");
		gameInvite.append(DIV_PULL_RIGHT_START);
		gameInvite.append("<img style='max-width:100%;' src='" + NasaApod.getApodImageUrl(gameDir) + "'/>");
		gameInvite.append("<center>(Image courtesy of NASA's \"Astronomy Picture of the Day\")</center>");
		gameInvite.append("</div>");
		gameInvite.append("<p>The first turn will commence shortly");
		gameInvite.append(" after the join deadline has passed and");
		gameInvite.append(" there are at least two players registered.</p>");
		gameInvite.append("<p>There is currently a maximun limit of ");
		gameInvite.append(MAX_PLAYERS);
		gameInvite.append(" players per game.</p>");
		gameInvite.append("<p>You may also participate in multiple games.</p>");
		gameInvite.append("<p>The game manual is here: [");
		gameInvite.append("<a href='http://muksihs.com/Far-Horizons-Steem-Client/Far-Horizons-Manual.pdf'"
				+ " target='_blank'>PDF GAME RULES</a>");
		gameInvite.append("].");
		gameInvite.append("</p>");

		gameInvite.append("<p>The game front-end does require that you login");
		gameInvite.append(" with your private posting key to verify your");
		gameInvite.append(" identity to show you your specific turn results");
		gameInvite.append(" and in order to submit turn orders as replies to");
		gameInvite.append(" each turn complete announcement. The front-end uses");
		gameInvite.append(" the same steemjs that the steemit website uses and");
		gameInvite.append(" does NOT transmit your private posting key to any servers.</p>");

		gameInvite.append("<p>The game will play for at least 20 turns.");
		gameInvite.append(" When the game ends will be determined randomly.</p>");
		gameInvite.append("<p>If a turn's announce post receives enough up votes for a payout,");
		gameInvite.append(" and the payout is large enough for the math to work,");
		gameInvite.append(" the payout will be divided");
		gameInvite.append(" up between the players with the remainder assigned to the gamemaster.");
		gameInvite.append(" <em>You can think of it as a player participation pool.</em></p>");

		gameInvite.append(
				"<h3>Is the game already started and you want to join in to play and possibly earn rewards?</h3>");
		gameInvite.append("<p>");
		gameInvite.append("Reply to this post asking the gamemaster to start a new game.");
		gameInvite.append(" <em>Players can only join at the start of a new game.</em>");
		gameInvite.append("</p>");
		gameInvite.append("</html>");

		String title = generateGameInviteTitle(gameDir);
		String permlink = "@" + accountName + "/" + SteemJUtils.createPermlinkString(title);
		String gameInviteHtml = gameInvite.toString();
		gameInviteHtml = gameInviteHtml.replace(_PERMLINK, permlink);
		FileUtils.write(new File(gameDir, "reports/_steem-post-0.html"), gameInviteHtml + "\n", StandardCharsets.UTF_8);
		NewGameInviteInfo info = new NewGameInviteInfo();
		info.setGameDir(gameDir);
		info.setPermlink(permlink);
		info.setTitle(title);
		info.setHtml(gameInviteHtml);
		return info;
	}

	private String getMapImagesHtml(File gameDir) {
		List<String> urls = getMapUrls(gameDir);
		if (urls.isEmpty()) {
			return "";
		}
		StringBuilder html = new StringBuilder();
		html.append("<html><table><tr>");
		html.append("<td><a href='");
		html.append(urls.get(0));
		html.append("'><img src='");
		html.append(urls.get(0));
		html.append("'/></a></td>");
		if (urls.size() > 1) {
			html.append("<td><a href='");
			html.append(urls.get(1));
			html.append("'><img src='");
			html.append(urls.get(1));
			html.append("'/></a></td>");
		}
		if (urls.size() > 2) {
			html.append("<td><a href='");
			html.append(urls.get(2));
			html.append("'><img src='");
			html.append(urls.get(2));
			html.append("'/></a></td>");
		}
		html.append("</tr></table></html>");
		return html.toString();
	}

	private List<String> getMapUrls(File gameDir) {
		File mapUrlsFile = new File(gameDir, "steem-data/map-urls.txt");
		Set<String> mapUrlsList = new HashSet<>();
		String parentFolder = "/Far-Horizons-Steem/Maps/" + gameDir.getName() + "/";
		try (IpfsFolder ipfs = new IpfsFolder(parentFolder)) {
			for (String map : MAP_LIST) {
				File imgFile = new File(gameDir, "maps/" + map);
				if (!imgFile.exists()) {
					System.err.println("Warning: Map file " + imgFile.getName() + " does not exist.");
					continue;
				}
				ipfs.add(imgFile);
			}
			String hash;
			try {
				hash = ipfs.commit();
			} catch (IOException e1) {
				return new ArrayList<>();
			}
			Random r = new Random();
			for (String map : MAP_LIST) {
				if (r.nextBoolean()) {
					mapUrlsList.add("https://cloudflare-ipfs.com/ipfs/" + hash + parentFolder + map);
				} else {
					mapUrlsList.add("https://ipfs.io/ipfs/" + hash + parentFolder + map);
				}
			}
		} catch (Exception e) {
			return new ArrayList<>();
		}
		try {
			FileUtils.writeLines(mapUrlsFile, StandardCharsets.UTF_8.name(), mapUrlsList);
		} catch (IOException e) {
		}
		return new ArrayList<>(mapUrlsList);
	}

	public static String basicEscape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replaceAll(">", "&gt;");
	}

	private String getTurnNumber(File gameDir) throws IOException, InterruptedException {
		ProcessBuilder xpb = new ProcessBuilder(new File(gameDir, "bin/TurnNumber").getAbsolutePath());
		xpb.redirectOutput(new File(gameDir, "_turnNumber.txt"));
		xpb.redirectErrorStream(true);
		xpb.directory(gameDir);
		Process xprocess = xpb.start();
		xprocess.waitFor();
		/**
		 * Turn number being reported.
		 */
		String tn = FileUtils.readFileToString(new File(gameDir, "_turnNumber.txt"), StandardCharsets.UTF_8).trim();
		if (tn.isEmpty()) {
			System.err.println("GET TURN NUMBER FAIL.");
			return "";
		}
		return tn;
	}

	/*
	 * gameComplete.append("<h1>FINAL SPECIES GAME DETAILS</h1>");
	 * 
	 */

	private void postGameCompleteDetails(AccountName parentAuthor, Permlink parentPermlink, File gameDir, String tn,
			String[] tags) throws IOException {
		if (true)
			return; // this is an RCs killer, TODO: switch to IPFS based solution
		@SuppressWarnings("unused")
		List<String> stats = getUncompressedSpeciesStatus(gameDir, tn);
		for (String stat : stats) {
			doPost: while (true) {
				sleep(25l * 1000l);// 25 seconds
				try {
					waitCheckBeforeReplying(steemJ);
					steemJ.createComment(parentAuthor, parentPermlink, stat, tags, MIME_HTML, defaultMetadata);
					break doPost;
				} catch (SteemCommunicationException | SteemResponseException | SteemInvalidTransactionException e) {
					System.err.println("Posting error. Retry in 25 seconds. [" + parentPermlink.getLink() + "]");
				}
			}
		}
	}

	/**
	 * Post final stats to blog.
	 * 
	 * @param gameDir
	 * @return
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws SteemCommunicationException
	 * @throws SteemResponseException
	 * @throws SteemInvalidTransactionException
	 */

	private String postGameComplete(File gameDir) throws IOException, InterruptedException, SteemCommunicationException,
			SteemResponseException, SteemInvalidTransactionException {

		String gameCompleteResultsHtml = gameCompleteResultsHtml(gameDir);

		String tn = getTurnNumber(gameDir);
		String title = generateGameCompleteTitle(gameDir, tn);
		String[] tags = new String[5];
		tags[0] = "playbypost";
		tags[1] = "far-horizons";
		tags[2] = gameDir.getName();
		tags[3] = "games";
		tags[4] = "contest";
		Permlink parentPermlink;
		AccountName parentAuthor;
		doPost: while (true) {
			try {
				System.out.println("POSTING: " + title + " ("
						+ gameCompleteResultsHtml.getBytes(StandardCharsets.UTF_8).length + " bytes)");
				waitCheckBeforePosting(steemJ);
				CommentOperation posted = steemJ.createPost(title, gameCompleteResultsHtml, tags, MIME_HTML,
						defaultMetadata);
				parentPermlink = posted.getPermlink();
				parentAuthor = posted.getAuthor();
				break doPost;
			} catch (Exception e) {
				System.err.println("Posting error. Sleeping 5 minutes.");
				if (e.getMessage().contains("STEEMIT_MIN_ROOT_COMMENT_INTERVAL")) {
					System.err.println("STEEMIT_MIN_ROOT_COMMENT_INTERVAL");
				} else {
					System.err.println(e.getClass().getName() + ":\n" + e.getMessage());
				}
				sleep(5l * 60l * 1000l);
			}
		}
		postGameCompleteDetails(parentAuthor, parentPermlink, gameDir, tn, tags);
		return parentPermlink.getLink();
	}

	private String gameCompleteResultsHtml(File gameDir) throws IOException, InterruptedException {
		System.out.println("Generating HTML for game complete: " + gameDir.getName());
		String tn = getTurnNumber(gameDir);
		File gameStatsDir = new File(gameDir, GAME_STATS_DIR);
		String stats = FileUtils.readFileToString(new File(gameStatsDir, "stats.t" + tn), StandardCharsets.UTF_8);
		stats = basicEscape(stats);

		/*
		 * split the stats up between the "table" and the text, starting at the word
		 * "Average" so that we can mark just the table with <pre> and not the whole
		 * mess.
		 */
		String s1 = StringUtils.substringBefore(stats, "Average");
		String s2 = "Average" + StringUtils.substringAfter(stats, "Average");
		stats = "<pre>" + s1 + "</pre>" + s2;
		stats = stats.replace("\n", "<br/>");
		stats = stats.replace("  ", " &nbsp;");
		stats = stats.replace("  ", " &nbsp;");

		StringBuilder gameComplete = new StringBuilder();
		gameComplete.append("<html>");
		gameComplete.append(DIV_PULL_RIGHT_START);
		gameComplete.append("<img style='max-width:100%;' src='" + NasaApod.getApodImageUrl(gameDir) + "'/>");
		gameComplete.append("<center>(Image courtesy of NASA's \"Astronomy Picture of the Day\")</center>");
		gameComplete.append("</div>");
		gameComplete.append("<div>");
		gameComplete.append("<h1>Far Horizons Steem</h1>");
		gameComplete.append("<h2>Game Complete!</h2>");
		/*
		 * load players
		 */
		gameComplete.append("<h3>Players</h3>");
		gameComplete.append("<p>");
		String playersTxt = FileUtils.readFileToString(new File(gameDir, "_players.tab"), StandardCharsets.UTF_8);
		String[] players = playersTxt.split("\n");
		Collections.shuffle(Arrays.asList(players));
		for (String player : players) {
			if (!player.contains("\t")) {
				continue;
			}
			gameComplete.append("@");
			gameComplete.append(basicEscape(player.split("\t")[1].trim()));
			gameComplete.append(" ");
		}
		gameComplete.append("</p>");

		gameComplete.append("<p>Hopefully everyone had fun playing the game!");
		gameComplete.append(" A new game signup will be posted soon!</p>");

		gameComplete.append("<h1>Summary Stats</h1>");
		gameComplete.append("<div>");
		gameComplete.append(stats);
		gameComplete.append("</div>");

		gameComplete.append("</html>");
		String gameCompleteHtml = gameComplete.toString();
		FileUtils.write(new File(gameDir, "final-summary-stats.html"), gameCompleteHtml, StandardCharsets.UTF_8);
		return gameCompleteHtml;
	}

	private List<String> getUncompressedSpeciesStatus(File gameDir, String tn) throws IOException {
		List<String> stats = new ArrayList<>();
		for (int spNo = 0; spNo < 100; spNo++) {
			String sp = (spNo < 10 ? "0" : "") + spNo;
			File report = new File(gameDir, "reports/sp" + sp + ".rpt.t" + tn);
			if (!report.canRead()) {
				continue;
			}
			String reportTxt = FileUtils.readFileToString(report, StandardCharsets.UTF_8);
			if (!reportTxt.contains("Species name:")) {
				System.err.println("BAD REPORT FILE: " + report.getAbsolutePath());
				continue;
			}
			reportTxt = StringUtils.substringAfter(reportTxt, "Species name:").trim();
			if (reportTxt.contains("ORDER SECTION")) {
				reportTxt = StringUtils.substringBefore(reportTxt, "ORDER SECTION");
			}
			String species = StringUtils.substringBefore(reportTxt, "\n");
			reportTxt = StringUtils.substringAfter(reportTxt, "\n").trim();

			System.out.println("=== " + species);

			if (!reportTxt.contains("\n" + SECTION_MARKER_START)) {
				reportTxt += "\n" + SECTION_MARKER_START + "\n";
			}

			String[] sections = reportTxt.split("\n" + Pattern.quote(SECTION_MARKER_START) + "[^\n]*");

			StringBuilder gameComplete = new StringBuilder();
			for (String section : sections) {
				section = basicEscape(section);
				section = section.replace("\t", "    ");
				section = section.replaceAll(" ?(\\* )+\\*?", "<hr/><hr/>");
				section = section.replaceAll(" ?---+", "<hr/>");
				section = section.replace("  ", "&nbsp; ");
				section = section.replace("  ", "&nbsp; ");
				section = section.replace("\n", "<br/>\n");

				gameComplete.setLength(0);
				gameComplete.append("<html>");
				gameComplete.append("<div><h2>Species: ");
				gameComplete.append(basicEscape(species));
				gameComplete.append("</h2>");
				gameComplete.append("<p><samp>");
				gameComplete.append(section);
				gameComplete.append("</samp></p></div>");
				gameComplete.append("</html>");

				stats.add(gameComplete.toString());
			}
		}
		return stats;
	}

	/*
	 * Post turn results to blog.
	 */
	private String postTurnResults(File gameDir) throws IOException, InterruptedException, SteemCommunicationException,
			SteemResponseException, SteemInvalidTransactionException {

		TurnResults turnResult = generateAndSaveTurnResultsHtml(gameDir);
		String turnResultsHtml = turnResult.getMessage();
		Map<String, Object> metadata = new HashMap<>();
		metadata.putAll(defaultMetadata);
		FarHorizonsGameData fgd = new FarHorizonsGameData();
		fgd.setPlayerData(turnResult.getCompressedGameData());
		fgd.setPlayers(getPlayers(gameDir));
		fgd.setStartingTurnNumber(getTurnNumber(gameDir));
		metadata.put(KEY_GAME_DATA, fgd);

		String tn = getTurnNumber(gameDir);
		String title = generateTurnTitle(gameDir, tn);
		String[] tags = getGameTurnTags(gameDir);
		while (true) {
			boolean isUpdate = false;
			Permlink permlink = new Permlink(SteemJUtils.createPermlinkString(title));
			try {
				Discussion content = steemJ.getContent(botAccount, permlink);
				if (content != null && content.getBody() != null && content.getBody().trim().startsWith("<html")) {
					isUpdate = true;
				}
			} catch (Exception e) {
				System.err.println("Read error. Sleeping 5 minutes.");
				System.err.println(e.getClass().getName() + ":\n" + e.getMessage());
				sleep(5l * 60l * 1000l);
			}
			try {
				System.out.println("POSTING: " + title + " (" + turnResultsHtml.getBytes(StandardCharsets.UTF_8).length
						+ " bytes)");
				if (isUpdate) {
					waitCheckBeforePosting(steemJ);
					CommentOperation posted = steemJ.updatePost(permlink, title, //
							turnResultsHtml, tags, MIME_HTML, metadata);
					return posted.getPermlink().getLink();
				}
				waitCheckBeforePosting(steemJ);
				CommentOperation posted = steemJ.createPost(title, //
						turnResultsHtml, tags, MIME_HTML, metadata);
				return posted.getPermlink().getLink();
			} catch (Exception e) {
				System.err.println("Posting error. Sleeping 5 minutes.");
				if (e.getMessage().contains("STEEMIT_MIN_ROOT_COMMENT_INTERVAL")) {
					System.err.println("STEEMIT_MIN_ROOT_COMMENT_INTERVAL");
				} else {
					System.err.println(e.getClass().getName() + ":\n" + e.getMessage());
				}
				sleep(5l * 60l * 1000l);
			}
		}
	}

	private String[] getGameTurnTags(File gameDir) {
		String[] tags = new String[5];
		tags[0] = "playbypost";
		tags[1] = "far-horizons";
		tags[2] = gameDir.getName();
		tags[3] = "games";
		tags[4] = "strategy";
		return tags;
	}

	private String generateTurnTitle(File gameDir, String tn) {
		String title = "Far Horizons Steem - Start of Turn " + tn + " - For Game "
				+ gameDir.getName().replaceAll("[^\\d]", "");
		return title;
	}

	private String generateGameCompleteTitle(File gameDir, String tn) {
		String title = "Far Horizons Steem - Game Complete - Game " + gameDir.getName().replaceAll("[^\\d]", "");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		sdf.setTimeZone(TimeZone.getTimeZone("EST5EDT"));
		title += " [" + sdf.format(new Date()) + "] " + System.currentTimeMillis();
		return title;
	}

	private String generateGameInviteTitle(File gameDir) {
		String title = "Far Horizons Steem - New Player Signup! " + " - Game "
				+ gameDir.getName().replaceAll("[^\\d]", "");
		return title;
	}

	private static BigDecimal minRcsToRun(AccountName botAccount) {
		RcAccounts rcs;
		try {
			rcs = SteemRcApi.getRc(botAccount);
		} catch (IOException e) {
			return _MIN_RCS_TO_RUN;
		}
		for (RcAccount rc : rcs.getRcAccounts()) {
			if (rc.getAccount().equals(botAccount.getName())) {
				return rc.getMaxRc().divide(new BigDecimal("2")).setScale(0, RoundingMode.DOWN);
			}
		}
		return _MIN_RCS_TO_RUN;
	}

	private static String decompress(String compressed) {
		if (compressed == null) {
			return null;
		}
		compressed = basicUnescape(compressed);
		try {
			return LZSEncoding.decompressFromBase64(compressed);
		} catch (Exception e) {
		}
		try {
			return LZSEncoding.decompressFromUTF16(compressed);
		} catch (Exception e) {
		}
		return null;
	}

	private static String compress(String uncompressed) {
		if (uncompressed == null) {
			return null;
		}
		return basicEscape(LZSEncoding.compressToBase64(uncompressed));
	}
	
	private static final NumberFormat NF = NumberFormat.getInstance();
	private void waitCheckBeforePosting(SteemJ steemJ) throws SteemCommunicationException, SteemResponseException {
		long FIVE_MINUTES = 1000l * 60l * 5l;
		long EXTRA_SECOND = 1000l;
		SteemJConfig config = SteemJConfig.getInstance();
		AccountName account = config.getDefaultAccount();
		while (true) {
			List<ExtendedAccount> info = steemJ.getAccounts(Arrays.asList(account));
			TimePointSec now = steemJ.getDynamicGlobalProperties().getTime();
			TimePointSec lastPostTime = now;
			for (ExtendedAccount e : info) {
				lastPostTime = e.getLastRootPost();
				break;
			}
			long since = now.getDateTimeAsTimestamp() - lastPostTime.getDateTimeAsTimestamp();
			long sleepFor = FIVE_MINUTES + EXTRA_SECOND - since;
			if (sleepFor < 0) {
				return;
			}
			System.out.println("Last post was within 5 minutes. Sleeping " + NF.format(sleepFor / 60000f) + " minutes.");
			sleep(sleepFor);
		}
	}

	private void waitCheckBeforeReplying(SteemJ steemJ) throws SteemCommunicationException, SteemResponseException {
		final long MIN_DELAY = 1000l * 3l;
		final long EXTRA_DELAY = 1000l;
		SteemJConfig config = SteemJConfig.getInstance();
		AccountName account = config.getDefaultAccount();
		while (true) {
			List<ExtendedAccount> info = steemJ.getAccounts(Arrays.asList(account));
			TimePointSec now = steemJ.getDynamicGlobalProperties().getTime();
			TimePointSec lastPostTime = now;
			for (ExtendedAccount e : info) {
				lastPostTime = e.getLastPost();
				break;
			}
			long since = now.getDateTimeAsTimestamp() - lastPostTime.getDateTimeAsTimestamp();
			long sleepFor = MIN_DELAY + EXTRA_DELAY - since;
			if (sleepFor < 0) {
				return;
			}
			System.out.println("Last reply was within 3 seconds. Sleeping " + NF.format(sleepFor / 1000f) + " seconds.");
			sleep(sleepFor);
		}
	}
}
