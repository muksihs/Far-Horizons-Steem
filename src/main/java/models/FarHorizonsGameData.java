package models;

import java.util.List;

public class FarHorizonsGameData {
	private String playerData;
	private String startingTurnNumber;
	private List<String> players;
	public String getPlayerData() {
		return playerData;
	}
	public void setPlayerData(String playerData) {
		this.playerData = playerData;
	}
	public String getStartingTurnNumber() {
		return startingTurnNumber;
	}
	public void setStartingTurnNumber(String startingTurnNumber) {
		this.startingTurnNumber = startingTurnNumber;
	}
	public List<String> getPlayers() {
		return players;
	}
	public void setPlayers(List<String> players) {
		this.players = players;
	}
}
