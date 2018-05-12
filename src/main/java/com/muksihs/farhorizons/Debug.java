package com.muksihs.farhorizons;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.tuple.ImmutablePair;

import eu.bittrade.libs.steemj.SteemJ;
import eu.bittrade.libs.steemj.base.models.AccountName;
import eu.bittrade.libs.steemj.base.models.ExtendedAccount;
import eu.bittrade.libs.steemj.configuration.SteemJConfig;
import eu.bittrade.libs.steemj.enums.PrivateKeyType;
import eu.bittrade.libs.steemj.exceptions.SteemCommunicationException;
import eu.bittrade.libs.steemj.exceptions.SteemResponseException;

public class Debug {

	public static void main(String[] args) throws FileNotFoundException, IOException, SteemCommunicationException, SteemResponseException {
		Debug debug = new Debug();
		debug.loadSteemAccountInformation();
		debug.initilizeSteemJ();
		debug.extendedAccountInfo();
	}
	private void extendedAccountInfo() throws SteemCommunicationException, SteemResponseException {
		ExtendedAccount info = getExtendedAccount();
		System.out.println("Voting power: "+info.getVotingPower());
		
	}
	private ExtendedAccount getExtendedAccount() throws SteemCommunicationException, SteemResponseException {
		List<ExtendedAccount> accounts = steemJ.getAccounts(Arrays.asList(account));
		if (accounts.isEmpty()) {
			return null;
		}
		return accounts.iterator().next();
	}
	private AccountName account;
	private SteemJ steemJ;
	private void initilizeSteemJ() throws SteemCommunicationException, SteemResponseException {
		SteemJConfig myConfig = SteemJConfig.getInstance();
		myConfig.setEncodingCharset(StandardCharsets.UTF_8);
		myConfig.setIdleTimeout(250);
		myConfig.setResponseTimeout(1000);
		account = new AccountName(this.accountName);
		myConfig.setDefaultAccount(account);
		List<ImmutablePair<PrivateKeyType, String>> privateKeys = new ArrayList<>();
		if (!this.activeKey.trim().isEmpty()) {
			privateKeys.add(new ImmutablePair<>(PrivateKeyType.ACTIVE, this.activeKey));
		}
		if (!this.postingKey.trim().isEmpty()) {
			privateKeys.add(new ImmutablePair<>(PrivateKeyType.POSTING, this.postingKey));
		}
		myConfig.getPrivateKeyStorage().addAccount(myConfig.getDefaultAccount(), privateKeys);
		steemJ = new SteemJ();
	}
	private String postingKey;
	private String activeKey;
	private String accountName;
	private void loadSteemAccountInformation() throws FileNotFoundException, IOException {
		File configFile = new File(System.getProperty("user.home"), FarHorizonsApp.STEEM_CONFIG);
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
}
