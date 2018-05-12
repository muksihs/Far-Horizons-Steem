package models;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;

public class NewGameInfo {
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("NewGameInfo [");
		if (player != null) {
			builder.append("player=");
			builder.append(player);
			builder.append(", ");
		}
		builder.append("biology=");
		builder.append(biology);
		builder.append(", ");
		if (governmentName != null) {
			builder.append("governmentName=");
			builder.append(governmentName);
			builder.append(", ");
		}
		if (governmentType != null) {
			builder.append("governmentType=");
			builder.append(governmentType);
			builder.append(", ");
		}
		builder.append("gravitics=");
		builder.append(gravitics);
		builder.append(", ");
		if (homePlanetName != null) {
			builder.append("homePlanetName=");
			builder.append(homePlanetName);
			builder.append(", ");
		}
		builder.append("lifesupport=");
		builder.append(lifesupport);
		builder.append(", military=");
		builder.append(military);
		builder.append(", ");
		if (speciesName != null) {
			builder.append("speciesName=");
			builder.append(speciesName);
		}
		builder.append("]");
		return builder.toString();
	}
	private String player;
	
	private int getLength(String text){
		int l1 = text.length();
		int l2 = text.getBytes(StandardCharsets.UTF_8).length;
		return l1>l2?l1:l2; 
	}
	
	public boolean isValid() {
		if (getSpeciesName()==null||getLength(getSpeciesName())<7||getLength(getSpeciesName())>31) {
			return false;
		}
		if (getGovernmentName()==null||getLength(getGovernmentName())==0||getLength(getGovernmentName())>31) {
			return false;
		}
		if (getHomePlanetName()==null||getLength(getHomePlanetName())==0||getLength(getHomePlanetName())>31) {
			return false;
		}
		if (getGovernmentType()==null||getLength(getGovernmentType())==0||getLength(getGovernmentType())>31) {
			return false;
		}
		return true;
	}

	public static NewGameInfo parse(String body) {
		if (body==null) {
			return null;
		}
		Iterator<String> iLines = Arrays.asList(body.split("\n")).iterator();
		NewGameInfo info = new NewGameInfo();
		while (iLines.hasNext()) {
			String line = iLines.next();
			line = line.trim();
			if (!line.contains(":")) {
				continue;
			}
			String property = line.replaceAll("(.*?):.*", "$1").toLowerCase().trim().replace(" ", ""); 
			String value = line.replaceAll(".*?:(.*)", "$1").trim();
			if ("speciesname".equals(property)) {
				info.speciesName=value;
			}
			if ("homeplanetname".equals(property)) {
				info.homePlanetName=value;
			}
			if ("governmentname".equals(property)) {
				info.governmentName=value;
			}
			if ("governmenttype".equals(property)) {
				info.governmentType=value;
			}
			if ("military".equals(property)) {
				try {
					info.military=Integer.valueOf(value);
				} catch (NumberFormatException e) {
				}
			}
			if ("gravitics".equals(property)) {
				try {
					info.gravitics=Integer.valueOf(value);
				} catch (NumberFormatException e) {
				}
			}
			if ("lifesupport".equals(property)) {
				try {
					info.lifesupport=Integer.valueOf(value);
				} catch (NumberFormatException e) {
				}
			}
			if ("biology".equals(property)) {
				try {
					info.biology=Integer.valueOf(value);
				} catch (NumberFormatException e) {
				}
			}
		}
		return info;
	}
	private int biology;

	private String governmentName="";

	private String governmentType="";

	private int gravitics;

	private String homePlanetName="";

	private int lifesupport;

	private int military;

	private String speciesName="";

	public Integer getBiology() {
		return biology;
	}

	public String getGovernmentName() {
		return governmentName;
	}

	public String getGovernmentType() {
		return governmentType;
	}

	public Integer getGravitics() {
		return gravitics;
	}

	public String getHomePlanetName() {
		return homePlanetName;
	}

	public Integer getLifesupport() {
		return lifesupport;
	}

	public Integer getMilitary() {
		return military;
	}

	public String getSpeciesName() {
		return speciesName;
	}

	public void setBiology(Integer biology) {
		this.biology = biology;
	}
	public void setGovernmentName(String governmentName) {
		this.governmentName = governmentName;
	}
	public void setGovernmentType(String governmentType) {
		this.governmentType = governmentType;
	}
	public void setGravitics(Integer gravitics) {
		this.gravitics = gravitics;
	}
	public void setHomePlanetName(String homePlanetName) {
		this.homePlanetName = homePlanetName;
	}
	public void setLifesupport(Integer lifesupport) {
		this.lifesupport = lifesupport;
	}
	public void setMilitary(Integer military) {
		this.military = military;
	}

	public void setSpeciesName(String speciesName) {
		this.speciesName = speciesName;
	}

	public String getPlayer() {
		return player;
	}

	public void setPlayer(String player) {
		this.player = player;
	}

}
