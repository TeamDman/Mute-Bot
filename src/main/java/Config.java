import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
	private final String     name;
	private final Properties props = new Properties();

	private Config(String name) {
		this.name = name;
		load();
	}

	private void load() {
		try (FileInputStream in = new FileInputStream(name + ".properties")) {
			props.load(in);
		} catch (FileNotFoundException e) { // Create config from defaults
			for (Property p : Property.values())
				props.put(p.name(), p.fallback);
			save();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void save() {
		try (FileOutputStream out = new FileOutputStream(name + ".properties")) {
			props.store(out, "MUTETYPE can be either 'role' or 'perms'");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static Config getConfig() {
		if (main.config == null) {
			return new Config("bot");
		}
		return main.config;
	}

	String get(Property key) {
		return props.get(key.name()).toString(); // Not good, but good enough for now.
	}

	void set(Property key, String value) {
		props.put(key.name(), value);
		save();
	}

	public enum Property {
		DISCORD_TOKEN("undefined"),
		REPORTCHANNEL("000000000000000000"),
		MUTEROLE("000000000000000000"),
		MUTETYPE("perms");
		final Object fallback;

		Property(Object fallback) {
			this.fallback = fallback;
		}
	}
}
